package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import com.example.data.db.SongDao
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao
) {
    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<SongEntity>> = songDao.getFavoriteSongs()
    val lastAddedSongs: Flow<List<SongEntity>> = songDao.getLastAddedSongs()
    val mostPlayedSongs: Flow<List<SongEntity>> = songDao.getMostPlayedSongs()
    val recentlyPlayedSongs: Flow<List<SongEntity>> = songDao.getRecentlyPlayedSongs()
    val allPlaylists: Flow<List<PlaylistEntity>> = songDao.getAllPlaylists()

    suspend fun initDefaultGenerativeTracks() = withContext(Dispatchers.IO) {
        // Stop seeding default generative ambient tracks as requested.
        // Also clear any existing ones from the localized database to keep the music player 100% clean.
        try {
            songDao.deleteGenerativeSongs()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to delete existing generative tracks", e)
        }
    }

    suspend fun insertSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.insertSong(song)
    }

    suspend fun toggleFavorite(songId: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        songDao.updateFavorite(songId, isFav)
    }

    suspend fun incrementPlayCount(songId: String) = withContext(Dispatchers.IO) {
        songDao.incrementPlayCount(songId)
    }

    // Background scan & reconciliation of physical media from device storage
    suspend fun scanLocalMusic() = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor: Cursor? = try {
                resolver.query(uri, projection, selection, null, null)
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed querying MediaStore", e)
                null
            }

            val freshSongs = mutableListOf<SongEntity>()
            val freshSongsMap = mutableMapOf<String, SongEntity>()
            val prefs = context.getSharedPreferences("noctune_deleted_songs_prefs", Context.MODE_PRIVATE)
            val deletedIds = prefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()
            val deletedPaths = prefs.getStringSet("deleted_paths", emptySet()) ?: emptySet()

            cursor?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val idLong = if (idCol != -1) c.getLong(idCol) else continue
                    val id = "local_$idLong"
                    val title = if (titleCol != -1) c.getString(titleCol) ?: "Unknown Track" else "Unknown Track"
                    val artist = if (artistCol != -1) c.getString(artistCol) ?: "<Unknown Artist>" else "<Unknown Artist>"
                    val album = if (albumCol != -1) c.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durationCol != -1) c.getLong(durationCol) else 0L
                    val path = if (dataCol != -1) c.getString(dataCol) ?: "" else ""
                    val addedDate = if (dateAddedCol != -1) c.getLong(dateAddedCol) * 1000 else System.currentTimeMillis()

                    // Exclude songs that were previously permanently deleted by the user inside the app
                    if (id in deletedIds || (path.isNotBlank() && (path in deletedPaths || deletedPaths.any { it.equals(path, ignoreCase = true) }))) {
                        continue
                    }

                    // Verify that the file actually exists on device memory
                    if (path.isNotBlank() && path.startsWith("/")) {
                        val file = java.io.File(path)
                        if (!file.exists() || !file.canRead() || file.length() == 0L) {
                            continue
                        }
                    }

                    val songEntity = SongEntity(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        path = path,
                        addedDate = addedDate
                    )
                    freshSongs.add(songEntity)
                    freshSongsMap[id] = songEntity
                }
            }

            // Fetch all current database songs to reconcile and remove deleted ones
            val currentDbSongs = songDao.getAllSongsSync()
            val deadSongIds = mutableListOf<String>()

            for (dbSong in currentDbSongs) {
                if (dbSong.isGenerative) {
                    deadSongIds.add(dbSong.id)
                    continue
                }

                // Check if user previously marked this song deleted
                if (dbSong.id in deletedIds || (dbSong.path.isNotBlank() && (dbSong.path in deletedPaths || deletedPaths.any { it.equals(dbSong.path, ignoreCase = true) }))) {
                    deadSongIds.add(dbSong.id)
                    continue
                }

                // Check physical existence of the file
                if (dbSong.path.isNotBlank() && dbSong.path.startsWith("/")) {
                    val file = java.io.File(dbSong.path)
                    if (!file.exists() || !file.canRead() || file.length() == 0L) {
                        deadSongIds.add(dbSong.id)
                        continue
                    }
                } else if (dbSong.path.startsWith("content://")) {
                    val isAccessible = try {
                        val parsedUri = android.net.Uri.parse(dbSong.path)
                        resolver.openAssetFileDescriptor(parsedUri, "r")?.use { afd ->
                            afd.length > 0L
                        } ?: false
                    } catch (e: Exception) {
                        false
                    }
                    if (!isAccessible) {
                        deadSongIds.add(dbSong.id)
                        continue
                    }
                }

                // For local MediaStore songs, if it's no longer found by MediaStore and file doesn't exist
                if (dbSong.id.startsWith("local_")) {
                    if (!freshSongsMap.containsKey(dbSong.id)) {
                        val file = java.io.File(dbSong.path)
                        if (!file.exists()) {
                            deadSongIds.add(dbSong.id)
                            continue
                        }
                    }
                }
            }

            // Batch delete dead/removed songs from DB
            if (deadSongIds.isNotEmpty()) {
                Log.d("MusicRepository", "Removing ${deadSongIds.size} deleted songs from library")
                songDao.deletePlaylistSongCrossRefsForSongIds(deadSongIds)
                songDao.deleteSongsByIds(deadSongIds)
                
                // Also remove them from active playback queue
                com.example.player.MusicPlayerManager.removeDeletedSongsFromQueue(deadSongIds)
            }

            // Upsert / Insert fresh valid songs into DB, preserving user favorite status and play count
            if (freshSongs.isNotEmpty()) {
                val existingMap = currentDbSongs.associateBy { it.id }
                val songsToInsert = freshSongs.map { fresh ->
                    val existing = existingMap[fresh.id]
                    if (existing != null) {
                        fresh.copy(
                            isFavorite = existing.isFavorite,
                            playCount = existing.playCount,
                            lastPlayedDate = existing.lastPlayedDate,
                            addedDate = existing.addedDate
                        )
                    } else {
                        fresh
                    }
                }
                songDao.insertSongs(songsToInsert)
            }

            // Update song counts for all playlists
            val playlistIds = songDao.getAllPlaylistIds()
            for (pId in playlistIds) {
                songDao.updatePlaylistSongCount(pId)
            }

        } catch (e: Exception) {
            Log.e("MusicRepository", "Error during background library sync", e)
        }
    }

    // Playlist Operations
    suspend fun createPlaylist(name: String): Int = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(name = name)
        songDao.insertPlaylist(playlist).toInt()
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        songDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: String) = withContext(Dispatchers.IO) {
        songDao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId))
        songDao.updatePlaylistSongCount(playlistId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: String) = withContext(Dispatchers.IO) {
        songDao.deletePlaylistSong(playlistId, songId)
        songDao.updatePlaylistSongCount(playlistId)
    }

    fun getSongsForPlaylist(playlistId: Int): Flow<List<SongEntity>> {
        return songDao.getSongsForPlaylist(playlistId)
    }

    suspend fun deleteSong(song: SongEntity) = withContext(Dispatchers.IO) {
        // Save to deleted songs Preferences so it will never show up or be re-scanned
        val prefs = context.getSharedPreferences("noctune_deleted_songs_prefs", Context.MODE_PRIVATE)
        val deletedIds = prefs.getStringSet("deleted_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val deletedPaths = prefs.getStringSet("deleted_paths", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        deletedIds.add(song.id)
        if (song.path.isNotBlank()) {
            deletedPaths.add(song.path)
        }
        
        prefs.edit()
            .putStringSet("deleted_ids", deletedIds)
            .putStringSet("deleted_paths", deletedPaths)
            .apply()

        songDao.deletePlaylistSongCrossRefs(song.id)
        songDao.deleteSong(song.id)
        
        if (!song.isGenerative && song.path.isNotBlank()) {
            try {
                // Try deleting using java.io.File First
                val file = java.io.File(song.path)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("MusicRepository", "Successfully physically deleted song path: ${song.path}: $deleted")
                } else {
                    val uri = android.net.Uri.parse(song.path)
                    if (uri.scheme == "content") {
                        context.contentResolver.delete(uri, null, null)
                        Log.d("MusicRepository", "ContentResolver deleted song: ${song.path}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed to delete physical file of ${song.path}", e)
            }
            
            // Also explicitly try to resolve/delete via MediaStore content URI if it's a MediaStore track
            if (song.id.startsWith("local_")) {
                val mediaStoreId = song.id.substringAfter("local_").toLongOrNull()
                if (mediaStoreId != null) {
                    try {
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaStoreId
                        )
                        val rowsDeleted = context.contentResolver.delete(contentUri, null, null)
                        Log.d("MusicRepository", "ContentResolver deleted song uri: $contentUri, rows: $rowsDeleted")
                    } catch (e: Exception) {
                        Log.e("MusicRepository", "Failed to delete from media store row for client-side delete request $mediaStoreId", e)
                    }
                }
            }
        }
    }
}
