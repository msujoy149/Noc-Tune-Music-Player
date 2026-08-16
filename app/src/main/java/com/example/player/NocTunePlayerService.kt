package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class NocTunePlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "noctune_playback_channel"
        const val NOTIFICATION_ID = 404
        
        const val ACTION_PLAY_PAUSE = "com.example.noctune.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.noctune.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.noctune.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.noctune.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isServiceInForeground = false
    private var mediaSession: MediaSessionCompat? = null
    private var currentExtractedMetadata: ExtractedMetadata? = null
    private var marqueeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        MusicPlayerManager.init(this)
        createNotificationChannel()

        // Initialize MediaSessionCompat
        mediaSession = MediaSessionCompat(this, "NocTuneMediaSession").apply {
            isActive = true
            setPlaybackToLocal(android.media.AudioManager.STREAM_MUSIC)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MusicPlayerManager.resumePlayback()
                }

                override fun onPause() {
                    MusicPlayerManager.pausePlayback()
                }

                override fun onSkipToNext() {
                    MusicPlayerManager.nextSong()
                }

                override fun onSkipToPrevious() {
                    MusicPlayerManager.prevSong()
                }

                override fun onStop() {
                    MusicPlayerManager.stopPlayback()
                }

                override fun onSeekTo(pos: Long) {
                    MusicPlayerManager.seekTo(pos)
                }
            })
        }
        
        // Start foreground immediately in onCreate with initial notification to satisfy Android requirements
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            isServiceInForeground = true
        } catch (e: Exception) {
            isServiceInForeground = false
            android.util.Log.e("NocTunePlayerService", "Failed to startForeground in onCreate", e)
        }
        
        // Listen to state changes to update the notification dynamically
        MusicPlayerManager.isPlaying.onEach { updateNotification() }.launchIn(serviceScope)
        
        MusicPlayerManager.currentSong.onEach { song ->
            if (song != null) {
                serviceScope.launch {
                    val extracted = withContext(Dispatchers.IO) {
                        SongMetadataExtractor.extract(this@NocTunePlayerService, song)
                    }
                    currentExtractedMetadata = extracted
                    updateNotification()
                }
            } else {
                currentExtractedMetadata = null
                updateNotification()
            }
        }.launchIn(serviceScope)
        
        // Lightweight MediaSession state syncing on position updates
        MusicPlayerManager.currentPosition.onEach { updateMediaSessionStateOnly() }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (MusicPlayerManager.isPlaying.value) {
                    MusicPlayerManager.pausePlayback()
                } else {
                    MusicPlayerManager.resumePlayback()
                }
            }
            ACTION_NEXT -> MusicPlayerManager.nextSong()
            ACTION_PREVIOUS -> MusicPlayerManager.prevSong()
            ACTION_STOP -> {
                MusicPlayerManager.stopPlayback()
                return START_NOT_STICKY
            }
        }
        
        // Ensure starting foreground again safely if restarted
        if (intent?.action != ACTION_STOP && MusicPlayerManager.currentSong.value != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                isServiceInForeground = true
            } catch (e: Exception) {
                android.util.Log.e("NocTunePlayerService", "Failed to startForeground in onStartCommand", e)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Noc Tune Playback Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Provides notification player controls for Noc Tune Music Lounge"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateMediaSessionStateOnly() {
        val session = mediaSession ?: return
        val isPlaying = MusicPlayerManager.isPlaying.value
        val position = MusicPlayerManager.currentPosition.value
        
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
                
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, if (isPlaying) 1.0f else 0.0f)
            .build()
            
        session.setPlaybackState(playbackState)
    }

    private fun updateMediaSessionMetadata(scrolledTitle: String? = null) {
        val session = mediaSession ?: return
        val song = MusicPlayerManager.currentSong.value ?: return
        val extracted = currentExtractedMetadata
        
        val titleText = scrolledTitle ?: extracted?.title ?: song.title
        
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, extracted?.artist ?: song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, extracted?.album ?: song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, extracted?.duration ?: song.duration)
            
        extracted?.artwork?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
            
        session.setMetadata(metadataBuilder.build())
    }

    private fun buildNotification(scrolledTitle: String? = null): Notification {
        val song = MusicPlayerManager.currentSong.value
        val isPlaying = MusicPlayerManager.isPlaying.value
        val extracted = currentExtractedMetadata
        
        val title = scrolledTitle ?: extracted?.title ?: song?.title ?: "No Song Loaded"
        val artist = extracted?.artist ?: song?.artist ?: "Relax in the Noc Tune Espresso lounge"
        
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous Action
        val prevIntent = Intent(this, NocTunePlayerService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Play/Pause Action
        val playIntent = Intent(this, NocTunePlayerService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPendingIntent = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Next Action
        val nextIntent = Intent(this, NocTunePlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Stop Action
        val stopIntent = Intent(this, NocTunePlayerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playIconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        // Standard notification builder loaded gracefully
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(mainPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playIconRes, if (isPlaying) "Pause" else "Play", playPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setColor(0xFF6B4EE0.toInt()) // Soothing Violet Accent Theme Colour for notification
        
        extracted?.artwork?.let {
            builder.setLargeIcon(it)
        }
        
        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isPlaying = MusicPlayerManager.isPlaying.value
        val song = MusicPlayerManager.currentSong.value

        // Clear notification & stop service cleanly if no song is loaded
        if (song == null) {
            marqueeJob?.cancel()
            marqueeJob = null
            if (isServiceInForeground) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NocTunePlayerService", "Error stopping foreground on null song", e)
                }
                isServiceInForeground = false
            } else {
                manager.cancel(NOTIFICATION_ID)
            }
            stopSelf()
            return
        }

        marqueeJob?.cancel()
        marqueeJob = null
        updateMediaSessionStateOnly()
        updateMediaSessionMetadata()
        
        val notification = buildNotification()

        if (isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isServiceInForeground = true
            } catch (e: Exception) {
                android.util.Log.e("NocTunePlayerService", "Failed to startForeground in updateNotification", e)
            }
        } else {
            if (isServiceInForeground) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                    isServiceInForeground = false
                } catch (e: Exception) {
                    android.util.Log.e("NocTunePlayerService", "Failed to stopForeground in updateNotification", e)
                }
            }
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
