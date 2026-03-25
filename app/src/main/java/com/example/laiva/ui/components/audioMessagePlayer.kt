package com.example.laiva.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.laiva.audio.AudioPlaybackService
import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class AudioMessagePlayerViewModel : ViewModel() {
    val positions = mutableStateMapOf<String, Float>()
    val durations = mutableStateMapOf<String, Float>()
}

@Composable
fun AudioMessagePlayer(
    filePath: String,
    viewModel: AudioMessagePlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val fileName = File(filePath).name

    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(viewModel.positions[filePath] ?: 0f) }
    var duration by remember { mutableStateOf(viewModel.durations[filePath] ?: 1f) }

    LaunchedEffect(filePath) {
        while (true) {
            isPlaying = AudioPlaybackService.isPlaying && AudioPlaybackService.currentFilePath == filePath

            val currentPos = if (AudioPlaybackService.currentFilePath == filePath) {
                AudioPlaybackService.currentPosition.toFloat()
            } else position

            val currentDur = if (AudioPlaybackService.currentFilePath == filePath) {
                AudioPlaybackService.duration.toFloat()
            } else duration

            position = currentPos
            duration = currentDur

            viewModel.positions[filePath] = currentPos
            viewModel.durations[filePath] = currentDur

            delay(500)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(
            onClick = {
                val action = if (AudioPlaybackService.currentFilePath == filePath) {
                    if (isPlaying) "PAUSE" else "RESUME"
                } else "PLAY"

                val intent = Intent(context, AudioPlaybackService::class.java).apply {
                    this.action = action
                    putExtra("path", filePath)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = if (duration != 0f) position / duration else 0f,
                onValueChange = { newValue ->
                    position = newValue * duration
                    viewModel.positions[filePath] = position

                    val seekIntent = Intent(context, AudioPlaybackService::class.java).apply {
                        action = "SEEK"
                        putExtra("position", position.toInt())
                    }
                    ContextCompat.startForegroundService(context, seekIntent)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}