package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PlaylistEntity
import com.example.data.model.SongEntity
import com.example.player.MusicPlayerManager
import com.example.player.RepeatMode
import com.example.ui.components.*
import com.example.ui.theme.NocTuneTheme
import com.example.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("app_prefs", MODE_PRIVATE) }
            var isNightMode by remember { mutableStateOf(prefs.getBoolean("is_night_mode", true)) }
            var themeColorHex by remember { mutableStateOf(prefs.getString("theme_color_hex", null)) }
            var themeBrightness by remember { mutableStateOf(prefs.getFloat("theme_brightness", 0.6f)) }

            NocTuneTheme(darkTheme = isNightMode, themeColorHex = themeColorHex, themeBrightness = themeBrightness) {
                MainAppScreen(
                    viewModel = viewModel,
                    isNightMode = isNightMode,
                    onToggleNightMode = {
                        val nextMode = !isNightMode
                        isNightMode = nextMode
                        prefs.edit().putBoolean("is_night_mode", nextMode).apply()
                    },
                    themeColorHex = themeColorHex,
                    onChangeThemeColor = { newColorHex ->
                        themeColorHex = newColorHex
                        if (newColorHex == null) {
                            prefs.edit().remove("theme_color_hex").apply()
                        } else {
                            prefs.edit().putString("theme_color_hex", newColorHex).apply()
                        }
                    },
                    themeBrightness = themeBrightness,
                    onChangeThemeBrightness = { newBrightness ->
                        themeBrightness = newBrightness
                        prefs.edit().putFloat("theme_brightness", newBrightness).apply()
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainAppScreen(
    viewModel: PlayerViewModel,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    themeColorHex: String?,
    onChangeThemeColor: (String?) -> Unit,
    themeBrightness: Float = 0.6f,
    onChangeThemeBrightness: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Core states
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentProgress by viewModel.currentPosition.collectAsStateWithLifecycle()
    val playQueue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val sleepTimerMs by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val stopAfterCurrent by viewModel.stopAfterCurrentSong.collectAsStateWithLifecycle()

    // Database query streams
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val lastAdded by viewModel.lastAddedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val songsInSelectedPlaylist by viewModel.songsInSelectedPlaylist.collectAsStateWithLifecycle()
    val audioRoute by viewModel.audioRoute.collectAsStateWithLifecycle()

    // Navigation and Popup management indices
    var currentTab by remember { mutableStateOf("home") } // "home", "library", "search"
    var expandedPlayer by remember { mutableStateOf(false) }
    var activeLibrarySection by remember { mutableStateOf("songs") } // "songs", "albums", "artists", "playlists"
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    
    // Dialog overlays toggles
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistInput by remember { mutableStateOf(false) }
    var showAddToPlaylistSelector by remember { mutableStateOf<SongEntity?>(null) }
    var showQueueDrawer by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showGlobalThemeDialog by remember { mutableStateOf(false) }
    var showAboutUsDialog by remember { mutableStateOf(false) }

    // App dynamic visuals Constants from Theme
    val appColors = com.example.ui.theme.LocalAppColors.current
    val deepEspresso = appColors.deepEspresso
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    // System Back Press Handler for smooth and robust navigation
    androidx.activity.compose.BackHandler(
        enabled = expandedPlayer || showQueueDrawer || showSleepTimerMenu || showAddToPlaylistSelector != null || showCreatePlaylistInput || selectedPlaylist != null || selectedAlbum != null || selectedArtist != null || searchQuery.isNotEmpty() || currentTab != "home" || showEqualizerPanel || showGlobalThemeDialog || showAboutUsDialog
    ) {
        when {
            showAboutUsDialog -> showAboutUsDialog = false
            showGlobalThemeDialog -> showGlobalThemeDialog = false
            showEqualizerPanel -> showEqualizerPanel = false
            showQueueDrawer -> showQueueDrawer = false
            showSleepTimerMenu -> showSleepTimerMenu = false
            showAddToPlaylistSelector != null -> showAddToPlaylistSelector = null
            showCreatePlaylistInput -> showCreatePlaylistInput = false
            expandedPlayer -> expandedPlayer = false
            selectedAlbum != null -> selectedAlbum = null
            selectedArtist != null -> selectedArtist = null
            selectedPlaylist != null -> viewModel.selectPlaylist(null)
            searchQuery.isNotEmpty() -> viewModel.updateSearchQuery("")
            currentTab != "home" -> currentTab = "home"
        }
    }

    // Check & request runtime permissions for music files
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[audioPermission] ?: false
        if (audioGranted) {
            viewModel.scanMusicFiles()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(context, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(audioPermission)
        } else {
            viewModel.scanMusicFiles()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            launcher.launch(permissionsToRequest.toTypedArray())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (!isWideScreen) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = deepEspresso,
                bottomBar = {
                    if (!expandedPlayer) {
                        Column(
                            modifier = Modifier
                                .background(deepEspresso)
                                .navigationBarsPadding()
                        ) {
                            // Inline floating mini player sits above bottom navigation bar
                            val activeSong = currentSong
                            if (activeSong != null) {
                                MiniFloatingPlayer(
                                    song = activeSong,
                                    isPlaying = isPlaying,
                                    onPlayPauseToggle = { viewModel.resumeOrPause() },
                                    onClick = { expandedPlayer = true }
                                )
                            }

                            NavigationBar(
                            containerColor = darkMocha.copy(alpha = 0.9f),
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, Color(0x24B08968), RoundedCornerShape(24.dp))
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            NavigationBarItem(
                                selected = currentTab == "home",
                                onClick = { 
                                    currentTab = "home" 
                                },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home tab icon") },
                                label = { Text("Home", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = deepEspresso,
                                    selectedTextColor = softLatte,
                                    indicatorColor = softLatte,
                                    unselectedIconColor = secondaryText,
                                    unselectedTextColor = secondaryText
                                ),
                                modifier = Modifier.testTag("nav_home").coffeeFocusHighlight(CircleShape)
                            )
                            NavigationBarItem(
                                selected = currentTab == "library",
                                onClick = { 
                                    if (currentTab == "library") {
                                        selectedAlbum = null
                                        selectedArtist = null
                                        viewModel.selectPlaylist(null)
                                    } else {
                                        currentTab = "library"
                                    }
                                },
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library tab icon") },
                                label = { Text("Library", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = deepEspresso,
                                    selectedTextColor = softLatte,
                                    indicatorColor = softLatte,
                                    unselectedIconColor = secondaryText,
                                    unselectedTextColor = secondaryText
                                ),
                                modifier = Modifier.testTag("nav_library").coffeeFocusHighlight(CircleShape)
                            )
                            NavigationBarItem(
                                selected = currentTab == "search",
                                onClick = { currentTab = "search" },
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search tab icon") },
                                label = { Text("Search", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = deepEspresso,
                                    selectedTextColor = softLatte,
                                    indicatorColor = softLatte,
                                    unselectedIconColor = secondaryText,
                                    unselectedTextColor = secondaryText
                                ),
                                modifier = Modifier.testTag("nav_search").coffeeFocusHighlight(CircleShape)
                            )
                        }
                    }
                }
            }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Tab router with smooth, responsive fade transitions to eliminate harsh flashing
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300, easing = LinearOutSlowInEasing)) togetherWith
                                    fadeOut(animationSpec = tween(250, easing = LinearOutSlowInEasing))
                        },
                        label = "TabRouterPhone"
                    ) { targetTab ->
                        when (targetTab) {
                            "home" -> HomeScreen(
                                favoriteSongs = favoriteSongs,
                                lastAdded = lastAdded,
                                allSongsList = allSongs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onPlaySong = { s -> viewModel.playSong(s, allSongs) },
                                onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                isNightMode = isNightMode,
                                onToggleNightMode = onToggleNightMode,
                                onTriggerTheme = { showGlobalThemeDialog = true },
                                onTriggerSleepTimer = { showSleepTimerMenu = true },
                                onTriggerEqualizer = { showEqualizerPanel = true },
                                onTriggerAboutUs = { showAboutUsDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            "library" -> LibraryScreen(
                                songs = allSongs,
                                playlists = playlists,
                                isNightMode = isNightMode,
                                onToggleNightMode = onToggleNightMode,
                                selectedPlaylist = selectedPlaylist,
                                songsInSelectedPlaylist = songsInSelectedPlaylist,
                                selectedAlbum = selectedAlbum,
                                onSelectAlbum = { selectedAlbum = it },
                                selectedArtist = selectedArtist,
                                onSelectArtist = { selectedArtist = it },
                                activeSection = activeLibrarySection,
                                onSectionChange = { activeLibrarySection = it },
                                onPlaySong = { s, list -> viewModel.playSong(s, list) },
                                onSelectPlaylist = { viewModel.selectPlaylist(it) },
                                onCreatePlaylistRequest = { showCreatePlaylistInput = true },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onRemoveSongFromPlaylist = { pid, sid -> viewModel.removeSongFromPlaylist(pid, sid) },
                                onDeleteSong = { viewModel.deleteSong(it) },
                                onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxSize(),
                                onAddSongsToPlaylist = { songsList, playlist ->
                                    songsList.forEach { s -> viewModel.addSongToPlaylist(playlist.id, s.id) }
                                },
                                onTriggerEqualizer = { showEqualizerPanel = true },
                                onTriggerTheme = { showGlobalThemeDialog = true },
                                onTriggerSleepTimer = { showSleepTimerMenu = true },
                                onTriggerAboutUs = { showAboutUsDialog = true }
                            )
                            
                            "search" -> SearchScreen(
                                songs = filteredSongs,
                                query = searchQuery,
                                isNightMode = isNightMode,
                                onToggleNightMode = onToggleNightMode,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onPlaySong = { s -> viewModel.playSong(s, filteredSongs) },
                                onDeleteSong = { viewModel.deleteSong(it) },
                                onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxSize(),
                                playlists = playlists,
                                onAddSongsToPlaylist = { songsList, playlist ->
                                    songsList.forEach { s -> viewModel.addSongToPlaylist(playlist.id, s.id) }
                                },
                                onCreatePlaylistRequest = { showCreatePlaylistInput = true },
                                onTriggerTheme = { showGlobalThemeDialog = true },
                                onTriggerSleepTimer = { showSleepTimerMenu = true },
                                onTriggerEqualizer = { showEqualizerPanel = true },
                                onTriggerAboutUs = { showAboutUsDialog = true }
                            )
                        }
                    }

                    // Full Expanded Immersive Player Screen (slides up)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expandedPlayer,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                        )
                    ) {
                        val activeSong = currentSong
                        if (activeSong != null) {
                            FullPlayerScreen(
                                song = activeSong,
                                isPlaying = isPlaying,
                                progress = currentProgress,
                                shuffleEnabled = shuffleEnabled,
                                repeatMode = repeatMode,
                                sleepTimerRemainingMs = sleepTimerMs,
                                stopAfterCurrentEnabled = stopAfterCurrent,
                                audioRoute = audioRoute,
                                onMinimize = { expandedPlayer = false },
                                onPlayPause = { viewModel.resumeOrPause() },
                                onNext = { viewModel.next() },
                                onPrevious = { viewModel.previous() },
                                onSeek = { viewModel.seekTo(it) },
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onToggleRepeat = { viewModel.toggleRepeat() },
                                onToggleFavorite = { viewModel.toggleFavorite() },
                                onTriggerSleepTimerMenu = { showSleepTimerMenu = true },
                                onTriggerQueueDrawer = { showQueueDrawer = true },
                                themeColorHex = themeColorHex,
                                onChangeThemeColor = onChangeThemeColor,
                                themeBrightness = themeBrightness,
                                onChangeThemeBrightness = onChangeThemeBrightness,
                                onTriggerEqualizer = { showEqualizerPanel = true },
                                onTriggerAboutUs = { showAboutUsDialog = true }
                            )
                        }
                    }

                    // Sliding active playlist queue Drawer
                    if (showQueueDrawer) {
                        ActiveQueueDrawer(
                            queue = playQueue,
                            activeSongIndex = playQueue.indexOf(currentSong),
                            onDismiss = { showQueueDrawer = false },
                            onPlaySong = { s -> viewModel.playSong(s, playQueue) }
                        )
                    }
                }
            }
        } else {
            // WIDESCREEN / TABLET / TV / LANDSCAPE LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(deepEspresso)
            ) {
                // Side Navigation Rail (anchored on left)
                NavigationRail(
                    containerColor = darkMocha,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "App logo",
                                tint = softLatte,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Noc Tune",
                                color = warmCream,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = currentTab == "home",
                        onClick = { currentTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 10.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = deepEspresso,
                            selectedTextColor = softLatte,
                            indicatorColor = softLatte,
                            unselectedIconColor = secondaryText,
                            unselectedTextColor = secondaryText
                        ),
                        modifier = Modifier.coffeeFocusHighlight(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = currentTab == "library",
                        onClick = { currentTab = "library" },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library", fontSize = 10.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = deepEspresso,
                            selectedTextColor = softLatte,
                            indicatorColor = softLatte,
                            unselectedIconColor = secondaryText,
                            unselectedTextColor = secondaryText
                        ),
                        modifier = Modifier.coffeeFocusHighlight(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = currentTab == "search",
                        onClick = { currentTab = "search" },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search", fontSize = 10.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = deepEspresso,
                            selectedTextColor = softLatte,
                            indicatorColor = softLatte,
                            unselectedIconColor = secondaryText,
                            unselectedTextColor = secondaryText
                        ),
                        modifier = Modifier.coffeeFocusHighlight(CircleShape)
                    )
                    Spacer(modifier = Modifier.weight(2f))
                }

                VerticalDivider(
                    color = Color(0x24B08968),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                // Main body area (on right of NavigationRail) with safe status and navigation bars padding
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Apply center-constrained width to tablet views to keep elements readable
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 1000.dp)
                                .align(Alignment.Center)
                        ) {
                            // Tab router with smooth, responsive fade transitions to eliminate harsh flashing
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(300, easing = LinearOutSlowInEasing)) togetherWith
                                            fadeOut(animationSpec = tween(250, easing = LinearOutSlowInEasing))
                                },
                                label = "TabRouterWidescreen"
                            ) { targetTab ->
                                when (targetTab) {
                                    "home" -> HomeScreen(
                                        favoriteSongs = favoriteSongs,
                                        lastAdded = lastAdded,
                                        allSongsList = allSongs,
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        onPlaySong = { s -> viewModel.playSong(s, allSongs) },
                                        onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                        isNightMode = isNightMode,
                                        onToggleNightMode = onToggleNightMode,
                                        onTriggerTheme = { showGlobalThemeDialog = true },
                                        onTriggerSleepTimer = { showSleepTimerMenu = true },
                                        onTriggerEqualizer = { showEqualizerPanel = true },
                                        onTriggerAboutUs = { showAboutUsDialog = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    "library" -> LibraryScreen(
                                        songs = allSongs,
                                        playlists = playlists,
                                        isNightMode = isNightMode,
                                        onToggleNightMode = onToggleNightMode,
                                        selectedPlaylist = selectedPlaylist,
                                        songsInSelectedPlaylist = songsInSelectedPlaylist,
                                        selectedAlbum = selectedAlbum,
                                        onSelectAlbum = { selectedAlbum = it },
                                        selectedArtist = selectedArtist,
                                        onSelectArtist = { selectedArtist = it },
                                        activeSection = activeLibrarySection,
                                        onSectionChange = { activeLibrarySection = it },
                                        onPlaySong = { s, list -> viewModel.playSong(s, list) },
                                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                                        onCreatePlaylistRequest = { showCreatePlaylistInput = true },
                                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                        onRemoveSongFromPlaylist = { pid, sid -> viewModel.removeSongFromPlaylist(pid, sid) },
                                        onDeleteSong = { viewModel.deleteSong(it) },
                                        onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxSize(),
                                        onAddSongsToPlaylist = { songsList, playlist ->
                                            songsList.forEach { s -> viewModel.addSongToPlaylist(playlist.id, s.id) }
                                        },
                                        onTriggerEqualizer = { showEqualizerPanel = true },
                                        onTriggerTheme = { showGlobalThemeDialog = true },
                                        onTriggerSleepTimer = { showSleepTimerMenu = true },
                                        onTriggerAboutUs = { showAboutUsDialog = true }
                                    )
                                    
                                    "search" -> SearchScreen(
                                        songs = filteredSongs,
                                        query = searchQuery,
                                        isNightMode = isNightMode,
                                        onToggleNightMode = onToggleNightMode,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onPlaySong = { s -> viewModel.playSong(s, filteredSongs) },
                                        onDeleteSong = { viewModel.deleteSong(it) },
                                        onAddToPlaylistRequest = { s -> showAddToPlaylistSelector = s },
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxSize(),
                                        playlists = playlists,
                                        onAddSongsToPlaylist = { songsList, playlist ->
                                            songsList.forEach { s -> viewModel.addSongToPlaylist(playlist.id, s.id) }
                                        },
                                        onCreatePlaylistRequest = { showCreatePlaylistInput = true },
                                        onTriggerTheme = { showGlobalThemeDialog = true },
                                        onTriggerSleepTimer = { showSleepTimerMenu = true },
                                        onTriggerEqualizer = { showEqualizerPanel = true },
                                        onTriggerAboutUs = { showAboutUsDialog = true }
                                    )
                                }
                            }
                        }

                        // Full Expanded Immersive Player Screen (slides up)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = expandedPlayer,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                            )
                        ) {
                            val activeSong = currentSong
                            if (activeSong != null) {
                                FullPlayerScreen(
                                    song = activeSong,
                                    isPlaying = isPlaying,
                                    progress = currentProgress,
                                    shuffleEnabled = shuffleEnabled,
                                    repeatMode = repeatMode,
                                    sleepTimerRemainingMs = sleepTimerMs,
                                    stopAfterCurrentEnabled = stopAfterCurrent,
                                    onMinimize = { expandedPlayer = false },
                                    onPlayPause = { viewModel.resumeOrPause() },
                                    onNext = { viewModel.next() },
                                    onPrevious = { viewModel.previous() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onToggleShuffle = { viewModel.toggleShuffle() },
                                    onToggleRepeat = { viewModel.toggleRepeat() },
                                    onToggleFavorite = { viewModel.toggleFavorite() },
                                    onTriggerSleepTimerMenu = { showSleepTimerMenu = true },
                                    onTriggerQueueDrawer = { showQueueDrawer = true },
                                    themeColorHex = themeColorHex,
                                    onChangeThemeColor = onChangeThemeColor,
                                    themeBrightness = themeBrightness,
                                    onChangeThemeBrightness = onChangeThemeBrightness,
                                    onTriggerEqualizer = { showEqualizerPanel = true },
                                    onTriggerAboutUs = { showAboutUsDialog = true }
                                )
                            }
                        }

                        // Sliding active playlist queue Drawer
                        if (showQueueDrawer) {
                            ActiveQueueDrawer(
                                queue = playQueue,
                                activeSongIndex = playQueue.indexOf(currentSong),
                                onDismiss = { showQueueDrawer = false },
                                onPlaySong = { s -> viewModel.playSong(s, playQueue) }
                            )
                        }
                    }

                    // Mini player sits perfectly at the bottom in widescreen mode
                    val activeSong = currentSong
                    if (activeSong != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(deepEspresso)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .align(Alignment.Center)
                            ) {
                                MiniFloatingPlayer(
                                    song = activeSong,
                                    isPlaying = isPlaying,
                                    onPlayPauseToggle = { viewModel.resumeOrPause() },
                                    onClick = { expandedPlayer = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Setup Sleep clock countdown
    if (showSleepTimerMenu) {
        SleepTimerDialog(
            currentRemainingMs = sleepTimerMs,
            stopAfterCurrentEnabled = stopAfterCurrent,
            onDismiss = { showSleepTimerMenu = false },
            onSetTimer = { mins -> viewModel.setSleepTimer(mins) },
            onToggleStopAfterCurrent = { viewModel.toggleStopAfterCurrent() }
        )
    }

    // Dialog: Create dynamic Playlist
    if (showCreatePlaylistInput) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistInput = false },
            onCreate = { name -> viewModel.createPlaylist(name) }
        )
    }

    // Dialog: Add song to playlist
    val targetSong = showAddToPlaylistSelector
    if (targetSong != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistSelector = null },
            onSelectPlaylist = { playlist ->
                viewModel.addSongToPlaylist(playlist.id, targetSong.id)
                Toast.makeText(context, "Added to playlist ${playlist.name}", Toast.LENGTH_SHORT).show()
                showAddToPlaylistSelector = null
            },
            onCreateNewPlaylist = { showCreatePlaylistInput = true }
        )
    }

    // Dialog: Equalizer panel
    if (showEqualizerPanel) {
        EqualizerPanel(
            isPlaying = isPlaying,
            isNightMode = isNightMode,
            onDismissRequest = { showEqualizerPanel = false }
        )
    }

    if (showGlobalThemeDialog) {
        ThemeColorPickerDialog(
            currentThemeColorHex = themeColorHex,
            themeBrightness = themeBrightness,
            onBrightnessChanged = onChangeThemeBrightness,
            onColorSelected = onChangeThemeColor,
            onDismissRequest = { showGlobalThemeDialog = false }
        )
    }

    if (showAboutUsDialog) {
        AboutUsDialog(
            onDismissRequest = { showAboutUsDialog = false }
        )
    }
}

// ==========================================
// SCREEN: HOME TAB
// ==========================================
@Composable
fun HomeScreen(
    favoriteSongs: List<SongEntity>,
    lastAdded: List<SongEntity>,
    allSongsList: List<SongEntity>,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    onPlaySong: (SongEntity) -> Unit,
    onAddToPlaylistRequest: (SongEntity) -> Unit,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    onTriggerTheme: () -> Unit = {},
    onTriggerSleepTimer: () -> Unit = {},
    onTriggerEqualizer: () -> Unit = {},
    onTriggerAboutUs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 48.dp)
    ) {
        // Welcome and App branding Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lost in Classics",
                    color = warmCream,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleNightMode,
                        modifier = Modifier.size(48.dp).testTag("night_mode_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Night/Light Mode",
                            tint = if (isNightMode) coffeeBrown else Color(0xFFFF9F1C),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    SettingsMoreMenu(
                        onTriggerTheme = onTriggerTheme,
                        onTriggerSleepTimer = onTriggerSleepTimer,
                        onTriggerEqualizer = onTriggerEqualizer,
                        onTriggerAboutUs = onTriggerAboutUs,
                        tint = warmCream
                    )
                }
            }
        }

        // Section: Favorite Music (Favourite Music)
        item {
            HomeSectionHeader(title = "Favourite Music")
            if (favoriteSongs.isEmpty()) {
                EmptyStateCard(message = "Tap the heart on any song to create your custom espresso favorites.")
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(favoriteSongs) { song ->
                        PlayableLoungeCard(
                            song = song,
                            onClick = { onPlaySong(song) },
                            onLongClick = { onAddToPlaylistRequest(song) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }

        // Section: Latest Music (Latest Music)
        item {
            HomeSectionHeader(title = "Latest Music")
            if (lastAdded.isEmpty()) {
                EmptyStateCard(message = "No recently added tracks. Your local music library files will appear here.")
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(lastAdded) { song ->
                        PlayableLoungeCard(
                            song = song,
                            onClick = { onPlaySong(song) },
                            onLongClick = { onAddToPlaylistRequest(song) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }

        // Global default catalog of preset melodies if no scanned tracks
        if (allSongsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(darkMocha)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "No music available",
                            tint = coffeeBrown,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Music Library is Empty",
                            color = warmCream,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Import local audio files or scan storage to enjoy your classic collection.",
                            color = secondaryText,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSectionHeader(title: String) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    Text(
        text = title,
        color = appColors.warmCream,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

// ==========================================
// SCREEN: LIBRARY TAB
// ==========================================
@Composable
fun LibraryScreen(
    songs: List<SongEntity>,
    playlists: List<PlaylistEntity>,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    selectedPlaylist: PlaylistEntity?,
    songsInSelectedPlaylist: List<SongEntity>,
    selectedAlbum: String? = null,
    onSelectAlbum: (String?) -> Unit = {},
    selectedArtist: String? = null,
    onSelectArtist: (String?) -> Unit = {},
    activeSection: String,
    onSectionChange: (String) -> Unit,
    onPlaySong: (SongEntity, List<SongEntity>) -> Unit,
    onSelectPlaylist: (PlaylistEntity?) -> Unit,
    onCreatePlaylistRequest: () -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onRemoveSongFromPlaylist: (Int, String) -> Unit,
    onDeleteSong: (SongEntity) -> Unit,
    onAddToPlaylistRequest: (SongEntity) -> Unit,
    currentSong: SongEntity? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onAddSongsToPlaylist: (List<SongEntity>, PlaylistEntity) -> Unit = { _, _ -> },
    onTriggerEqualizer: () -> Unit = {},
    onTriggerTheme: () -> Unit = {},
    onTriggerSleepTimer: () -> Unit = {},
    onTriggerAboutUs: () -> Unit = {}
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val deepEspresso = appColors.deepEspresso
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText
    val isNight = appColors.isNight

    val context = androidx.compose.ui.platform.LocalContext.current

    var activeSongForOptions by remember { mutableStateOf<SongEntity?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<SongEntity>>(emptySet()) }
    var showAddToPlaylistForSongs by remember { mutableStateOf<Set<SongEntity>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Back-action indicator if inspecting playlist details
        if (selectedPlaylist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        onSelectPlaylist(null)
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back into catalog view button", tint = warmCream)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = selectedPlaylist.name, color = warmCream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${songsInSelectedPlaylist.size} songs compiled", color = secondaryText, fontSize = 12.sp)
                }
                IconButton(
                    onClick = { onDeletePlaylist(selectedPlaylist) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete custom compiled playlist", tint = Color.Red.copy(alpha = 0.8f))
                }
            }

            // Quick Play All and Shuffle actions for Playlist
            if (songsInSelectedPlaylist.isNotEmpty() && !isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlaySong(songsInSelectedPlaylist.first(), songsInSelectedPlaylist)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = coffeeBrown),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = deepEspresso, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = deepEspresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = songsInSelectedPlaylist.shuffled()
                            onPlaySong(shuffled.first(), shuffled)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = warmCream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = softLatte, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = warmCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Render selected playlist songs
            if (songsInSelectedPlaylist.isEmpty()) {
                EmptyStateCard(message = "This playlist is empty. Search tracks or browse standard lists and long-click to add!")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(songsInSelectedPlaylist) { song ->
                        val isActive = currentSong?.id == song.id
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaySong(song, songsInSelectedPlaylist) }
                                    .background(if (isActive) (if (isNight) Color(0x1F9575CD) else Color(0x0F000000)) else Color.Transparent)
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    // Elegant rounded-square music note container from 2nd picture style
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(coffeeBrown.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = softLatte,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isActive) softLatte else warmCream,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (song.artist.isBlank() || song.artist.contains("<unknown>", ignoreCase = true)) "<unknown>" else song.artist,
                                            color = secondaryText.copy(alpha = 0.82f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isActive) {
                                        EqualizerAnimation(
                                            isPlaying = isPlaying,
                                            color = softLatte,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveSongFromPlaylist(selectedPlaylist.id, song.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircleOutline,
                                        contentDescription = "Remove music track item",
                                        tint = Color.Red.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = if (isNight) Color(0x1A9575CD) else Color(0x0E000000),
                                thickness = 0.5.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        } else if (selectedAlbum != null) {
            val albumTitle = selectedAlbum
            val albumSongs = songs.filter { it.album == albumTitle }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        onSelectAlbum(null)
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = warmCream)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = albumTitle, color = warmCream, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${albumSongs.size} tracks from this album", color = secondaryText, fontSize = 12.sp)
                }
                if (!isMultiSelectMode && albumSongs.isNotEmpty()) {
                    IconButton(
                        onClick = { 
                            isMultiSelectMode = true
                            selectedSongs = emptySet()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Checklist, contentDescription = "Select songs", tint = warmCream)
                    }
                }
            }

            // Quick Play All and Shuffle actions for Album
            if (albumSongs.isNotEmpty() && !isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlaySong(albumSongs.first(), albumSongs)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = coffeeBrown),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = deepEspresso, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = deepEspresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = albumSongs.shuffled()
                            onPlaySong(shuffled.first(), shuffled)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = warmCream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = softLatte, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = warmCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Multi-selection block
            if (isMultiSelectMode) {
                MultiSelectionControlBar(
                    selectedCount = selectedSongs.size,
                    totalCount = albumSongs.size,
                    onSelectAll = { selectedSongs = albumSongs.toSet() },
                    onDeselectAll = { selectedSongs = emptySet() },
                    onCancel = { 
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    onAddToPlaylist = { showAddToPlaylistForSongs = selectedSongs },
                    onDelete = {
                        selectedSongs.forEach { onDeleteSong(it) }
                        Toast.makeText(context, "Deleted ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    }
                )
            }

            if (albumSongs.isEmpty()) {
                EmptyStateCard(message = "This album contains no tracks.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(albumSongs) { song ->
                        val isActive = currentSong?.id == song.id
                        val isSelected = selectedSongs.contains(song)
                        SongListItem(
                            song = song,
                            onClick = { 
                                if (isMultiSelectMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                } else {
                                    onPlaySong(song, albumSongs) 
                                }
                            },
                            onLongClick = { 
                                if (isMultiSelectMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                } else {
                                    activeSongForOptions = song
                                }
                            },
                            isPlaying = isPlaying,
                            isActive = isActive,
                            showCheckbox = isMultiSelectMode,
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedSongs = if (checked) selectedSongs + song else selectedSongs - song
                            }
                        )
                    }
                }
            }
        } else if (selectedArtist != null) {
            val artistName = selectedArtist
            val artistSongs = songs.filter { it.artist == artistName }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        onSelectArtist(null)
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = warmCream)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = artistName, color = warmCream, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${artistSongs.size} tracks by this artist", color = secondaryText, fontSize = 12.sp)
                }
                if (!isMultiSelectMode && artistSongs.isNotEmpty()) {
                    IconButton(
                        onClick = { 
                            isMultiSelectMode = true
                            selectedSongs = emptySet()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Checklist, contentDescription = "Select songs", tint = warmCream)
                    }
                }
            }

            // Quick Play All and Shuffle actions for Artist
            if (artistSongs.isNotEmpty() && !isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlaySong(artistSongs.first(), artistSongs)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = coffeeBrown),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = deepEspresso, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = deepEspresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = artistSongs.shuffled()
                            onPlaySong(shuffled.first(), shuffled)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = warmCream),
                        border = androidx.compose.foundation.BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = softLatte, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = warmCream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Multi-selection block
            if (isMultiSelectMode) {
                MultiSelectionControlBar(
                    selectedCount = selectedSongs.size,
                    totalCount = artistSongs.size,
                    onSelectAll = { selectedSongs = artistSongs.toSet() },
                    onDeselectAll = { selectedSongs = emptySet() },
                    onCancel = { 
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    onAddToPlaylist = { showAddToPlaylistForSongs = selectedSongs },
                    onDelete = {
                        selectedSongs.forEach { onDeleteSong(it) }
                        Toast.makeText(context, "Deleted ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    }
                )
            }

            if (artistSongs.isEmpty()) {
                EmptyStateCard(message = "This artist has no tracks.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(artistSongs) { song ->
                        val isActive = currentSong?.id == song.id
                        val isSelected = selectedSongs.contains(song)
                        SongListItem(
                            song = song,
                            onClick = { 
                                if (isMultiSelectMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                } else {
                                    onPlaySong(song, artistSongs) 
                                }
                            },
                            onLongClick = { 
                                if (isMultiSelectMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                } else {
                                    activeSongForOptions = song
                                }
                            },
                            isPlaying = isPlaying,
                            isActive = isActive,
                            showCheckbox = isMultiSelectMode,
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedSongs = if (checked) selectedSongs + song else selectedSongs - song
                            }
                        )
                    }
                }
            }
        } else {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Studio Library",
                    color = warmCream,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleNightMode,
                        modifier = Modifier.size(48.dp).testTag("night_mode_toggle_library")
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Night/Light Mode",
                            tint = if (isNightMode) coffeeBrown else Color(0xFFFF9F1C),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    SettingsMoreMenu(
                        onTriggerTheme = onTriggerTheme,
                        onTriggerSleepTimer = onTriggerSleepTimer,
                        onTriggerEqualizer = onTriggerEqualizer,
                        onTriggerAboutUs = onTriggerAboutUs,
                        tint = warmCream
                    )
                }
            }

            // Multi-selection block for "songs" tab
            if (isMultiSelectMode && activeSection == "songs") {
                MultiSelectionControlBar(
                    selectedCount = selectedSongs.size,
                    totalCount = songs.size,
                    onSelectAll = { selectedSongs = songs.toSet() },
                    onDeselectAll = { selectedSongs = emptySet() },
                    onCancel = { 
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    },
                    onAddToPlaylist = { showAddToPlaylistForSongs = selectedSongs },
                    onDelete = {
                        selectedSongs.forEach { onDeleteSong(it) }
                        Toast.makeText(context, "Deleted ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                        isMultiSelectMode = false
                        selectedSongs = emptySet()
                    }
                )
            }

            // Section Filter Selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sections = listOf(
                    "songs" to "Songs",
                    "albums" to "Albums",
                    "artists" to "Artists",
                    "playlists" to "Playlists"
                )
                sections.forEach { (secId, secLabel) ->
                    val isSelected = activeSection == secId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) coffeeBrown else darkMocha)
                            .clickable {
                                isMultiSelectMode = false
                                selectedSongs = emptySet()
                                onSectionChange(secId) 
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = secLabel,
                            color = if (isSelected) deepEspresso else secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Selected Sub-Section rendering
            when (activeSection) {
                "songs" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(songs) { song ->
                            val isActive = currentSong?.id == song.id
                            val isSelected = selectedSongs.contains(song)
                            SongListItem(
                                song = song,
                                onClick = { 
                                    if (isMultiSelectMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                    } else {
                                        onPlaySong(song, songs) 
                                    }
                                },
                                onLongClick = { 
                                    if (isMultiSelectMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                                    } else {
                                        activeSongForOptions = song
                                    }
                                },
                                isPlaying = isPlaying,
                                isActive = isActive,
                                showCheckbox = isMultiSelectMode,
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedSongs = if (checked) selectedSongs + song else selectedSongs - song
                                }
                            )
                        }
                    }
                }
                
                "albums" -> {
                    // Group songs dynamically by album
                    val albumsGroups = songs.groupBy { it.album }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albumsGroups.keys.toList()) { albumTitle ->
                            val albumSongs = albumsGroups[albumTitle] ?: emptyList()
                            val repSong = albumSongs.firstOrNull()
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(darkMocha)
                                    .coffeeFocusHighlight(RoundedCornerShape(20.dp))
                                    .clickable {
                                        onSelectAlbum(albumTitle)
                                    }
                                    .padding(12.dp)
                            ) {
                                CoffeeAlbumArt(
                                    presetId = repSong?.generativePreset ?: "",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    songPath = repSong?.path
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = albumTitle,
                                    color = warmCream,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${albumSongs.size} tracks",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                
                "artists" -> {
                    // Group songs dynamically by artist
                    val artistGroups = songs.groupBy { it.artist }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(artistGroups.keys.toList()) { artistName ->
                            val artSongs = artistGroups[artistName] ?: emptyList()
                            val repSong = artSongs.firstOrNull()
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(darkMocha)
                                    .coffeeFocusHighlight(RoundedCornerShape(20.dp))
                                    .clickable {
                                        onSelectArtist(artistName)
                                    }
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Rounded profile visual styling for artists
                                CoffeeAlbumArt(
                                    presetId = repSong?.generativePreset ?: "",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape),
                                    songPath = repSong?.path
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = artistName,
                                    color = warmCream,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${artSongs.size} tracks listed",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                
                "playlists" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Playlists Add Header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(darkMocha)
                                .clickable { onCreatePlaylistRequest() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create blank compiler playlist", tint = coffeeBrown)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Initialize New Playlist", color = softLatte, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (playlists.isEmpty()) {
                            EmptyStateCard(message = "No custom compilation lists present yet. Initialize one above!")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(playlists) { playlist ->
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(darkMocha)
                                            .coffeeFocusHighlight(RoundedCornerShape(20.dp))
                                            .clickable { onSelectPlaylist(playlist) }
                                            .padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LibraryMusic,
                                            contentDescription = "Playlist folder visual emblem",
                                            tint = softLatte,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = playlist.name,
                                            color = warmCream,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.songCount} songs stored",
                                            color = secondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper dialog overlays
    activeSongForOptions?.let { song ->
        SongOptionsDialog(
            song = song,
            onDismiss = { activeSongForOptions = null },
            onAddToPlaylist = {
                activeSongForOptions = null
                onAddToPlaylistRequest(song)
            },
            onDelete = {
                activeSongForOptions = null
                onDeleteSong(song)
                Toast.makeText(context, "Deleted song ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onSelectMultiple = {
                activeSongForOptions = null
                isMultiSelectMode = true
                selectedSongs = setOf(song)
            }
        )
    }

    if (showAddToPlaylistForSongs != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistForSongs = null },
            onSelectPlaylist = { playlist ->
                showAddToPlaylistForSongs?.let { selected ->
                    onAddSongsToPlaylist(selected.toList(), playlist)
                    Toast.makeText(context, "${selected.size} songs added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                }
                showAddToPlaylistForSongs = null
                isMultiSelectMode = false
                selectedSongs = emptySet()
            },
            onCreateNewPlaylist = {
                onCreatePlaylistRequest()
                showAddToPlaylistForSongs = null
            }
        )
    }
}

@Composable
fun MultiSelectionControlBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val warmCream = appColors.warmCream
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val deepEspresso = appColors.deepEspresso

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = coffeeBrown.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount of $totalCount channels selected",
                    color = warmCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (selectedCount == totalCount) {
                                onDeselectAll()
                            } else {
                                onSelectAll()
                            }
                        }
                    ) {
                        Text(
                            text = if (selectedCount == totalCount) "Deselect All" else "Select All",
                            color = softLatte,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Close multi-select mode", tint = warmCream)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedCount > 0) {
                            onAddToPlaylist()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = coffeeBrown),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = deepEspresso)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Playlist", color = deepEspresso, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (selectedCount > 0) {
                            onDelete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// SCREEN: SEARCH TAB
// ==========================================
@Composable
fun SearchScreen(
    songs: List<SongEntity>,
    query: String,
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPlaySong: (SongEntity) -> Unit,
    onDeleteSong: (SongEntity) -> Unit,
    onAddToPlaylistRequest: (SongEntity) -> Unit,
    currentSong: SongEntity? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    playlists: List<PlaylistEntity> = emptyList(),
    onAddSongsToPlaylist: (List<SongEntity>, PlaylistEntity) -> Unit = { _, _ -> },
    onCreatePlaylistRequest: () -> Unit = {},
    onTriggerEqualizer: () -> Unit = {},
    onTriggerTheme: () -> Unit = {},
    onTriggerSleepTimer: () -> Unit = {},
    onTriggerAboutUs: () -> Unit = {}
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    val context = androidx.compose.ui.platform.LocalContext.current

    var activeSongForOptions by remember { mutableStateOf<SongEntity?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<SongEntity>>(emptySet()) }
    var showAddToPlaylistForSongs by remember { mutableStateOf<Set<SongEntity>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // App search title header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search Your Songs",
                color = warmCream,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleNightMode,
                    modifier = Modifier.size(48.dp).testTag("night_mode_toggle_search")
                ) {
                    Icon(
                        imageVector = if (isNightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Toggle Night/Light Mode",
                        tint = if (isNightMode) coffeeBrown else Color(0xFFFF9F1C),
                        modifier = Modifier.size(28.dp)
                    )
                }
                SettingsMoreMenu(
                    onTriggerTheme = onTriggerTheme,
                    onTriggerSleepTimer = onTriggerSleepTimer,
                    onTriggerEqualizer = onTriggerEqualizer,
                    onTriggerAboutUs = onTriggerAboutUs,
                    tint = warmCream
                )
            }
        }

        // Custom search text field
        OutlinedTextField(
            value = query,
            onValueChange = {
                isMultiSelectMode = false
                selectedSongs = emptySet()
                onQueryChange(it)
            },
            placeholder = { Text("Search songs, albums, and artists in Noc Tune...", color = secondaryText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Active search trigger magnifier", tint = secondaryText) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = coffeeBrown,
                unfocusedBorderColor = darkMocha,
                cursorColor = coffeeBrown,
                focusedTextColor = warmCream,
                unfocusedTextColor = warmCream
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_field"),
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Multi-selection bar
        if (isMultiSelectMode) {
            MultiSelectionControlBar(
                selectedCount = selectedSongs.size,
                totalCount = songs.size,
                onSelectAll = { selectedSongs = songs.toSet() },
                onDeselectAll = { selectedSongs = emptySet() },
                onCancel = { 
                    isMultiSelectMode = false
                    selectedSongs = emptySet()
                },
                onAddToPlaylist = { showAddToPlaylistForSongs = selectedSongs },
                onDelete = {
                    selectedSongs.forEach { onDeleteSong(it) }
                    Toast.makeText(context, "Deleted ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                    isMultiSelectMode = false
                    selectedSongs = emptySet()
                }
            )
        }

        // Search Results List
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tracks fit your query inside our lounge.",
                    color = secondaryText,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(songs) { song ->
                    val isActive = currentSong?.id == song.id
                    val isSelected = selectedSongs.contains(song)
                    SongListItem(
                         song = song,
                         onClick = { 
                             if (isMultiSelectMode) {
                                 selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                             } else {
                                 onPlaySong(song) 
                             }
                         },
                         onLongClick = { 
                             if (isMultiSelectMode) {
                                 selectedSongs = if (isSelected) selectedSongs - song else selectedSongs + song
                             } else {
                                 activeSongForOptions = song
                             }
                         },
                         isPlaying = isPlaying,
                         isActive = isActive,
                         showCheckbox = isMultiSelectMode,
                         checked = isSelected,
                         onCheckedChange = { checked ->
                             selectedSongs = if (checked) selectedSongs + song else selectedSongs - song
                         }
                    )
                }
            }
        }
    }

    // Overlay components
    activeSongForOptions?.let { song ->
        SongOptionsDialog(
            song = song,
            onDismiss = { activeSongForOptions = null },
            onAddToPlaylist = {
                activeSongForOptions = null
                onAddToPlaylistRequest(song)
            },
            onDelete = {
                activeSongForOptions = null
                onDeleteSong(song)
                Toast.makeText(context, "Deleted song ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onSelectMultiple = {
                activeSongForOptions = null
                isMultiSelectMode = true
                selectedSongs = setOf(song)
            }
        )
    }

    if (showAddToPlaylistForSongs != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistForSongs = null },
            onSelectPlaylist = { playlist ->
                showAddToPlaylistForSongs?.let { selected ->
                    onAddSongsToPlaylist(selected.toList(), playlist)
                    Toast.makeText(context, "${selected.size} songs added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                }
                showAddToPlaylistForSongs = null
                isMultiSelectMode = false
                selectedSongs = emptySet()
            },
            onCreateNewPlaylist = {
                onCreatePlaylistRequest()
                showAddToPlaylistForSongs = null
            }
        )
    }
}

// ==========================================
// SUB-COMPONENT: REUSABLE ITEMS & PILLS
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayableLoungeCard(
    song: SongEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(darkMocha, darkMocha.copy(alpha = 0.8f))
                )
            )
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
            .coffeeFocusHighlight(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    ) {
        CoffeeAlbumArt(
            presetId = song.generativePreset,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            songPath = song.path
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = song.title,
            color = warmCream,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (song.artist.isBlank() || song.artist.contains("<unknown>", ignoreCase = true)) "<unknown>" else song.artist,
            color = secondaryText.copy(alpha = 0.85f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isPlaying: Boolean = false,
    isActive: Boolean = false,
    isHighlighted: Boolean = false,
    showCheckbox: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText
    val isNight = appColors.isNight
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val deepEspresso = appColors.deepEspresso

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(
                    if (isHighlighted) Color.Red.copy(alpha = 0.15f)
                    else if (isActive) coffeeBrown.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = coffeeBrown,
                        uncheckedColor = secondaryText,
                        checkmarkColor = deepEspresso
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            // Elegant rounded-square music note container from 2nd picture style
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(coffeeBrown.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = softLatte,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isActive) softLatte else warmCream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (song.artist.isBlank() || song.artist.contains("<unknown>", ignoreCase = true)) "<unknown>" else song.artist,
                    color = secondaryText.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isActive) {
                EqualizerAnimation(
                    isPlaying = isPlaying,
                    color = softLatte,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (song.isFavorite) Color.Red else secondaryText.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
        HorizontalDivider(
            color = if (isNight) Color(0x1A9575CD) else Color(0x0E000000),
            thickness = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EmptyStateCard(message: String) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val secondaryText = appColors.secondaryText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(darkMocha.copy(alpha = 0.5f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = secondaryText,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ==========================================
// SUB-COMPONENT: FLOATING MINI PLAYER
// ==========================================
@Composable
fun MiniFloatingPlayer(
    song: SongEntity,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onClick: () -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    val shape = RoundedCornerShape(24.dp)
    
    // Smooth fade-in onset transition when music starts playing
    val strokeRevealAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "strokeReveal"
    )

    // Breathing pulse cycle representing playing/resonance flow
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .clip(shape)
        .background(darkMocha.copy(alpha = 0.95f))
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)

            // 1. Draw static muted border when relaxed/paused
            val baseBorderAlpha = (1f - strokeRevealAlpha) * 0.14f
            if (baseBorderAlpha > 0f) {
                drawOutline(
                    outline = outline,
                    color = Color(0xFFB08968).copy(alpha = baseBorderAlpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }

            // 2. Draw highly-polished thin glowing borders when active
            if (strokeRevealAlpha > 0f) {
                val glowColor = appColors.softLatte
                val alpha = strokeRevealAlpha * glowIntensity

                // Layer 1: Ambient outer halo (broad blur simulation)
                drawOutline(
                    outline = outline,
                    color = glowColor.copy(alpha = alpha * 0.15f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )

                // Layer 2: Medium glow accentuation
                drawOutline(
                    outline = outline,
                    color = glowColor.copy(alpha = alpha * 0.4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                // Layer 3: Main sharp glowing core (extremely thin: 0.8dp)
                drawOutline(
                    outline = outline,
                    color = glowColor.copy(alpha = strokeRevealAlpha * 0.95f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx())
                )
            }
        }
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .testTag("mini_player")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rotating album mini visual
        CoffeeAlbumArt(
            presetId = song.generativePreset,
            modifier = Modifier.size(42.dp),
            isPlaying = isPlaying,
            songPath = song.path
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = warmCream,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("mini_play_pause")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Floating playback toggle button",
                tint = coffeeBrown,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ==========================================
// SCREEN: FULL PLAYER DETAIL OVERLAY
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    song: SongEntity,
    isPlaying: Boolean,
    progress: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    sleepTimerRemainingMs: Long,
    stopAfterCurrentEnabled: Boolean,
    audioRoute: com.example.player.AudioRoute = com.example.player.AudioRoute.SPEAKER,
    onMinimize: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTriggerSleepTimerMenu: () -> Unit,
    onTriggerQueueDrawer: () -> Unit,
    themeColorHex: String? = null,
    onChangeThemeColor: (String?) -> Unit = {},
    themeBrightness: Float = 0.6f,
    onChangeThemeBrightness: (Float) -> Unit = {},
    onTriggerEqualizer: () -> Unit = {},
    onTriggerAboutUs: () -> Unit = {}
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val deepEspresso = appColors.deepEspresso
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val softLatte = appColors.softLatte
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    var showRemainingTime by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rawDragY = remember { FloatArray(1) { 0f } }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (true) {
                withFrameMillis {
                    dragOffsetY = rawDragY[0]
                }
            }
        } else {
            rawDragY[0] = 0f
            dragOffsetY = 0f
        }
    }

    val displayOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetY else 0f,
        animationSpec = if (isDragging) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "dragOffset"
    )

    if (showThemeDialog) {
        ThemeColorPickerDialog(
            currentThemeColorHex = themeColorHex,
            themeBrightness = themeBrightness,
            onBrightnessChanged = onChangeThemeBrightness,
            onColorSelected = onChangeThemeColor,
            onDismissRequest = { showThemeDialog = false }
        )
    }

    // Dynamic scrubbing position state (0.0f..1.0f)
    var localScrubbingProgress by remember { mutableStateOf<Float?>(null) }

    val liveProgressMs = if (localScrubbingProgress != null) {
        (localScrubbingProgress!! * song.duration).toLong().coerceIn(0L, song.duration)
    } else {
        progress
    }

    // Formatted timelines
    val currentFormatted = remember(liveProgressMs) {
        val totalSeconds = liveProgressMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    val durationFormatted = remember(liveProgressMs, song.duration, showRemainingTime) {
        if (showRemainingTime) {
            val remainMs = maxOf(0L, song.duration - liveProgressMs)
            val totalSeconds = remainMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format("-%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("-%02d:%02d", minutes, seconds)
            }
        } else {
            val totalSeconds = song.duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onMinimize) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var accumulatedDragY = 0f
                            var isVerticalDrag = false
                            val activePointerId = down.id
                            var lastY = down.position.y
                            var lastX = down.position.x

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == activePointerId } ?: break
                                if (!change.pressed) break

                                val currentY = change.position.y
                                val currentX = change.position.x
                                val diffY = currentY - lastY
                                val diffX = currentX - lastX

                                if (!isVerticalDrag) {
                                    accumulatedDragY += diffY
                                    val accumulatedDragX = currentX - down.position.x
                                    val touchSlop = 15f
                                    if (Math.abs(accumulatedDragY) > touchSlop && Math.abs(accumulatedDragY) > Math.abs(accumulatedDragX)) {
                                        if (accumulatedDragY > 0) {
                                            isVerticalDrag = true
                                            isDragging = true
                                            rawDragY[0] = accumulatedDragY
                                        }
                                    }
                                } else {
                                    rawDragY[0] = (rawDragY[0] + diffY).coerceAtLeast(-50f)
                                    change.consume()
                                }

                                lastY = currentY
                                lastX = currentX
                            }

                            if (isVerticalDrag) {
                                isDragging = false
                                if (rawDragY[0] > 180f) {
                                    onMinimize()
                                }
                            }
                        }
                    }
                }
                .offset { IntOffset(0, displayOffset.coerceAtLeast(0f).toInt()) }
                .background(deepEspresso)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Swallow all clicks/taps on the backdrop area to prevent fall-through
                }
        ) {
        val isWide = maxWidth >= 600.dp

        // Dynamic Backdrop Glowing Blur based on ambient song themes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .graphicsLayer(alpha = 0.45f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(coffeeBrown.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset.Unspecified
                        )
                    )
            )
        }

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Artwork & Basic Metadata
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(16.dp)
                            .graphicsLayer {
                                shadowElevation = 16f
                                shape = RoundedCornerShape(20)
                            }
                    ) {
                        CoffeeAlbumArt(
                            presetId = song.generativePreset,
                            modifier = Modifier.fillMaxSize(),
                            isPlaying = isPlaying,
                            songPath = song.path
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            color = warmCream,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = if (isPlaying) TextOverflow.Clip else TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .then(
                                    if (isPlaying) {
                                        Modifier.basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 1000
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            color = softLatte,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Column: Action controllers, Progress and Status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    // Header Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onMinimize, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize full player button", tint = warmCream, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            text = "Noc Tune Active Lounge",
                            color = warmCream,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onTriggerQueueDrawer, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                                Icon(Icons.Default.QueueMusic, contentDescription = "Toggle playing queue drawer", tint = warmCream)
                            }
                            Box {
                                var menuExpanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Show options menu", tint = warmCream)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(darkMocha)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Theme", color = warmCream) },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = coffeeBrown) },
                                        onClick = {
                                            menuExpanded = false
                                            showThemeDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sleep Timer", color = warmCream) },
                                        leadingIcon = { Icon(Icons.Default.Snooze, contentDescription = null, tint = coffeeBrown) },
                                        onClick = {
                                            menuExpanded = false
                                            onTriggerSleepTimerMenu()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Equalizer", color = warmCream) },
                                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = coffeeBrown) },
                                        onClick = {
                                            menuExpanded = false
                                            onTriggerEqualizer()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("About Us", color = warmCream) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = coffeeBrown) },
                                        onClick = {
                                            menuExpanded = false
                                            onTriggerAboutUs()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sleep Timer Badge
                    if (sleepTimerRemainingMs > 0 || stopAfterCurrentEnabled) {
                        val timerLabel = if (sleepTimerRemainingMs > 0) {
                            val rem = sleepTimerRemainingMs / 1000
                            String.format("%02d:%02d left", rem / 60, rem % 60)
                        } else {
                            "Stop after track"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(coffeeBrown.copy(alpha = 0.25f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Snooze, contentDescription = "Snooze indicators", tint = softLatte, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = timerLabel, color = softLatte, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Progress Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val sliderValue = localScrubbingProgress ?: if (song.duration > 0) progress.toFloat() / song.duration else 0f
                        Slider(
                            value = sliderValue.coerceIn(0f, 1f),
                            onValueChange = { percent ->
                                localScrubbingProgress = percent
                            },
                            onValueChangeFinished = {
                                localScrubbingProgress?.let { percent ->
                                    onSeek((percent * song.duration).toLong())
                                }
                                localScrubbingProgress = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = coffeeBrown,
                                activeTrackColor = coffeeBrown,
                                inactiveTrackColor = darkMocha
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("player_progress_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentFormatted,
                                color = secondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { showRemainingTime = !showRemainingTime }
                                    .padding(8.dp)
                            )
                            Text(
                                text = durationFormatted,
                                color = secondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { showRemainingTime = !showRemainingTime }
                                    .padding(8.dp)
                            )
                        }
                    }

                    // Primary control decks
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favorite Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onToggleFavorite() }
                                .coffeeFocusHighlight(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle favorite state on song",
                                tint = if (song.isFavorite) Color.Red else warmCream,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Media Controls clustered together in the center with zero-padding clicks
                        @OptIn(ExperimentalMaterial3Api::class)
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Previous Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { onPrevious() }
                                        .coffeeFocusHighlight(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Skip previous track button",
                                        tint = warmCream,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Play / Pause Circle Tactile button
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(coffeeBrown)
                                        .clickable { onPlayPause() }
                                        .coffeeFocusHighlight(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play icon",
                                        tint = deepEspresso,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // Next Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { onNext() }
                                        .coffeeFocusHighlight(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip next track button",
                                        tint = warmCream,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        // Repeat Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onToggleRepeat() }
                                .coffeeFocusHighlight(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            }
                            val tint = if (repeatMode != RepeatMode.OFF) coffeeBrown else warmCream
                            Icon(
                                imageVector = icon,
                                contentDescription = "Toggle Repeat Mode",
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Action controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize full player button", tint = warmCream, modifier = Modifier.size(32.dp))
                    }
                    
                    Text(
                        text = "Noc Tune Active Lounge",
                        color = warmCream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTriggerQueueDrawer, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                            Icon(Icons.Default.QueueMusic, contentDescription = "Toggle playing queue drawer", tint = warmCream)
                        }
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.minimumInteractiveComponentSize().coffeeFocusHighlight(CircleShape)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Show options menu", tint = warmCream)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(darkMocha)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Theme", color = warmCream) },
                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = coffeeBrown) },
                                    onClick = {
                                        menuExpanded = false
                                        showThemeDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sleep Timer", color = warmCream) },
                                    leadingIcon = { Icon(Icons.Default.Snooze, contentDescription = null, tint = coffeeBrown) },
                                    onClick = {
                                        menuExpanded = false
                                        onTriggerSleepTimerMenu()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Equalizer", color = warmCream) },
                                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = coffeeBrown) },
                                    onClick = {
                                        menuExpanded = false
                                        onTriggerEqualizer()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("About Us", color = warmCream) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = coffeeBrown) },
                                    onClick = {
                                        menuExpanded = false
                                        onTriggerAboutUs()
                                    }
                                )
                            }
                        }
                    }
                }

                // Central spinning Album Artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(24.dp)
                        .graphicsLayer {
                            shadowElevation = 16f
                            shape = RoundedCornerShape(20)
                        }
                ) {
                    CoffeeAlbumArt(
                        presetId = song.generativePreset,
                        modifier = Modifier.fillMaxSize(),
                        isPlaying = isPlaying,
                        songPath = song.path
                    )
                }

                // Sleep Timer countdown bubble active visualization
                if (sleepTimerRemainingMs > 0 || stopAfterCurrentEnabled) {
                    val timerLabel = if (sleepTimerRemainingMs > 0) {
                        val rem = sleepTimerRemainingMs / 1000
                        String.format("%02d:%02d left", rem / 60, rem % 60)
                    } else {
                        "Stop after track"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(coffeeBrown.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Snooze, contentDescription = "Snooze active indicator link", tint = softLatte, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = timerLabel, color = softLatte, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Metadata: Titles
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = song.title,
                        color = warmCream,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = if (isPlaying) TextOverflow.Clip else TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .then(
                                if (isPlaying) {
                                    Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 1000
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        color = softLatte,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Timeline Progress Slider layout
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderValue = localScrubbingProgress ?: if (song.duration > 0) progress.toFloat() / song.duration else 0f
                    Slider(
                        value = sliderValue.coerceIn(0f, 1f),
                        onValueChange = { percent ->
                            localScrubbingProgress = percent
                        },
                        onValueChangeFinished = {
                            localScrubbingProgress?.let { percent ->
                                onSeek((percent * song.duration).toLong())
                            }
                            localScrubbingProgress = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = coffeeBrown,
                            activeTrackColor = coffeeBrown,
                            inactiveTrackColor = darkMocha
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("player_progress_slider")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentFormatted,
                            color = secondaryText,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { showRemainingTime = !showRemainingTime }
                                .padding(8.dp)
                        )
                        Text(
                            text = durationFormatted,
                            color = secondaryText,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { showRemainingTime = !showRemainingTime }
                                .padding(8.dp)
                        )
                    }
                }

                // Primary control decks row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onToggleFavorite() }
                            .coffeeFocusHighlight(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle favorite state on song",
                            tint = if (song.isFavorite) Color.Red else warmCream,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Media Controls clustered together in the center with zero-padding clicks
                    @OptIn(ExperimentalMaterial3Api::class)
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 0.dp
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onPrevious() }
                                    .coffeeFocusHighlight(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Skip previous track button",
                                    tint = warmCream,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Play / Pause Circle Tactile button
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(coffeeBrown)
                                    .clickable { onPlayPause() }
                                    .coffeeFocusHighlight(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Primary player playback controller",
                                    tint = deepEspresso,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Next Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onNext() }
                                    .coffeeFocusHighlight(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip next track button",
                                    tint = warmCream,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // Repeat Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onToggleRepeat() }
                            .coffeeFocusHighlight(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        val tint = if (repeatMode != RepeatMode.OFF) coffeeBrown else warmCream
                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle Repeat Mode",
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
}

// ==========================================
// DRAWER: ACTIVE QUEUE OVERLAY PANEL
// ==========================================
@Composable
fun ActiveQueueDrawer(
    queue: List<SongEntity>,
    activeSongIndex: Int,
    onDismiss: () -> Unit,
    onPlaySong: (SongEntity) -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val deepEspresso = appColors.deepEspresso
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        // Drawer body (occupies right 75% or slides up from bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(darkMocha)
                .clickable(enabled = false) { } // block click dismiss
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Playing Queue", color = warmCream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Close, contentDescription = "Close queue overlay panel", tint = warmCream)
                    }
                }

                // Songs List
                if (queue.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = "Sequence queue is empty.", color = secondaryText, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(queue) { song ->
                            val isActive = queue.indexOf(song) == activeSongIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) coffeeBrown.copy(alpha = 0.25f) else deepEspresso.copy(alpha = 0.5f))
                                    .clickable { onPlaySong(song) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoffeeAlbumArt(
                                    presetId = song.generativePreset,
                                    modifier = Modifier.size(40.dp),
                                    songPath = song.path
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = song.title,
                                        color = if (isActive) coffeeBrown else warmCream,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(text = song.artist, color = secondaryText, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Modifier.coffeeFocusHighlight(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    borderColor: Color = Color(0xFFDDB892),
    borderWidth: androidx.compose.ui.unit.Dp = 2.dp
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    onFocusChanged { isFocused = it.isFocused }
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = if (isFocused) borderColor else Color.Transparent,
            shape = shape
        )
}

@Composable
fun EqualizerAnimation(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFB08968)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Equalizer")
    
    val b1HeightAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "Bar1"
    )
    
    val b2HeightAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "Bar2"
    )
    
    val b3HeightAnim by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutLinearInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    val b1Height = if (isPlaying) b1HeightAnim else 0.3f
    val b2Height = if (isPlaying) b2HeightAnim else 0.5f
    val b3Height = if (isPlaying) b3HeightAnim else 0.2f

    Row(
        modifier = modifier.size(20.dp, 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barWidth = 3.dp
        Box(
            modifier = Modifier
                .size(barWidth, 14.dp * b1Height)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .size(barWidth, 14.dp * b2Height)
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .size(barWidth, 14.dp * b3Height)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun SettingsMoreMenu(
    onTriggerTheme: () -> Unit,
    onTriggerSleepTimer: () -> Unit,
    onTriggerEqualizer: () -> Unit,
    onTriggerAboutUs: () -> Unit,
    tint: Color = com.example.ui.theme.LocalAppColors.current.warmCream
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream

    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(48.dp).coffeeFocusHighlight(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Show options menu",
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(darkMocha)
        ) {
            DropdownMenuItem(
                text = { Text("Theme", color = warmCream) },
                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = coffeeBrown) },
                onClick = {
                    menuExpanded = false
                    onTriggerTheme()
                }
            )
            DropdownMenuItem(
                text = { Text("Sleep Timer", color = warmCream) },
                leadingIcon = { Icon(Icons.Default.Snooze, contentDescription = null, tint = coffeeBrown) },
                onClick = {
                    menuExpanded = false
                    onTriggerSleepTimer()
                }
            )
            DropdownMenuItem(
                text = { Text("Equalizer", color = warmCream) },
                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = coffeeBrown) },
                onClick = {
                    menuExpanded = false
                    onTriggerEqualizer()
                }
            )
            DropdownMenuItem(
                text = { Text("About Us", color = warmCream) },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = coffeeBrown) },
                onClick = {
                    menuExpanded = false
                    onTriggerAboutUs()
                }
            )
        }
    }
}

@Composable
fun ThemeColorPickerDialog(
    currentThemeColorHex: String?,
    themeBrightness: Float,
    onBrightnessChanged: (Float) -> Unit,
    onColorSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val presetColors = listOf(
        "#1F4E5B", "#7E8B90", "#FF1616", "#FF3E35", "#B01D21",
        "#FF5722", "#FF7043", "#FF9800", "#FFB300", "#FFD54F",
        "#FF8A65", "#FFA726", "#FFC107", "#FFEE58", "#FFEB3B",
        "#4CAF50", "#8BC34A", "#CDDC39", "#9CCC65", "#E91E63",
        "#388E3C", "#2E7D32", "#81C784", "#558B2F", "#1B5E20",
        "#4682B4", "#2196F3", "#00BCD4", "#009688", "#008080",
        "#3B5998", "#5D3FD3", "#7F00FF", "#8A2BE2", "#9932CC",
        "#8D6E63", "#EC407A", "#D81B60", "#4A148C", "#8236EB",
        "#F4A460", "#6B8E23", "#556B2F", "#800000", "#1C1C1C"
    )

    val targetBlack = Color(0xFF020105)
    val targetWhite = Color(0xFFFAFAFC)

    var baseColor = Color(0xFF151030)
    if (currentThemeColorHex != null) {
        try {
            baseColor = Color(android.graphics.Color.parseColor(currentThemeColorHex))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isBaseDark = com.example.ui.theme.isColorDark(baseColor)
    val finalBgColor = if (isBaseDark) {
        com.example.ui.theme.blendColor(baseColor, targetBlack, 1f - themeBrightness)
    } else {
        com.example.ui.theme.blendColor(baseColor, targetWhite, 1f - themeBrightness)
    }

    val isBgDark = com.example.ui.theme.isColorDark(finalBgColor)

    val surfaceColor = if (isBgDark) {
        com.example.ui.theme.blendColor(finalBgColor, Color.White, 0.08f * themeBrightness + 0.02f)
    } else {
        com.example.ui.theme.blendColor(finalBgColor, Color.Black, 0.06f * themeBrightness + 0.02f)
    }

    val textColor = if (isBgDark) {
        com.example.ui.theme.blendColor(Color(0xFFE8E5EE), Color(0xFF807A8A), 1f - themeBrightness)
    } else {
        com.example.ui.theme.blendColor(Color(0xFF140D2B), Color(0xFF7E7A8A), 1f - themeBrightness)
    }

    val primaryColor = if (isBgDark) {
        com.example.ui.theme.blendColor(Color(0xFF6B4EE0), targetBlack, 0.45f * (1f - themeBrightness))
    } else {
        com.example.ui.theme.blendColor(Color(0xFF6B4EE0), targetWhite, 0.4f * (1f - themeBrightness))
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}, // prevent click-through dismissal
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select a Colour",
                            color = textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Close dialog", tint = textColor)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 5 Columns grid of circles
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                    ) {
                        items(presetColors) { colorHex ->
                            val parsedColor = remember(colorHex) {
                                Color(android.graphics.Color.parseColor(colorHex))
                            }
                            val isSelected = currentThemeColorHex?.equals(colorHex, ignoreCase = true) == true
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .clickable {
                                        onColorSelected(colorHex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    val checkTint = if (com.example.ui.theme.isColorDark(parsedColor)) Color.White else Color.Black
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = checkTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Brightness Icon",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Theme Brightness: ${(themeBrightness * 100).toInt()}%",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Slider(
                        value = themeBrightness,
                        onValueChange = onBrightnessChanged,
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = primaryColor.copy(alpha = 0.24f),
                            thumbColor = primaryColor
                        ),
                        enabled = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    
                    // Reset button
                    Button(
                        onClick = {
                            onColorSelected(null)
                            onBrightnessChanged(0.6f)
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Reset to Default Theme", color = if (com.example.ui.theme.isColorDark(primaryColor)) Color.White else Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutUsDialog(
    onDismissRequest: () -> Unit
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val darkMocha = appColors.darkMocha
    val coffeeBrown = appColors.coffeeBrown
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText
    
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = darkMocha,
            border = androidx.compose.foundation.BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.5f)),
            tonalElevation = 8.dp,
            modifier = androidx.compose.ui.Modifier
                .padding(24.dp)
                .widthIn(max = 480.dp)
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    // Header Icon & Title
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(coffeeBrown.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = warmCream,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Lost in Classics",
                                color = warmCream,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Noc Tune Audio Experience • v2.1.1",
                                color = secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = coffeeBrown.copy(alpha = 0.3f), thickness = 1.dp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description text
                    Text(
                        text = "A beautifully designed, fluid, and robust local music player crafted for absolute comfort and pristine acoustic enjoyment. Built for the community, free from any compromises.",
                        color = warmCream.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Key Feature Pillars
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AboutUsFeatureRow(
                            icon = Icons.Default.CheckCircle,
                            title = "Ad-Free & Lightweight",
                            description = "No annoying popups, tracking, or background overhead. Purely focused on your offline music files."
                        )
                        AboutUsFeatureRow(
                            icon = Icons.Default.Security,
                            title = "100% Safe & Secure",
                            description = "Built on clean open-source standards. Zero malware, zero viruses, and no privacy-intrusive trackers."
                        )
                        AboutUsFeatureRow(
                            icon = Icons.Default.Code,
                            title = "Open Source & Free",
                            description = "Completely open to everyone. Anyone is free to use, audit, and distribute this application indefinitely."
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Developer Connection Section
                    Text(
                        text = "DEVELOPER CONTACT",
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = coffeeBrown.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, coffeeBrown.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    uriHandler.openUri("https://t.me/msujoy149")
                                } catch (e: Exception) {
                                    // Fallback or ignore
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Telegram icon",
                                    tint = warmCream,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "@msujoy149",
                                        color = warmCream,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to chat on Telegram",
                                        color = secondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Go arrow",
                                tint = secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Close Action
                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = coffeeBrown
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Dismiss",
                            color = warmCream,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

@Composable
fun AboutUsFeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val warmCream = appColors.warmCream
    val secondaryText = appColors.secondaryText

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = warmCream.copy(alpha = 0.8f),
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                color = warmCream,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = secondaryText.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

