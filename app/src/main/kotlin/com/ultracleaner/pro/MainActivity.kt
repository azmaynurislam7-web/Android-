package com.ultracleaner.pro

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 16, 30)
    private val card = Color.rgb(25, 32, 51)
    private val accent = Color.rgb(0, 220, 170)
    private val white = Color.WHITE
    private val muted = Color.rgb(165, 178, 200)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDashboard()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun text(
        value: String,
        size: Float = 16f,
        color: Int = white
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun button(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(Color.rgb(40, 65, 85))
            setOnClickListener { action() }
        }
    }

    private fun section(
        title: String,
        subtitle: String
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(card)
        }

        box.addView(text(title, 18f))
        box.addView(
            text(
                subtitle,
                13f,
                muted
            ).apply {
                setPadding(0, dp(6), 0, 0)
            }
        )

        content.addView(
            box,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun showDashboard() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(18), dp(18), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        header.addView(text("ULTRA CLEANER", 26f, accent))
        header.addView(
            text("PRO  •  SMART PHONE CARE", 12f, muted)
        )

        root.addView(header)

        status = text(
            "Your device care dashboard",
            14f,
            muted
        ).apply {
            setPadding(0, dp(14), 0, dp(14))
        }

        root.addView(status)

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(content)

        // Yahan width ko -1 (MATCH_PARENT) kar diya gaya hai taaki saare buttons screen par dikhein
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        progress = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(accent)
        }

        content.addView(progress)

        val storage = storageInfo()

        section(
            "📱  STORAGE OVERVIEW",
            storage
        )

        content.addView(
            button("⚡  SCAN DEVICE STORAGE") {
                scanStorage()
            }
        )

        content.addView(
            button("🗑️  OPEN APP SETTINGS") {
                openAppSettings()
            }
        )

        content.addView(
            button("🛡️  SECURITY CHECK") {
                securityCheck()
            }
        )

        content.addView(
            button("📦  VIEW INSTALLED APPS") {
                openInstalledApps()
            }
        )

        content.addView(
            button("📁  BROWSE FILES") {
                browseFiles()
            }
        )

        content.addView(
            button("ℹ️  ABOUT THIS APP") {
                about()
            }
        )

        section(
            "PRIVACY PROTECTION",
            "This app does not claim to detect every virus. " +
                "No files are deleted automatically."
        )

        setContentView(root)
    }

    private fun storageInfo(): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)

            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free

            "Total: ${formatBytes(total)}\n" +
                "Used: ${formatBytes(used)}\n" +
                "Available: ${formatBytes(free)}"
        } catch (e: Exception) {
            "Storage information is unavailable."
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "Unknown"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var index = 0

        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }

        return String.format(
            Locale.getDefault(),
            "%.2f %s",
            size,
            units[index]
        )
    }

    private fun scanStorage() {
        status.text = "Scanning storage information..."
        progress.progress = 0

        Thread {
            val info = storageInfo()

            runOnUiThread {
                status.text = "Storage scan completed"
                progress.progress = 100

                AlertDialog.Builder(this)
                    .setTitle("Storage Scan Report")
                    .setMessage(
                        "$info\n\n" +
                        "This scan measures storage usage. " +
                        "It does not identify all junk files " +
                        "or delete any files."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open app settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun securityCheck() {
        status.text = "Security check opened"

        AlertDialog.Builder(this)
            .setTitle("Security Check")
            .setMessage(
                "Android security information\n\n" +
                "• Review apps you installed.\n" +
                "• Install Android security updates.\n" +
                "• Enable Google Play Protect.\n\n" +
                "This basic check is not a virus scan. " +
                "It cannot certify that your device is malware-free."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openInstalledApps() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open installed apps",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun browseFiles() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "File picker unavailable",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun about() {
        AlertDialog.Builder(this)
            .setTitle("ULTRA CLEANER PRO")
            .setMessage(
                "Version 1.0\n\n" +
                "A device storage information and " +
                "security guidance app built with Kotlin.\n\n" +
                "Your files are not deleted automatically."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
