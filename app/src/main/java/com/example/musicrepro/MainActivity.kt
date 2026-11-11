package com.example.musicrepro

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicrepro.data.LocalAudioRepository
import com.example.musicrepro.data.LocalAudioTrack
import com.example.musicrepro.data.toMediaItem
import com.example.musicrepro.playback.MusicPlaybackService
import com.example.musicrepro.ui.theme.MusicReproTheme
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutionException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicReproTheme {
                MusicPlayerScreen()
            }
        }
    }
}

@Composable
private fun MusicPlayerScreen() {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    val repository = remember { LocalAudioRepository(context.contentResolver) }
    var tracks by remember { mutableStateOf<List<LocalAudioTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(permissionGranted, reloadCount) {
        if (permissionGranted) {
            isLoading = true
            loadError = null
            try {
                tracks = repository.loadTracks()
            } catch (exception: SecurityException) {
                loadError = "Audio access was denied. Allow access to load your music."
            } catch (exception: IllegalStateException) {
                loadError = exception.message ?: "Could not read the music library."
            } finally {
                isLoading = false
            }
        }
    }

    var connectionError by remember { mutableStateOf<String?>(null) }
    val controller = rememberMediaController { message -> connectionError = message }
    var playerRevision by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playerRevision++
                isPlaying = player.isPlaying
                shuffleEnabled = player.shuffleModeEnabled
                repeatMode = player.repeatMode
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            if (!isSeeking) seekPosition = positionMs.toFloat()
            isPlaying = controller?.isPlaying == true
            delay(400)
        }
    }

    val currentTrack = remember(tracks, playerRevision, controller) {
        val mediaId = controller?.currentMediaItem?.mediaId
        tracks.firstOrNull { it.id.toString() == mediaId }
    }
    var searchQuery by remember { mutableStateOf("") }
    val visibleTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val durationMs = currentTrack?.durationMs ?: 0L

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("YOUR MUSIC", style = MaterialTheme.typography.labelLarge)
            Text("Library", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            if (!permissionGranted) {
                EmptyState(
                    title = "Allow access to your audio",
                    message = "Music Player needs audio access to find tracks stored on this device.",
                    actionLabel = "Allow access",
                    onAction = { requestAudioPermission.launch(audioPermission) }
                )
            } else if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading your library")
                }
            } else if (loadError != null) {
                EmptyState(
                    title = "Couldn't load your music",
                    message = loadError.orEmpty(),
                    actionLabel = "Try again",
                    onAction = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            audioPermission
                        ) == PackageManager.PERMISSION_GRANTED
                        permissionGranted = granted
                        if (granted) {
                            reloadCount++
                        } else {
                            requestAudioPermission.launch(audioPermission)
                        }
                    }
                )
            } else if (tracks.isEmpty()) {
                EmptyState(
                    title = "No music found",
                    message = "Audio files on this device will appear here.",
                    actionLabel = null,
                    onAction = {}
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search tracks") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${visibleTracks.size} tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleTracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = isPlaying && currentTrack?.id == track.id,
                            onClick = {
                                val queue = tracks.map(LocalAudioTrack::toMediaItem)
                                val index = tracks.indexOfFirst { it.id == track.id }
                                if (index >= 0) {
                                    controller?.setMediaItems(queue, index, 0L)
                                    controller?.prepare()
                                    controller?.play()
                                }
                            }
                        )
                    }
                }
                if (currentTrack != null) {
                    PlayerControls(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        positionMs = positionMs,
                        seekPosition = seekPosition,
                        durationMs = durationMs,
                        onSeekChange = {
                            seekPosition = it
                            isSeeking = true
                        },
                        onSeekFinished = {
                            controller?.seekTo(seekPosition.toLong())
                            isSeeking = false
                        },
                        onTogglePlay = { controller?.let { player ->
                            if (player.isPlaying) player.pause() else player.play()
                        } },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onToggleShuffle = {
                            controller?.let { player ->
                                player.shuffleModeEnabled = !player.shuffleModeEnabled
                                shuffleEnabled = player.shuffleModeEnabled
                            }
                        },
                        onCycleRepeat = {
                            controller?.let { player ->
                                player.repeatMode = when (player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                repeatMode = player.repeatMode
                            }
                        }
                    )
                }
            }
            if (connectionError != null) {
                Text(
                    text = connectionError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TrackRow(
    track: LocalAudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(shape = CircleShape) {
            Text(
                text = if (isPlaying) "♪" else "♫",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp)
        ) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatTime(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerControls(
    track: LocalAudioTrack,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    positionMs: Long,
    seekPosition: Float,
    durationMs: Long,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Slider(
                value = seekPosition.coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                enabled = durationMs > 0L
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = shuffleEnabled,
                    onClick = onToggleShuffle,
                    label = { Text("SHUFFLE") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious) {
                        Text("⏮", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            if (isPlaying) "Ⅱ" else "▶",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    TextButton(onClick = onNext) {
                        Text("⏭", style = MaterialTheme.typography.titleLarge)
                    }
                }
                OutlinedButton(onClick = onCycleRepeat) {
                    Text(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> "REPEAT ALL"
                            Player.REPEAT_MODE_ONE -> "REPEAT ONE"
                            else -> "REPEAT OFF"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun rememberMediaController(onConnectionError: (String) -> Unit): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var disposed = false
        future.addListener({
            if (!disposed) {
                try {
                    controller = future.get()
                } catch (exception: ExecutionException) {
                    onConnectionError(
                        exception.cause?.message ?: "Could not connect to the playback service."
                    )
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                    onConnectionError("Playback service connection was interrupted.")
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            MediaController.releaseFuture(future)
        }
    }
    return controller
}
