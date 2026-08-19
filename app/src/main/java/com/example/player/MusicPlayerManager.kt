package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.data.db.AppDatabase
import com.example.data.model.SongEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class AudioRoute(val label: String, val isExternal: Boolean) {
    HEADPHONES("Headphones", true),
    BLUETOOTH("Bluetooth Audio", true),
    SPEAKER("Phone Speaker", false)
}

object MusicPlayerManager {
    private const val PREFS_NAME = "noctune_player_prefs"
    private const val KEY_LAST_SONG_ID = "last_song_id"
    private const val KEY_LAST_POSITION = "last_position"

    private var context: Context? = null
    private var mediaPlayer: MediaPlayer? = null
    private val generativeSynth = ProceduralAudioSynthesizer()
    
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isBecomingNoisyReceiverRegistered = false
    private var wasPlayingBeforeTransientLoss = false
    
    private val _audioRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val audioRoute = _audioRoute.asStateFlow()
    
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("NocTunePlayer", "Audio becoming noisy (headphones disconnected), pausing playback.")
                pausePlayback()
                updateAudioRoute()
            } else if (action == Intent.ACTION_HEADSET_PLUG ||
                       action == "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED" ||
                       action == "android.bluetooth.device.action.ACL_CONNECTED" ||
                       action == "android.bluetooth.device.action.ACL_DISCONNECTED") {
                updateAudioRoute()
            }
        }
    }
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeTransientLoss = false
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeTransientLoss = _isPlaying.value
                if (_isPlaying.value) {
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    mediaPlayer?.setVolume(0.25f, 0.25f)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (e: Exception) {
                    // Ignore
                }
                if (wasPlayingBeforeTransientLoss && !_isPlaying.value && _currentSong.value != null) {
                    wasPlayingBeforeTransientLoss = false
                    resumePlayback()
                }
            }
        }
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null
    
    // Core playback flows
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()
    
    private val _playbackQueue = MutableStateFlow<List<SongEntity>>(emptyList())
    val playbackQueue = _playbackQueue.asStateFlow()
    
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode = _repeatMode.asStateFlow()
    
    private val _sleepTimerRemaining = MutableStateFlow(0L) // Remaining time in ms
    val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()
    
    private val _stopAfterCurrentSong = MutableStateFlow(false)
    val stopAfterCurrentSong = _stopAfterCurrentSong.asStateFlow()
    
    private var currentIndex = -1
    private var originalQueue = listOf<SongEntity>()
    private var consecutiveErrors = 0

    fun init(ctx: Context) {
        if (context != null) return
        val appContext = ctx.applicationContext
        this.context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            appContext.createAttributionContext("mediaPlayback")
        } else {
            appContext
        }
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        registerAudioDeviceCallback()
        updateAudioRoute()
        loadSavedState()
    }

    fun updateAudioRoute() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                var isBt = false
                var isHeadphones = false
                for (dev in devices) {
                    when (dev.type) {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> isBt = true
                        
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> isHeadphones = true
                    }
                }
                _audioRoute.value = when {
                    isBt -> AudioRoute.BLUETOOTH
                    isHeadphones -> AudioRoute.HEADPHONES
                    else -> AudioRoute.SPEAKER
                }
            } else {
                @Suppress("DEPRECATION")
                _audioRoute.value = when {
                    am.isBluetoothA2dpOn || am.isBluetoothScoOn -> AudioRoute.BLUETOOTH
                    am.isWiredHeadsetOn -> AudioRoute.HEADPHONES
                    else -> AudioRoute.SPEAKER
                }
            }
        } catch (e: Exception) {
            Log.e("NocTunePlayer", "Error detecting audio output device", e)
        }
    }

    private fun registerAudioDeviceCallback() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        updateAudioRoute()
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        updateAudioRoute()
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Failed to register audio device callback", e)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val ctx = context ?: return true
        val am = audioManager ?: (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it }
        if (am == null) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.e("NocTunePlayer", "Error abandoning audio focus", e)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        val ctx = context ?: return
        if (!isBecomingNoisyReceiverRegistered) {
            try {
                ctx.registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                isBecomingNoisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Error registering noisy receiver", e)
            }
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        val ctx = context ?: return
        if (isBecomingNoisyReceiverRegistered) {
            try {
                ctx.unregisterReceiver(becomingNoisyReceiver)
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Error unregistering noisy receiver", e)
            }
            isBecomingNoisyReceiverRegistered = false
        }
    }

    private fun loadSavedState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSongId = prefs.getString(KEY_LAST_SONG_ID, null)
        val lastPos = prefs.getLong(KEY_LAST_POSITION, 0L)
        
        if (lastSongId != null) {
            coroutineScope.launch {
                val db = AppDatabase.getDatabase(ctx)
                try {
                    val foundSong = withContext(Dispatchers.IO) {
                        db.songDao().getAllSongs().first().find { it.id == lastSongId }
                    }
                    if (foundSong != null) {
                        _currentSong.value = foundSong
                        _currentPosition.value = lastPos
                        // Enqueue song
                        setQueue(listOf(foundSong))
                        Log.d("NocTunePlayer", "Restored matching last song: ${foundSong.title} to position: ${lastPos}ms")
                    }
                } catch (e: Exception) {
                    Log.e("NocTunePlayer", "Error loading saved song", e)
                }
            }
        }
    }

    private fun savePlaybackState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val song = _currentSong.value
        prefs.edit().apply {
            if (song != null) {
                putString(KEY_LAST_SONG_ID, song.id)
                putLong(KEY_LAST_POSITION, _currentPosition.value)
            } else {
                remove(KEY_LAST_SONG_ID)
                remove(KEY_LAST_POSITION)
            }
            apply()
        }
    }

    fun setQueue(songs: List<SongEntity>, startIndex: Int = 0) {
        originalQueue = songs
        currentIndex = startIndex
        if (_shuffleEnabled.value) {
            val shuffled = songs.shuffled()
            _playbackQueue.value = shuffled
            currentIndex = shuffled.indexOf(songs.getOrNull(startIndex))
        } else {
            _playbackQueue.value = songs
        }
        
        if (_playbackQueue.value.isNotEmpty() && currentIndex in _playbackQueue.value.indices) {
            _currentSong.value = _playbackQueue.value[currentIndex]
        }
    }

    fun playSong(song: SongEntity) {
        val index = _playbackQueue.value.indexOfFirst { it.id == song.id }
        if (index != -1) {
            currentIndex = index
        } else {
            // Append and play
            val currentList = _playbackQueue.value.toMutableList()
            currentList.add(song)
            _playbackQueue.value = currentList
            currentIndex = currentList.size - 1
        }
        
        _currentSong.value = song
        _currentPosition.value = 0L
        startPlayback()
    }

    fun startPlayback() {
        val song = _currentSong.value ?: return
        val ctx = context ?: return
        
        requestAudioFocus()
        registerBecomingNoisyReceiver()

        // Stop current
        stopAllPlayers()
        
        if (song.isGenerative) {
            startGenerativeAudio(song)
            consecutiveErrors = 0
        } else {
            startLocalAudio(song)
        }
        
        _isPlaying.value = true
        startProgressTracker()
        savePlaybackState()
        
        // Log in Database play history
        coroutineScope.launch {
            val db = AppDatabase.getDatabase(ctx)
            db.songDao().incrementPlayCount(song.id)
        }
        
        // Start foreground service
        startForegroundService()
    }

    private fun startLocalAudio(song: SongEntity) {
        val ctx = context ?: return
        mediaPlayer = MediaPlayer().apply {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
                setAudioAttributes(audioAttributes)

                val songUri = if (song.path.startsWith("content://")) {
                    android.net.Uri.parse(song.path)
                } else {
                    android.net.Uri.fromFile(java.io.File(song.path))
                }
                setDataSource(ctx, songUri)
                prepare()
                try {
                    AudioEffectsController.attachSession(ctx, audioSessionId)
                    AudioVisualizerManager.attachSession(ctx, audioSessionId)
                } catch (e: Exception) {
                    Log.e("NocTunePlayer", "Error attaching equalizer/visualizer to session", e)
                }
                if (_currentPosition.value > 0) {
                    seekTo(_currentPosition.value.toInt())
                }
                start()
                consecutiveErrors = 0
                
                setOnCompletionListener {
                    onSongCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("NocTunePlayer", "MediaPlayer runtime error for ${song.title}: what=$what, extra=$extra")
                    coroutineScope.launch {
                        handlePlaybackError()
                    }
                    true
                }
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Error preparing local MediaPlayer for ${song.title}", e)
                try { release() } catch (ignored: Exception) {}
                coroutineScope.launch {
                    handlePlaybackError()
                }
            }
        }
    }

    private fun startGenerativeAudio(song: SongEntity) {
        val ctx = context ?: return
        generativeSynth.start(ctx, song.generativePreset)
    }

    private suspend fun handlePlaybackError() {
        consecutiveErrors++
        val failedSong = _currentSong.value
        if (failedSong != null) {
            val ctx = context
            if (ctx != null) {
                try {
                    val db = AppDatabase.getDatabase(ctx)
                    db.songDao().deleteSong(failedSong.id)
                    db.songDao().deletePlaylistSongCrossRefs(failedSong.id)
                } catch (e: Exception) {
                    Log.e("NocTunePlayer", "Error deleting dead song from db", e)
                }
            }
            removeSongFromQueue(failedSong)
        }

        val queueSize = _playbackQueue.value.size
        if (queueSize == 0 || consecutiveErrors >= queueSize || consecutiveErrors > 5) {
            Log.w("NocTunePlayer", "Too many playback errors ($consecutiveErrors). Stopping playback loops.")
            consecutiveErrors = 0
            stopPlayback()
        } else {
            delay(200)
            nextSong()
        }
    }

    fun pausePlayback() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        
        val song = _currentSong.value
        if (song != null) {
            if (song.isGenerative) {
                generativeSynth.stop()
            } else {
                mediaPlayer?.apply {
                    if (isPlaying) {
                        pause()
                    }
                }
            }
        }
        savePlaybackState()
        stopProgressTracker()
    }

    fun resumePlayback() {
        if (_isPlaying.value) return
        val song = _currentSong.value ?: return
        
        requestAudioFocus()
        registerBecomingNoisyReceiver()

        if (song.isGenerative) {
            val ctx = context ?: return
            generativeSynth.start(ctx, song.generativePreset)
        } else {
            try {
                mediaPlayer?.start() ?: run {
                    // Rebuild player if killed
                    startLocalAudio(song)
                }
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Error resuming MediaPlayer", e)
            }
        }
        _isPlaying.value = true
        startProgressTracker()
        startForegroundService()
    }

    fun nextSong(forcePlay: Boolean = true) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        
        if (_stopAfterCurrentSong.value) {
            _stopAfterCurrentSong.value = false
            pausePlayback()
            return
        }

        if (_repeatMode.value == RepeatMode.ONE) {
            _currentPosition.value = 0L
            startPlayback()
            return
        }

        currentIndex++
        if (currentIndex >= queue.size) {
            if (_repeatMode.value == RepeatMode.ALL || forcePlay) {
                currentIndex = 0
            } else {
                currentIndex = queue.size - 1
                pausePlayback()
                return
            }
        }
        
        _currentSong.value = queue[currentIndex]
        _currentPosition.value = 0L
        startPlayback()
    }

    fun prevSong(forcePlay: Boolean = true) {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return
        
        // If the current track is past 3 seconds, restart it first
        if (_currentPosition.value > 3000L) {
            seekTo(0L)
            return
        }

        currentIndex--
        if (currentIndex < 0) {
            if (_repeatMode.value == RepeatMode.ALL || forcePlay) {
                currentIndex = queue.size - 1
            } else {
                currentIndex = 0
                seekTo(0L)
                return
            }
        }
        
        _currentSong.value = queue[currentIndex]
        _currentPosition.value = 0L
        startPlayback()
    }

    fun seekTo(positionMs: Long) {
        val current = _currentSong.value ?: return
        _currentPosition.value = positionMs
        
        if (!current.isGenerative) {
            try {
                mediaPlayer?.seekTo(positionMs.toInt())
            } catch (e: Exception) {
                Log.e("NocTunePlayer", "Error seeking MediaPlayer", e)
            }
        } else {
            // Generative tracker just warps display clocks
        }
        savePlaybackState()
    }

    fun toggleShuffle() {
        val isEn = !_shuffleEnabled.value
        _shuffleEnabled.value = isEn
        
        val song = _currentSong.value
        if (isEn) {
            val shuffled = originalQueue.shuffled()
            _playbackQueue.value = shuffled
            if (song != null) {
                currentIndex = shuffled.indexOf(song)
            }
        } else {
            _playbackQueue.value = originalQueue
            if (song != null) {
                currentIndex = originalQueue.indexOf(song)
            }
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        val ctx = context ?: return
        val newFav = !song.isFavorite
        _currentSong.value = song.copy(isFavorite = newFav)
        
        coroutineScope.launch {
            val db = AppDatabase.getDatabase(ctx)
            db.songDao().updateFavorite(song.id, newFav)
        }
    }

    fun toggleStopAfterCurrent() {
        _stopAfterCurrentSong.value = !_stopAfterCurrentSong.value
    }

    // Dynamic countdown Sleep Timer
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0L
            return
        }
        
        val durationMs = minutes * 60 * 1000L
        _sleepTimerRemaining.value = durationMs
        
        sleepTimerJob = coroutineScope.launch {
            while (_sleepTimerRemaining.value > 0) {
                delay(1000)
                _sleepTimerRemaining.value -= 1000L
            }
            // Timer expired! Stop audio.
            pausePlayback()
            Log.d("NocTunePlayer", "Sleep timer expired. Playback paused.")
        }
    }

    private fun onSongCompleted() {
        nextSong(forcePlay = false)
    }

    private fun stopAllPlayers() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("NocTunePlayer", "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        generativeSynth.stop()
        try {
            AudioEffectsController.release()
            AudioVisualizerManager.release()
        } catch (e: Exception) {
            Log.e("NocTunePlayer", "Error releasing AudioEffects/Visualizer", e)
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = coroutineScope.launch {
            while (isActive) {
                val song = _currentSong.value
                if (song != null && _isPlaying.value) {
                    if (song.isGenerative) {
                        val nextPos = _currentPosition.value + 120L
                        if (nextPos >= song.duration) {
                            _currentPosition.value = song.duration
                            onSongCompleted()
                        } else {
                            _currentPosition.value = nextPos
                        }
                    } else {
                        mediaPlayer?.let { mp ->
                            try {
                                if (mp.isPlaying) {
                                    _currentPosition.value = mp.currentPosition.toLong()
                                }
                            } catch (e: Exception) {
                                // Ignore transient media player exceptions
                            }
                        }
                    }
                }
                delay(120)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun startForegroundService() {
        val ctx = context ?: return
        val serviceIntent = Intent(ctx, NocTunePlayerService::class.java)
        try {
            ContextCompat.startForegroundService(ctx, serviceIntent)
        } catch (e: Exception) {
            Log.e("NocTunePlayer", "Failed to start foreground service, falling back to regular startService", e)
            try {
                ctx.startService(serviceIntent)
            } catch (ex: Exception) {
                Log.e("NocTunePlayer", "Failed to start service entirely", ex)
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        stopAllPlayers()
        _currentSong.value = null
        _currentPosition.value = 0L
        stopProgressTracker()
        unregisterBecomingNoisyReceiver()
        abandonAudioFocus()
    }

    fun removeSongFromQueue(song: SongEntity) {
        val currentQueue = _playbackQueue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == song.id }
        if (index != -1) {
            currentQueue.removeAt(index)
            _playbackQueue.value = currentQueue
            if (currentIndex == index) {
                if (currentQueue.isEmpty()) {
                    stopPlayback()
                } else {
                    nextSong()
                }
            } else if (currentIndex > index) {
                currentIndex--
            }
        }
    }

    fun removeDeletedSongsFromQueue(deadSongIds: List<String>) {
        if (deadSongIds.isEmpty()) return
        val deadSet = deadSongIds.toSet()
        val currentSongId = _currentSong.value?.id
        if (currentSongId != null && currentSongId in deadSet) {
            stopPlayback()
        }
        val currentQueue = _playbackQueue.value.toMutableList()
        val originalSize = currentQueue.size
        currentQueue.removeAll { it.id in deadSet }
        if (currentQueue.size != originalSize) {
            _playbackQueue.value = currentQueue
            if (currentIndex >= currentQueue.size) {
                currentIndex = (currentQueue.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun release() {
        stopAllPlayers()
        stopProgressTracker()
        unregisterBecomingNoisyReceiver()
        abandonAudioFocus()
        sleepTimerJob?.cancel()
        coroutineScope.cancel()
    }
}
