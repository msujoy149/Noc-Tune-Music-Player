package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.example.data.model.SongEntity

data class ExtractedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artwork: Bitmap?
)

object SongMetadataExtractor {
    fun extract(context: Context, song: SongEntity): ExtractedMetadata {
        val path = song.path
        if (song.isGenerative || path.startsWith("generative://") || path.isEmpty()) {
            return ExtractedMetadata(
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                artwork = null
            )
        }

        var title = song.title
        var artist = song.artist
        var album = song.album
        var duration = song.duration
        var artwork: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }

            // Extract text fields if they are missing or generic in the entity
            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!extractedTitle.isNullOrEmpty()) {
                title = extractedTitle
            }

            val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!extractedArtist.isNullOrEmpty()) {
                artist = extractedArtist
            }

            val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (!extractedAlbum.isNullOrEmpty()) {
                album = extractedAlbum
            }

            val extractedDurationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            extractedDurationStr?.toLongOrNull()?.let {
                if (it > 0) {
                    duration = it
                }
            }

            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                
                // Scale down to max ~512x512 to prevent large memory pins in Ashmem / IPC transfers
                var sampleSize = 1
                while ((opts.outWidth / sampleSize) > 512 || (opts.outHeight / sampleSize) > 512) {
                    sampleSize *= 2
                }
                
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                artwork = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOpts)
            }
        } catch (e: Exception) {
            android.util.Log.e("SongMetadataExtractor", "Error extracting metadata via MediaMetadataRetriever for path: $path", e)
        } finally {
            try {
                retriever.release()
            } catch (ex: Exception) {
                // Ignore release errors
            }
        }

        // Fallback for artwork using loadThumbnail on Android Q+ if retriever failed
        if (artwork == null && path.startsWith("content://")) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = Uri.parse(path)
                    artwork = context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                }
            } catch (e2: Exception) {
                android.util.Log.e("SongMetadataExtractor", "Error loading thumbnail via content resolver for uri: $path", e2)
            }
        }

        return ExtractedMetadata(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            artwork = artwork
        )
    }
}
