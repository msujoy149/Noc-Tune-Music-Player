package com.example.data.db

import androidx.room.*
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistSongCrossRef
import com.example.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    // Song queries
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY addedDate DESC")
    fun getLastAddedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 20")
    fun getMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedDate > 0 ORDER BY lastPlayedDate DESC LIMIT 20")
    fun getRecentlyPlayedSongs(): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFav WHERE id = :songId")
    suspend fun updateFavorite(songId: String, isFav: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedDate = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM songs WHERE isGenerative = 0")
    suspend fun deleteScannedSongs()

    @Query("DELETE FROM songs WHERE isGenerative = 1")
    suspend fun deleteGenerativeSongs()

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsSync(): List<SongEntity>

    @Query("SELECT id FROM playlists")
    suspend fun getAllPlaylistIds(): List<Int>

    @Query("DELETE FROM songs WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<String>)

    @Query("DELETE FROM playlist_song_cross_ref WHERE songId IN (:songIds)")
    suspend fun deletePlaylistSongCrossRefsForSongIds(songIds: List<String>)

    // Playlist queries
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // Playlist Song associations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Int, songId: String)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref r ON s.id = r.songId
        WHERE r.playlistId = :playlistId
        ORDER BY s.title ASC
    """)
    fun getSongsForPlaylist(playlistId: Int): Flow<List<SongEntity>>

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE songId = :songId")
    suspend fun deletePlaylistSongCrossRefs(songId: String)

    @Query("UPDATE playlists SET songCount = (SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId) WHERE id = :playlistId")
    suspend fun updatePlaylistSongCount(playlistId: Int)
}
