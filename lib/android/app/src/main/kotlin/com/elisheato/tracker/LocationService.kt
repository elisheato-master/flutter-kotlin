package com.elisheato.tracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private val TAG = "LocationService"
    private val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
    private val NOTIFICATION_ID = 1001
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    
    private var groupName: String = ""
    private var userName: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var lastLocation: Location? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            groupName = intent.getStringExtra("groupName") ?: ""
            userName = intent.getStringExtra("userName") ?: ""
            Log.d(TAG, "Service started with groupName: $groupName, userName: $userName")
        }
        
        startForeground()
        startLocationUpdates()
        startPeriodicUpdates()
        
        return START_STICKY
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(15000)
            .build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    lastLocation = location
                    Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    private fun startPeriodicUpdates() {
        serviceScope.launch {
            while (isActive) {
                try {
                    delay(2 * 60 * 1000) // 2 minutes
                    lastLocation?.let { sendLocationToMongoDB(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic updates", e)
                }
            }
        }
    }

    private fun sendLocationToMongoDB(location: Location) {
        try {
            Log.d(TAG, "Sending location to MongoDB")
            
            // MongoDB Atlas API endpoint (replace with your actual endpoint)
            val url = "https://data.mongodb-api.com/app/[YOUR_APP_ID]/endpoint/data/v1/action/insertOne"
            
            val json = JSONObject().apply {
                put("collection", userName)
                put("database", groupName)
                put("dataSource", "Cluster0") // Replace with your actual data source name
                put("document", JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("timestamp", System.currentTimeMillis())
                    put("accuracy", location.accuracy)
                    put("provider", location.provider)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        put("verticalAccuracy", location.verticalAccuracyMeters)
                    }
                })
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("api-key", "[YOUR_API_KEY]") // Replace with your actual API key
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to send location to MongoDB", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Unsuccessful response: ${response.code}")
                    } else {
                        Log.d(TAG, "Location saved successfully")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sending location to MongoDB", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for tracking location in the background"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking location in background")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
