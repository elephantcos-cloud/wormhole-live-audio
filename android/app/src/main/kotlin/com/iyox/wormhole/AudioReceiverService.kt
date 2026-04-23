package com.iyox.wormhole

import android.app.*
import android.content.Intent
import android.media.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.net.Socket

class AudioReceiverService : Service() {

    private var socket: Socket? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackThread: Thread? = null

    companion object {
        const val ACTION_START = "START_RECEIVE"
        const val ACTION_STOP  = "STOP_RECEIVE"
        const val EXTRA_IP   = "IP"
        const val EXTRA_PORT = "PORT"
        const val CHANNEL_ID = "WormholeAudioReceiveChannel"
        const val NOTIFICATION_ID = 2002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ip   = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, AudioStreamService.STREAM_PORT)
                startForeground(NOTIFICATION_ID, createNotification())
                startPlayback(ip, port)
            }
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(ip: String, port: Int) {
        val sampleRate = AudioStreamService.SAMPLE_RATE
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 4

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        isPlaying = true
        playbackThread = Thread {
            try {
                socket = Socket(ip, port)
                val input: InputStream = socket!!.getInputStream()
                audioTrack?.play()
                val buffer = ByteArray(4096)
                while (isPlaying) {
                    val read = input.read(buffer)
                    if (read > 0) audioTrack?.write(buffer, 0, read)
                }
            } catch (_: Exception) {
            } finally {
                cleanup()
            }
        }
        playbackThread?.start()
    }

    private fun stopPlayback() {
        isPlaying = false
        playbackThread?.interrupt()
        cleanup()
    }

    private fun cleanup() {
        runCatching { socket?.close() }
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        audioTrack = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Wormhole Audio Receive",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wormhole")
            .setContentText("Receiving audio stream...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
