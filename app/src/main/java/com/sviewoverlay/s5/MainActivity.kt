package com.sviewoverlay.s5

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val PICK_WALLPAPER_REQ = 1001
    private val PICK_FONT_REQ = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Обработка вызовов из Overlay...
        when (intent?.action) {
            "ACTION_PICK_WALLPAPER" -> {
                openWallpaperPicker()
                return
            }
            "ACTION_PICK_FONT" -> {
                openFontPicker()
                return
            }
        }

        // Проверяем статус сервиса.
        startSViewService()

        // Управление сервисом через меню.
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor("#121212".toColorInt())
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.mod_title)
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val statusView = TextView(this).apply {
            text = getString(R.string.mod_status_desc)
            textSize = 14f
            setTextColor("#B3FFFFFF".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
        }

        fun makeActionButton(label: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundColor("#2A2A2A".toColorInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
                setOnClickListener { onClick() }
            }
        }

        val btnRestartService = makeActionButton(getString(R.string.mod_btn_restart)) {
            stopService(Intent(this, SViewService::class.java))
            startSViewService()
            Toast.makeText(this, getString(R.string.mod_toast_restarted), Toast.LENGTH_SHORT).show()
        }

        val btnPickWallpaper = makeActionButton(getString(R.string.mod_btn_pick_wallpaper)) {
            openWallpaperPicker()
        }

        val btnPickFont = makeActionButton(getString(R.string.mod_btn_pick_font)) {
            openFontPicker()
        }

        rootLayout.addView(titleView)
        rootLayout.addView(statusView)
        rootLayout.addView(btnRestartService)
        rootLayout.addView(btnPickWallpaper)
        rootLayout.addView(btnPickFont)

        setContentView(rootLayout)
    }

    private fun startSViewService() {
        val serviceIntent = Intent(this, SViewService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun openWallpaperPicker() {
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(
            Intent.createChooser(pickIntent, getString(R.string.mod_chooser_wallpaper)),
            PICK_WALLPAPER_REQ
        )
    }

    private fun openFontPicker() {
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(
            Intent.createChooser(pickIntent, getString(R.string.mod_chooser_font)),
            PICK_FONT_REQ
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: run {
            if (intent?.action != null) finish()
            return
        }

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_WALLPAPER_REQ -> {
                    val targetDir = getExternalFilesDir(null) ?: filesDir
                    val destFile = File(targetDir, "custom_wallpaper.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    val config = SViewConfig.load(this)
                    config.wallpaperKey = "custom"
                    config.save(this)
                    Toast.makeText(this, getString(R.string.mod_toast_wallpaper_applied), Toast.LENGTH_SHORT).show()
                }
                PICK_FONT_REQ -> {
                    val fileName = getFileName(this, uri) ?: "custom_font.ttf"
                    val baseDir = getExternalFilesDir(null) ?: filesDir
                    val fontsDir = File(baseDir, "fonts").apply { mkdirs() }
                    val destFile = File(fontsDir, fileName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    val config = SViewConfig.load(this)
                    config.fontKey = fileName
                    config.save(this)
                    Toast.makeText(this, getString(R.string.mod_toast_font_added, fileName), Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (intent?.action == "ACTION_PICK_WALLPAPER" || intent?.action == "ACTION_PICK_FONT") {
            finish()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }
}