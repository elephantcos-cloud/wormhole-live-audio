package com.iyox.wormhole

import android.app.*
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class AudioStreamService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isStreaming = false
    private var streamingThread: Thread? = null

    companion object {
        const val ACTION_START = "START_STREAM"
        const val ACTION_STOP  = "STOP_STREAM"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA = "DATA"
        const val CHANNEL_ID = "WormholeAudioStreamChannel"
        const val NOTIFICATION_ID = 2001
        const val STREAM_PORT = 55124
        const val SAMPLE_RATE = 44100
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode != -1 && data != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startAudioCapture(resultCode, data)
                }
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioCapture(resultCode: Int, data: Intent) {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 4

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        isStreaming = true
        streamingThread = Thread { runServer() }
        streamingThread?.start()
    }

    private fun runServer() {
        try {
            serverSocket = ServerSocket(STREAM_PORT)
            audioRecord?.startRecording()
            val buffer = ByteArray(4096)
            clientSocket = serverSocket?.accept()
            val out: OutputStream = clientSocket!!.getOutputStream()

            while (isStreaming && clientSocket?.isConnected == true) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    out.write(buffer, 0, read)
                    out.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            cleanup()
        }
    }

    private fun stopCapture() {
        isStreaming = false
        streamingThread?.interrupt()
        cleanup()
    }

    private fun cleanup() {
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { mediaProjection?.stop() }
        audioRecord = null
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Wormhole Audio Stream",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wormhole")
            .setContentText("System audio streaming...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
