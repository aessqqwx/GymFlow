package com.aess.gymflow

import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MusicTrack.asMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            artworkUri.takeIf { it.isNotBlank() }?.let { runCatching { setArtworkUri(Uri.parse(it)) } }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

class MusicService : MediaSessionService() {
    companion object {
        @Volatile private var suppressPersistForReset = false
        fun suppressPersistenceForStateReplacement() { suppressPersistForReset = true }
        fun suppressPersistenceForReset() = suppressPersistenceForStateReplacement()
    }

    private data class RestoredState(
        val prefs: PlayerPreferences,
        val tracks: List<MusicTrack>
    )

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private lateinit var store: GymFlowStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val persistListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
            ) persistPlayerState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // If reset was requested while the service was not running, consume the stale guard now.
        suppressPersistForReset = false
        store = GymFlowStore(this)
        player = ExoPlayer.Builder(this).build().apply { addListener(persistListener) }
        session = MediaSession.Builder(this, player).build()
        restorePlayerStateAsync()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun restorePlayerStateAsync() {
        serviceScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val prefs = store.loadPlayerPreferences()
                val originalLibrary = store.loadMusicLibrary()
                val library = originalLibrary.associateBy { it.uri }
                val tracks = prefs.queueUris.mapNotNull(library::get).filter(::canRead).map { ensureTrackArtwork(this@MusicService, it) }
                if (tracks.any { refreshed -> originalLibrary.firstOrNull { it.uri == refreshed.uri }?.artworkUri != refreshed.artworkUri }) {
                    val refreshedByUri = tracks.associateBy { it.uri }
                    store.saveMusicLibrary(originalLibrary.map { refreshedByUri[it.uri] ?: it })
                }
                RestoredState(prefs, tracks)
            }
            if (!::player.isInitialized) return@launch
            val queue = restored.tracks.map(MusicTrack::asMediaItem)
            if (queue.isNotEmpty()) {
                val index = restored.prefs.currentUri.takeIf(String::isNotBlank)?.let { uri ->
                    restored.tracks.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }
                } ?: 0
                player.setMediaItems(queue, index.coerceIn(0, queue.lastIndex), restored.prefs.positionMs.coerceAtLeast(0L))
                player.prepare()
            }
            player.shuffleModeEnabled = restored.prefs.shuffleEnabled
            player.repeatMode = restored.prefs.repeatMode.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
        }
    }

    private fun canRead(track: MusicTrack): Boolean = runCatching {
        contentResolver.openAssetFileDescriptor(Uri.parse(track.uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun persistPlayerState() {
        if (suppressPersistForReset || !::player.isInitialized) return
        val queue = buildList {
            for (i in 0 until player.mediaItemCount) add(player.getMediaItemAt(i).mediaId)
        }.filter(String::isNotBlank)
        // This preference object is intentionally tiny; heavy library parsing and URI checks are kept off Main.
        store.savePlayerPreferences(
            PlayerPreferences(
                queueUris = queue,
                currentUri = player.currentMediaItem?.mediaId.orEmpty(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        )
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        persistPlayerState()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistPlayerState()
        serviceScope.cancel()
        session?.release()
        session = null
        player.removeListener(persistListener)
        player.release()
        super.onDestroy()
    }
}

internal fun musicServiceComponent(context: android.content.Context) = ComponentName(context, MusicService::class.java)
