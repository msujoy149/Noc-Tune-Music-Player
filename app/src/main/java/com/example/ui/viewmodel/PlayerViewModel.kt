package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.PlaylistEntity
import com.example.data.model.SongEntity
import com.example.data.repository.MusicRepository
import com.example.player.MusicPlayerManager
import com.example.player.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    
    // Playback States connected reactively from Manager
    val currentSong: StateFlow<SongEntity?> = MusicPlayerManager.currentSong
    val isPlaying: StateFlow<Boolean> = MusicPlayerManager.isPlaying
    val currentPosition: StateFlow<Long> = MusicPlayerManager.currentPosition
    val playbackQueue: StateFlow<List<SongEntity>> = MusicPlayerManager.playbackQueue
    val shuffleEnabled: StateFlow<Boolean> = MusicPlayerManager.shuffleEnabled
    val repeatMode: StateFlow<RepeatMode> = MusicPlayerManager.repeatMode
    val sleepTimerRemaining: StateFlow<Long> = MusicPlayerManager.sleepTimerRemaining
    val stopAfterCurrentSong: StateFlow<Boolean> = MusicPlayerManager.stopAfterCurrentSong
    val audioRoute = MusicPlayerManager.audioRoute

    // Database Feeds
    val allSongs: StateFlow<List<SongEntity>>
    val favoriteSongs: StateFlow<List<SongEntity>>
    val lastAddedSongs: StateFlow<List<SongEntity>>
    val mostPlayedSongs: StateFlow<List<SongEntity>>
    val recentlyPlayedSongs: StateFlow<List<SongEntity>>
    val playlists: StateFlow<List<PlaylistEntity>>

    // UI Interactive States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    // Results filtered by search
    private val _filteredSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val filteredSongs = _filteredSongs.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _songsInSelectedPlaylist = MutableStateFlow<List<SongEntity>>(emptyList())
    val songsInSelectedPlaylist = _songsInSelectedPlaylist.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MusicRepository(application, database.songDao())
        MusicPlayerManager.init(application)

        // Standard feeds bound to viewModel scope
        allSongs = repository.allSongs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        favoriteSongs = repository.favoriteSongs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        lastAddedSongs = repository.lastAddedSongs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        mostPlayedSongs = repository.mostPlayedSongs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        recentlyPlayedSongs = repository.recentlyPlayedSongs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        playlists = repository.allPlaylists
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Launch essential initial setups
        viewModelScope.launch {
            repository.initDefaultGenerativeTracks()
            repository.scanLocalMusic()
        }

        // Setup automated query filtering
        combine(allSongs, _searchQuery) { songs, query ->
            if (query.isBlank()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
                }
            }
        }.onEach {
            _filteredSongs.value = it
        }.launchIn(viewModelScope)

        // Observe custom playlist songs whenever target selection changes
        _selectedPlaylist
            .flatMapLatest { playlist ->
                if (playlist != null) {
                    repository.getSongsForPlaylist(playlist.id)
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
            .onEach { songs ->
                _songsInSelectedPlaylist.value = songs
            }
            .launchIn(viewModelScope)
    }

    fun scanMusicFiles() {
        viewModelScope.launch {
            repository.scanLocalMusic()
        }
    }

    // Search query update
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Playback delegate actions
    fun playSong(song: SongEntity, scopeList: List<SongEntity>) {
        viewModelScope.launch {
            MusicPlayerManager.setQueue(scopeList, scopeList.indexOf(song))
            MusicPlayerManager.playSong(song)
        }
    }

    fun resumeOrPause() {
        if (isPlaying.value) {
            MusicPlayerManager.pausePlayback()
        } else {
            MusicPlayerManager.resumePlayback()
        }
    }

    fun next() = MusicPlayerManager.nextSong()
    
    fun previous() = MusicPlayerManager.prevSong()

    fun seekTo(positionMs: Long) = MusicPlayerManager.seekTo(positionMs)

    fun toggleShuffle() = MusicPlayerManager.toggleShuffle()

    fun toggleRepeat() = MusicPlayerManager.toggleRepeat()

    fun toggleFavorite(song: SongEntity? = null) = MusicPlayerManager.toggleFavorite(song)

    fun toggleStopAfterCurrent() = MusicPlayerManager.toggleStopAfterCurrent()

    fun setSleepTimer(minutes: Int) = MusicPlayerManager.setSleepTimer(minutes)

    // Playlist Controls
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _selectedPlaylist.value = playlist
    }

    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            if (currentSong.value?.id == song.id) {
                MusicPlayerManager.stopPlayback()
            }
            MusicPlayerManager.removeSongFromQueue(song)
            repository.deleteSong(song)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // We do *not* release the player manager here since the player manager scope
        // needs to continue running when the app is swiped away for foreground service.
    }
}
