package com.elisheato.tracker

import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.elisheato.tracker/location"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startLocationService" -> {
                    val groupName = call.argument<String>("groupName") ?: ""
                    val userName = call.argument<String>("userName") ?: ""
                    startLocationService(groupName, userName)
                    result.success(true)
                }
                "stopLocationService" -> {
                    stopLocationService()
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun startLocationService(groupName: String, userName: String) {
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("groupName", groupName)
            putExtra("userName", userName)
        }
        startService(serviceIntent)
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }
}
