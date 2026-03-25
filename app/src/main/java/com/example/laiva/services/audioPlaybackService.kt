package com.example.laiva.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import com.example.laiva.R
import java.io.File
import kotlinx.coroutines.*
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

class AudioPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "LAIVA_AUDIO"
        const val NOTIFICATION_ID = 2001
        const val ACTION_UPDATE_PROGRESS = "UPDATE_PROGRESS"

        var currentPosition: Int = 0
        var duration: Int = 1
        var isPlaying: Boolean = false
        var currentFilePath: String? = null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "AudioSession")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val path = intent.getStringExtra("path") ?: return START_NOT_STICKY
                play(path)
            }
            "PAUSE" -> pause()
            "RESUME" -> resume()
            "STOP" -> stop()
            "SEEK" -> {
                val pos = intent.getIntExtra("position", 0)
                seekTo(pos)
            }
            ACTION_UPDATE_PROGRESS -> updateNotification()
        }
        return START_STICKY
    }

    private fun play(path: String) {
        try {
            if (currentPath == path && mediaPlayer?.isPlaying == true) return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer()
            currentPath = path
            AudioPlaybackService.currentFilePath = path

            val file = File(path)
            if (!file.exists() || !file.canRead()) return

            mediaPlayer?.apply {
                setDataSource(file.absolutePath)

                setOnPreparedListener {
                    start()
                    AudioPlaybackService.isPlaying = true
                    AudioPlaybackService.duration = duration
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startProgressUpdater()
                }

                setOnCompletionListener {
                    stop()
                }

                prepareAsync()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pause() {
        mediaPlayer?.pause()
        AudioPlaybackService.isPlaying = false
        updateNotification()
    }

    private fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                AudioPlaybackService.isPlaying = true
                updateNotification()
            }
        }
    }

    private fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        AudioPlaybackService.isPlaying = false
        AudioPlaybackService.currentFilePath = null
        stopForeground(true)
        stopSelf()
        serviceScope.cancel()
    }

    private fun buildNotification(): Notification {
        val pauseIntent = Intent(this, AudioPlaybackService::class.java).apply { action = "PAUSE" }
        val pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val resumeIntent = Intent(this, AudioPlaybackService::class.java).apply { action = "RESUME" }
        val resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = currentPath?.substringAfterLast("/") ?: "Audio"
        val dur = mediaPlayer?.duration ?: 1
        val pos = mediaPlayer?.currentPosition ?: 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Laiva")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(dur, pos, false)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .addAction(
                if (mediaPlayer?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play,
                if (mediaPlayer?.isPlaying == true) "Pause" else "Play",
                if (mediaPlayer?.isPlaying == true) pausePendingIntent else resumePendingIntent
            )
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(mediaPlayer?.isPlaying == true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Laiva Audio", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startProgressUpdater() {
        serviceScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                AudioPlaybackService.currentPosition = mediaPlayer?.currentPosition ?: 0
                delay(500)
                val updateIntent = Intent(this@AudioPlaybackService, AudioPlaybackService::class.java).apply { action = ACTION_UPDATE_PROGRESS }
                startService(updateIntent)
            }
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        AudioPlaybackService.isPlaying = false
        AudioPlaybackService.currentFilePath = null
        serviceScope.cancel()
        super.onDestroy()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        AudioPlaybackService.currentPosition = position
        updateNotification()
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 1

    override fun onBind(intent: Intent?): IBinder? = null
}









object SoundManager {
    fun playSentSound(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }

        try {
            val mediaPlayer = MediaPlayer()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer.setAudioAttributes(attributes)

            val assetFileDescriptor = context.resources.openRawResourceFd(R.raw.outgoing_message)
            mediaPlayer.setDataSource(
                assetFileDescriptor.fileDescriptor,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.length
            )
            assetFileDescriptor.close()

            mediaPlayer.prepare()

            mediaPlayer.setOnCompletionListener {
                it.release()
            }

            mediaPlayer.start()
        } catch (e: Exception) {
            android.util.Log.e("LAIVA_SOUND", "Ошибка: ${e.message}")
        }
    }
}

