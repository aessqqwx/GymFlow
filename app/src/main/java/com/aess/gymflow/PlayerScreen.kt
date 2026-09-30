package com.aess.gymflow

import androidx.compose.foundation.verticalScroll

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animate
import kotlinx.coroutines.Job
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

@Composable
fun rememberMusicController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, musicServiceComponent(context))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { future.get() }.onSuccess { controller = it } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

data class PlaybackUiState(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
    val currentIndex: Int = C.INDEX_UNSET,
    val artworkUri: String = ""
)

@Composable
fun rememberPlaybackUiState(controller: MediaController?): PlaybackUiState {
    var state by remember(controller) { mutableStateOf(PlaybackUiState()) }

    fun snapshot(): PlaybackUiState {
        val p = controller ?: return PlaybackUiState()
        val item = p.currentMediaItem
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        return PlaybackUiState(
            mediaId = item?.mediaId.orEmpty(),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queueSize = p.mediaItemCount,
            currentIndex = p.currentMediaItemIndex,
            artworkUri = item?.mediaMetadata?.artworkUri?.toString().orEmpty()
        )
    }

    DisposableEffect(controller) {
        val p = controller
        if (p == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { state = snapshot() }
        }
        p.addListener(listener)
        state = snapshot()
        onDispose { p.removeListener(listener) }
    }

    LaunchedEffect(controller, state.mediaId, state.isPlaying) {
        while (controller != null) {
            state = snapshot()
            delay(if (state.isPlaying) 500L else 1_000L)
        }
    }
    return state
}

/**
 * Tracks whether the user has swiped away the mini player. Shared across every
 * screen that hosts [MiniPlayerBar] so a dismissal in one place sticks everywhere,
 * while playback itself keeps running via [MusicService] regardless.
 */
object MiniPlayerVisibility {
    var dismissed by mutableStateOf(false)
    fun restore() { dismissed = false }
}

@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null
) {
    val controller = rememberMusicController()
    val state = rememberPlaybackUiState(controller)
    val language = LocalAppLanguage.current
    if (controller == null || state.mediaId.isBlank()) return

    // Reset the dismissed flag whenever a *new* track starts, so the bar
    // reappears naturally for the next song instead of staying hidden forever.
    LaunchedEffect(state.mediaId) { MiniPlayerVisibility.dismissed = false }

    if (MiniPlayerVisibility.dismissed) {
        RestoreMiniPlayerChip(modifier = modifier, onRestore = { MiniPlayerVisibility.restore() })
        return
    }

    val scope = rememberCoroutineScope()
    var dragX by remember(state.mediaId) { mutableFloatStateOf(0f) }
    var dragY by remember(state.mediaId) { mutableFloatStateOf(0f) }
    var motionJob by remember(state.mediaId) { mutableStateOf<Job?>(null) }
    var dismissing by remember(state.mediaId) { mutableStateOf(false) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    DisposableEffect(state.mediaId) { onDispose { motionJob?.cancel() } }

    fun settle(x: Float, y: Float, dismiss: Boolean = false) {
        motionJob?.cancel()
        dismissing = dismiss
        motionJob = scope.launch {
            val spec = if (dismiss) GymGlowMotion.defaultEffects<Float>() else GymGlowMotion.defaultSpatial<Float>()
            val horizontal = launch { animate(dragX, x, animationSpec = spec) { value, _ -> dragX = value } }
            val vertical = launch { animate(dragY, y, animationSpec = spec) { value, _ -> dragY = value.coerceAtLeast(0f) } }
            horizontal.join()
            vertical.join()
            if (dismiss) MiniPlayerVisibility.dismissed = true
            dismissing = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { barWidthPx = it.size.width.toFloat(); barHeightPx = it.size.height.toFloat() }
            .graphicsLayer {
                translationX = dragX
                translationY = dragY
                val travel = max(barWidthPx, 1f)
                val verticalTravel = max(barHeightPx, 1f)
                alpha = 1f - max(abs(dragX) / travel, dragY / (verticalTravel * 2.5f)).coerceIn(0f, 1f)
            }
            .pointerInput(barWidthPx, barHeightPx, state.mediaId) {
                detectDragGestures(
                    onDragStart = { if (!dismissing) motionJob?.cancel() },
                    onDrag = { change, amount ->
                        change.consume()
                        if (!dismissing) {
                            dragX += amount.x
                            dragY = (dragY + amount.y).coerceAtLeast(0f)
                        }
                    },
                    onDragCancel = { if (!dismissing) settle(0f, 0f) },
                    onDragEnd = {
                        if (!dismissing) {
                            val horizontal = abs(dragX) / max(barWidthPx * .35f, 1f)
                            val vertical = dragY / max(barHeightPx * 1.1f, 1f)
                            if (vertical >= 1f && vertical >= horizontal) {
                                // Vertical dismissal settles horizontal drift rather than throwing diagonally.
                                settle(0f, barHeightPx * 2.5f, dismiss = true)
                            } else if (horizontal >= 1f) {
                                settle(if (dragX > 0f) barWidthPx * 1.4f else -barWidthPx * 1.4f, 0f, dismiss = true)
                            } else settle(0f, 0f)
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(
                MusicTrack(uri = state.mediaId, title = state.title, artist = state.artist, durationMs = state.durationMs, artworkUri = state.artworkUri),
                Modifier.size(48.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).then(if (onOpen != null) Modifier else Modifier),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    state.title.ifBlank { gs(language, R.string.audio_file) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.artist.ifBlank { gs(language, R.string.unknown_artist) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .72f),
                    fontSize = 12.sp
                )
            }
            if (onOpen != null) IconButton(onClick = onOpen) { Icon(Icons.Rounded.ExpandLess, gs(language, R.string.open_player)) }
            FilledIconButton(onClick = { if (state.isPlaying) controller.pause() else controller.play() }) {
                PlaybackMotionIcon(state.isPlaying, language)
            }
        }
    }
}

/** Small pill shown after the mini player is swiped away, so playback stays reachable. */
@Composable
private fun RestoreMiniPlayerChip(modifier: Modifier = Modifier, onRestore: () -> Unit) {
    val language = LocalAppLanguage.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            onClick = onRestore,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 3.dp
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(gs(language, R.string.open_player), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun NowPlayingSheet(onDismiss: () -> Unit) {
    val controller = rememberMusicController()
    val state = rememberPlaybackUiState(controller)
    var queueMode by remember { mutableStateOf(false) }
    if (controller == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BackHandler(onBack = if (queueMode) { { queueMode = false } } else onDismiss)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // Top bar: collapse (back) button, no other clutter.
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = if (queueMode) { { queueMode = false } } else onDismiss) {
                            Icon(
                                if (queueMode) Icons.Rounded.ArrowBack else Icons.Rounded.ExpandMore,
                                gs(LocalAppLanguage.current, R.string.back)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (!queueMode) {
                            Text(
                                gs(LocalAppLanguage.current, R.string.music_df4392a),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.size(48.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        AnimatedContent(queueMode, transitionSpec = { directionalMotion(targetState, GymGlowMotion.TabTravelDivisor) }, label = "player_queue") { queue ->
                            if (queue) QueueContent(controller = controller)
                            else NowPlayingContent(controller = controller, state = state, onQueue = { queueMode = true })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingContent(controller: MediaController, state: PlaybackUiState, onQueue: () -> Unit) {
    val language = LocalAppLanguage.current
    var scrub by remember(state.mediaId) { mutableFloatStateOf(0f) }
    var scrubbing by remember(state.mediaId) { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1L)
    LaunchedEffect(state.positionMs, state.mediaId, scrubbing) {
        if (!scrubbing) scrub = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.mediaId.isNotBlank()) {
            TrackArtwork(
                MusicTrack(uri = state.mediaId, title = state.title, artist = state.artist, durationMs = state.durationMs, artworkUri = state.artworkUri),
                Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            Surface(shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        AnimatedContent(state.title to state.artist, transitionSpec = { effectsMotion() }, label = "track_metadata") { (title, artist) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title.ifBlank { gs(language, R.string.choose_a_track) }, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(artist.ifBlank { gs(language, R.string.unknown_artist) }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(20.dp))
        Slider(
            value = scrub,
            onValueChange = { scrubbing = true; scrub = it },
            onValueChangeFinished = {
                controller.seekTo((duration * scrub).toLong())
                scrubbing = false
            },
            valueRange = 0f..1f
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(if (scrubbing) (duration * scrub).toLong() else state.positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(state.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller.shuffleModeEnabled = !controller.shuffleModeEnabled }) {
                Icon(Icons.Rounded.Shuffle, gs(language, R.string.shuffle), tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { controller.seekToPreviousMediaItem() }) { Icon(Icons.Rounded.SkipPrevious, gs(language, R.string.previous), Modifier.size(34.dp)) }
            FilledIconButton(
                onClick = { if (state.isPlaying) controller.pause() else controller.play() },
                modifier = Modifier.size(76.dp)
            ) { PlaybackMotionIcon(state.isPlaying, language, 40.dp) }
            IconButton(onClick = { controller.seekToNextMediaItem() }) { Icon(Icons.Rounded.SkipNext, gs(language, R.string.next_c59db3d), Modifier.size(34.dp)) }
            IconButton(onClick = {
                controller.repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    gs(language, R.string.repeat),
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        ExpressiveSurfaceButton(onClick = onQueue, modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QueueMusic, null)
                Spacer(Modifier.width(8.dp))
                Text(gs(language, R.string.queue_count, state.queueSize), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QueueContent(controller: MediaController) {
    val language = LocalAppLanguage.current
    val state = rememberPlaybackUiState(controller)
    var revision by remember { mutableIntStateOf(0) }
    val items = remember(state.queueSize, state.currentIndex, revision) {
        buildList { for (i in 0 until controller.mediaItemCount) add(i to controller.getMediaItemAt(i)) }
    }
    val uniqueIds = remember(items) { items.map { it.second.mediaId }.distinct().size == items.size }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(gs(language, R.string.queue), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { controller.clearMediaItems(); revision++ }) { Text(gs(language, R.string.clear)) }
            }
        }
        if (items.isEmpty()) {
            item {
                CompactMusicEmpty(
                    title = gs(language, R.string.queue_is_empty),
                    text = gs(language, R.string.add_tracks_from_the_music_library)
                )
            }
        } else {
            items(items, key = { (index, item) -> if (uniqueIds) "track:${item.mediaId}" else "position:$index" }) { (index, item) ->
                ListItem(
                    headlineContent = { Text(item.mediaMetadata.title?.toString().orEmpty().ifBlank { gs(language, R.string.audio_file) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(item.mediaMetadata.artist?.toString().orEmpty().ifBlank { gs(language, R.string.unknown_artist) }, maxLines = 1) },
                    leadingContent = {
                        if (index == state.currentIndex) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        else Text("${index + 1}")
                    },
                    trailingContent = {
                        Row {
                            IconButton(enabled = index > 0, onClick = { controller.moveMediaItem(index, index - 1); revision++ }) { Icon(Icons.Rounded.KeyboardArrowUp, gs(language, R.string.move_up)) }
                            IconButton(enabled = index < items.lastIndex, onClick = { controller.moveMediaItem(index, index + 1); revision++ }) { Icon(Icons.Rounded.KeyboardArrowDown, gs(language, R.string.move_down)) }
                            IconButton(onClick = { controller.removeMediaItem(index); revision++ }) { Icon(Icons.Rounded.Close, gs(language, R.string.remove)) }
                        }
                    },
                    modifier = (if (uniqueIds) motionItemModifier() else Modifier).fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val store = remember { GymFlowStore(context) }
    val language = LocalAppLanguage.current
    val controller = rememberMusicController()
    val playback = rememberPlaybackUiState(controller)
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }
    LaunchedEffect(store) {
        val loaded = withContext(Dispatchers.IO) { store.loadMusicLibrary() to store.loadPlaylists() }
        tracks = (loaded.first + tracks).distinctBy { it.uri }.sortedByDescending { it.addedAt }
        playlists = (loaded.second + playlists).distinctBy { it.id }.sortedBy { it.createdAt }
    }
    fun persistTracks(snapshot: List<MusicTrack>) { scope.launch(Dispatchers.IO) { store.saveMusicLibrary(snapshot) } }
    fun persistPlaylists(snapshot: List<MusicPlaylist>) { scope.launch(Dispatchers.IO) { store.savePlaylists(snapshot) } }
    var section by remember { mutableStateOf("RECENT") }
    var nowPlaying by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var infoTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var lostAccessTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedPlaylist by remember { mutableStateOf<MusicPlaylist?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    runCatching { readTrackMetadata(context, uri) }.getOrNull()
                }
            }
            tracks = (imported + tracks).distinctBy { it.uri }.sortedByDescending { it.addedAt }
            persistTracks(tracks)
        }
    }

    if (nowPlaying) NowPlayingSheet { nowPlaying = false }
    menuTrack?.let { track ->
        ModalBottomSheet(onDismissRequest = { menuTrack = null }) {
            TrackMenu(
                track = track,
                controller = controller,
                onPlayNext = {
                    controller?.let { p -> p.addMediaItem((p.currentMediaItemIndex + 1).coerceAtLeast(0), track.asMediaItem()) }
                    menuTrack = null
                },
                onAddQueue = { controller?.addMediaItem(track.asMediaItem()); menuTrack = null },
                onAddPlaylist = { playlistTrack = track; menuTrack = null },
                onInfo = { infoTrack = track },
                onClose = { menuTrack = null }
            )
        }
    }

    lostAccessTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { lostAccessTrack = null },
            title = { Text(gs(language, R.string.file_access_required)) },
            text = { Text(gs(language, R.string.file_access_lost, track.title)) },
            confirmButton = {
                TextButton(onClick = { lostAccessTrack = null; picker.launch(arrayOf("audio/*")) }) { Text(gs(language, R.string.choose_file)) }
            },
            dismissButton = { TextButton(onClick = { lostAccessTrack = null }) { Text(gs(language, R.string.cancel)) } }
        )
    }
    infoTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { infoTrack = null },
            title = { Text(track.title.ifBlank { gs(language, R.string.audio_file) }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(gs(language, R.string.artist_info, track.artist.ifBlank { gs(language, R.string.unknown_artist) }))
                    if (track.album.isNotBlank()) Text(gs(language, R.string.album_info, track.album))
                    Text(gs(language, R.string.duration_info, formatDuration(track.durationMs)))
                    Text(track.uri, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = { TextButton(onClick = { infoTrack = null }) { Text(gs(language, R.string.done)) } }
        )
    }
    playlistTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { playlistTrack = null },
            title = { Text(gs(language, R.string.add_to_playlist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (playlists.isEmpty()) Text(gs(language, R.string.create_a_playlist_first))
                    playlists.forEach { playlist ->
                        TextButton(onClick = {
                            playlists = playlists.map { if (it.id == playlist.id) it.copy(trackUris = (it.trackUris + track.uri).distinct()) else it }
                            persistPlaylists(playlists)
                            playlistTrack = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { playlistTrack = null; showCreatePlaylist = true }) { Text(gs(language, R.string.new_playlist)) } },
            dismissButton = { TextButton(onClick = { playlistTrack = null }) { Text(gs(language, R.string.cancel)) } }
        )
    }
    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = gs(language, R.string.new_playlist),
            initial = "",
            onDismiss = { showCreatePlaylist = false },
            onSave = { name ->
                val p = MusicPlaylist(System.currentTimeMillis(), name.trim())
                playlists = playlists + p
                persistPlaylists(playlists)
                showCreatePlaylist = false
            }
        )
    }
    selectedPlaylist?.let { playlist ->
        PlaylistSheet(
            playlist = playlist,
            library = tracks,
            controller = controller,
            onDismiss = { selectedPlaylist = null },
            onChanged = { changed ->
                playlists = playlists.map { if (it.id == changed.id) changed else it }
                persistPlaylists(playlists)
                selectedPlaylist = changed
            },
            onAccessLost = { lostAccessTrack = it },
            onDelete = {
                playlists = playlists.filterNot { it.id == playlist.id }
                persistPlaylists(playlists)
                selectedPlaylist = null
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(gs(language, R.string.music_df4392a), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(gs(language, R.string.your_local_library), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Icon(Icons.Rounded.LibraryMusic, gs(language, R.string.import_music)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "RECENT" to gs(language, R.string.recently_added),
                    "TRACKS" to gs(language, R.string.tracks),
                    "ALBUMS" to gs(language, R.string.albums),
                    "ARTISTS" to gs(language, R.string.artists),
                    "PLAYLISTS" to gs(language, R.string.playlists)
                ).forEach { (key, label) ->
                    FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label) })
                }
            }
        }

        when (section) {
            "PLAYLISTS" -> {
                item {
                    ExpressiveSurfaceButton(onClick = { showCreatePlaylist = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text(gs(language, R.string.create_playlist), fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (playlists.isEmpty()) item {
                    CompactMusicEmpty(gs(language, R.string.no_playlists_yet), gs(language, R.string.create_a_playlist_and_add_your_favorite_trac))
                }
                items(playlists, key = { it.id }) { playlist ->
                    ExpressiveSurfaceButton(onClick = { selectedPlaylist = playlist }, modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.QueueMusic, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(playlist.name, fontWeight = FontWeight.Bold); Text(gs(language, R.string.tracks_count, playlist.trackUris.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
            }
            "ALBUMS" -> {
                val albums = tracks.groupBy { it.album.ifBlank { gs(language, R.string.unknown_album) } }
                if (albums.isEmpty()) item { CompactMusicEmpty(gs(language, R.string.no_albums_yet), gs(language, R.string.import_music_to_see_albums)) }
                albums.forEach { (album, list) -> item(key = "album:$album") {
                    LibraryGroupCard(album, gs(language, R.string.tracks_count, list.size), Icons.Rounded.Album) {
                        scope.launch { if (!playQueue(context, controller, list, list.firstOrNull()?.uri)) lostAccessTrack = list.firstOrNull() }
                    }
                } }
            }
            "ARTISTS" -> {
                val artists = tracks.groupBy { it.artist.ifBlank { gs(language, R.string.unknown_artist) } }
                if (artists.isEmpty()) item { CompactMusicEmpty(gs(language, R.string.no_artists_yet), gs(language, R.string.import_music_to_see_artists)) }
                artists.forEach { (artist, list) -> item(key = "artist:$artist") {
                    LibraryGroupCard(artist, gs(language, R.string.tracks_count, list.size), Icons.Rounded.Person) {
                        scope.launch { if (!playQueue(context, controller, list, list.firstOrNull()?.uri)) lostAccessTrack = list.firstOrNull() }
                    }
                } }
            }
            else -> {
                val visible = if (section == "RECENT") tracks.take(12) else tracks
                if (visible.isEmpty()) item {
                    CompactMusicEmpty(
                        gs(language, R.string.no_music_yet),
                        gs(language, R.string.import_local_audio_files_gymflow_will_not_cr),
                        action = gs(language, R.string.import_music),
                        onAction = { picker.launch(arrayOf("audio/*")) }
                    )
                }
                items(visible, key = { it.uri }) { track ->
                    TrackCard(
                        track = track,
                        onPlay = { scope.launch { if (!playQueue(context, controller, tracks, track.uri)) lostAccessTrack = track } },
                        onMenu = { menuTrack = track }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(track: MusicTrack, onPlay: () -> Unit, onMenu: () -> Unit) {
    val language = LocalAppLanguage.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArtwork(track, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist.ifBlank { gs(language, R.string.unknown_artist) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(track.durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, gs(language, R.string.play)) }
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, gs(language, R.string.menu)) }
        }
    }
}

@Composable
private fun TrackArtwork(track: MusicTrack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(track.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track.uri) {
        bitmap = withContext(Dispatchers.IO) { readArtwork(context, track) }
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null) }
    }
}

@Composable
private fun TrackMenu(
    track: MusicTrack,
    controller: MediaController?,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onAddPlaylist: () -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit
) {
    val language = LocalAppLanguage.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(track.artist.ifBlank { gs(language, R.string.unknown_artist) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        MenuLine(Icons.Rounded.PlaylistPlay, gs(language, R.string.play_next), onPlayNext)
        MenuLine(Icons.Rounded.QueueMusic, gs(language, R.string.add_to_queue), onAddQueue)
        MenuLine(Icons.Rounded.PlaylistAdd, gs(language, R.string.add_to_playlist), onAddPlaylist)
        MenuLine(Icons.Rounded.Info, gs(language, R.string.information)) { onInfo(); onClose() }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MenuLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(text, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    playlist: MusicPlaylist,
    library: List<MusicTrack>,
    controller: MediaController?,
    onDismiss: () -> Unit,
    onChanged: (MusicPlaylist) -> Unit,
    onAccessLost: (MusicTrack) -> Unit,
    onDelete: () -> Unit
) {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf(false) }
    val tracks = playlist.trackUris.mapNotNull { uri -> library.firstOrNull { it.uri == uri } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(playlist.name, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(gs(language, R.string.tracks_count, tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { rename = true }) { Icon(Icons.Rounded.Edit, gs(language, R.string.edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, gs(language, R.string.delete)) }
            }
            Spacer(Modifier.height(10.dp))
            ExpressiveSurfaceButton(onClick = {
                if (tracks.isNotEmpty()) scope.launch { if (!playQueue(context, controller, tracks, tracks.first().uri)) onAccessLost(tracks.first()) }
            }, modifier = Modifier.fillMaxWidth()) { Text(gs(language, R.string.play), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            if (tracks.isEmpty()) CompactMusicEmpty(gs(language, R.string.playlist_is_empty), gs(language, R.string.add_tracks_from_a_track_menu))
            tracks.forEach { track ->
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(track.artist.ifBlank { gs(language, R.string.unknown_artist) }) },
                    trailingContent = { IconButton(onClick = { onChanged(playlist.copy(trackUris = playlist.trackUris.filterNot { it == track.uri })) }) { Icon(Icons.Rounded.Close, gs(language, R.string.remove)) } }
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
    if (rename) PlaylistNameDialog(
        title = gs(language, R.string.rename_playlist),
        initial = playlist.name,
        onDismiss = { rename = false },
        onSave = { onChanged(playlist.copy(name = it.trim())); rename = false }
    )
}

@Composable
private fun PlaylistNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val language = LocalAppLanguage.current
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it.take(60) }, label = { Text(gs(language, R.string.name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text(gs(language, R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(gs(language, R.string.cancel)) } }
    )
}

@Composable
private fun LibraryGroupCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onPlay: () -> Unit) {
    ExpressiveCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Icon(icon, null) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, gs(LocalAppLanguage.current, R.string.play)) }
        }
    }
}

@Composable
private fun CompactMusicEmpty(title: String, text: String, action: String = "", onAction: () -> Unit = {}) {
    ExpressiveCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicOff, null) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                if (action.isNotBlank()) TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action) }
            }
        }
    }
}

private suspend fun playQueue(context: Context, controller: MediaController?, tracks: List<MusicTrack>, startUri: String?): Boolean {
    val p = controller ?: return false
    if (tracks.isEmpty()) return false
    val requested = tracks.firstOrNull { it.uri == startUri } ?: tracks.first()
    val readable = withContext(Dispatchers.IO) { tracks.filter { canReadTrack(context, it) } }
    if (readable.none { it.uri == requested.uri }) return false
    val index = readable.indexOfFirst { it.uri == requested.uri }.coerceAtLeast(0)
    p.setMediaItems(readable.map(MusicTrack::asMediaItem), index, 0L)
    p.prepare()
    p.play()
    return true
}

private fun canReadTrack(context: Context, track: MusicTrack): Boolean = runCatching {
    context.contentResolver.openAssetFileDescriptor(Uri.parse(track.uri), "r")?.use { true } ?: false
}.getOrDefault(false)

private suspend fun readTrackMetadata(context: Context, uri: Uri): MusicTrack = withContext(Dispatchers.IO) {
    val fallbackName = queryDisplayName(context, uri).substringBeforeLast('.').trim()
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val artworkUri = retriever.embeddedPicture?.let { cacheArtwork(context, uri, it) }.orEmpty()
        MusicTrack(
            uri = uri.toString(),
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: fallbackName,
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            addedAt = System.currentTimeMillis(),
            artworkUri = artworkUri
        )
    } finally {
        runCatching { retriever.release() }
    }
}

internal fun cacheArtwork(context: Context, source: Uri, bytes: ByteArray): String = runCatching {
    val dir = File(context.filesDir, "music_art").apply { mkdirs() }
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toString().toByteArray())
        .joinToString("") { "%02x".format(it) }
    val file = File(dir, "$digest.img")
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    Uri.fromFile(file).toString()
}.getOrDefault("")


internal fun ensureTrackArtwork(context: Context, track: MusicTrack): MusicTrack {
    val cachedReadable = track.artworkUri.takeIf(String::isNotBlank)?.let { raw ->
        runCatching {
            val uri = Uri.parse(raw)
            if (uri.scheme == "file") File(requireNotNull(uri.path)).takeIf { it.isFile && it.length() > 0L } != null
            else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    } == true
    if (cachedReadable) return track

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(track.uri))
        val bytes = retriever.embeddedPicture ?: return track.copy(artworkUri = "")
        track.copy(artworkUri = cacheArtwork(context, Uri.parse(track.uri), bytes))
    } catch (_: Throwable) {
        track.copy(artworkUri = "")
    } finally {
        runCatching { retriever.release() }
    }
}

private fun readArtwork(context: Context, track: MusicTrack): Bitmap? {
    track.artworkUri.takeIf(String::isNotBlank)?.let { cached ->
        val uri = runCatching { Uri.parse(cached) }.getOrNull()
        val bitmap = runCatching {
            if (uri?.scheme == "file") BitmapFactory.decodeFile(uri.path)
            else uri?.let { context.contentResolver.openInputStream(it)?.use { input -> BitmapFactory.decodeStream(input) } }
        }.getOrNull()
        if (bitmap != null) return bitmap
    }
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(track.uri))
        retriever.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    } catch (_: Throwable) { null }
    finally { runCatching { retriever.release() } }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }.orEmpty()
    }.getOrDefault(uri.lastPathSegment.orEmpty())
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
private fun PlaybackMotionIcon(playing: Boolean, language: String, size: Dp = 24.dp) {
    AnimatedContent(playing, transitionSpec = { effectsMotion() }, label = "play_pause") { active ->
        Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            gs(language, if (active) R.string.pause else R.string.play), Modifier.size(size))
    }
}
