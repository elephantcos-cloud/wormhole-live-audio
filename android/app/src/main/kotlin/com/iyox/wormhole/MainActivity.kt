package com.iyox.wormhole

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val channelName = "com.iyox.wormhole/audio_stream"
    private val projectionRequestCode = 3001
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startStream" -> {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            result.error("UNSUPPORTED", "Android 10+ required", null)
                            return@setMethodCallHandler
                        }
                        pendingResult = result
                        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE)
                                as MediaProjectionManager
                        startActivityForResult(
                            mgr.createScreenCaptureIntent(),
                            projectionRequestCode
                        )
                    }
                    "stopStream" -> {
                        startService(Intent(this, AudioStreamService::class.java).apply {
                            action = AudioStreamService.ACTION_STOP
                        })
                        result.success(true)
                    }
                    "startReceive" -> {
                        val ip = call.argument<String>("ip") ?: run {
                            result.error("NO_IP", "IP address required", null)
                            return@setMethodCallHandler
                        }
                        val port = call.argument<Int>("port") ?: AudioStreamService.STREAM_PORT
                        startForegroundService(
                            Intent(this, AudioReceiverService::class.java).apply {
                                action = AudioReceiverService.ACTION_START
                                putExtra(AudioReceiverService.EXTRA_IP, ip)
                                putExtra(AudioReceiverService.EXTRA_PORT, port)
                            }
                        )
                        result.success(true)
                    }
                    "stopReceive" -> {
                        startService(Intent(this, AudioReceiverService::class.java).apply {
                            action = AudioReceiverService.ACTION_STOP
                        })
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequestCode) {
            if (resultCode == RESULT_OK && data != null) {
                startForegroundService(
                    Intent(this, AudioStreamService::class.java).apply {
                        action = AudioStreamService.ACTION_START
                        putExtra(AudioStreamService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(AudioStreamService.EXTRA_DATA, data)
                    }
                )
                pendingResult?.success(true)
            } else {
                pendingResult?.success(false)
            }
            pendingResult = null
        }
    }
}
