package com.sviewoverlay.s5

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.io.File

class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startSViewService()
        }
        finish()
    }

    // Обработчик выбора картинки из галереи
    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            saveCustomWallpaper(uri)
        }
        finish()
    }

    // Выбор шрифта (ttf,otf)
    private val pickFontLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            saveCustomFont(uri)
        }
        finish()
    }

    private fun saveCustomFont(uri: Uri) {
        try {
            // Получение действительного имени файла.
            var fileName = "custom_font_${System.currentTimeMillis()}.ttf"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrEmpty()) fileName = rawName
                }
            }

            // Сохранение шрифта в открытую папку.
            val baseDir = getExternalFilesDir(null) ?: filesDir
            val fontsDir = File(baseDir, "fonts").apply { if (!exists()) mkdirs() }
            val destFile = File(fontsDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Запись шрифта в конфиг.
            val config = SViewConfig.load(this)
            config.fontKey = fileName
            config.save(this)

            // Сообщение службе, О повторном показе окна.
            val showIntent = Intent(this, SViewService::class.java).apply { action = "ACTION_SHOW" }
            startService(showIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Открытие по вызову для применения - Открыть проводник с фильтрацией.
        if (intent?.action == "ACTION_PICK_WALLPAPER") {
            pickWallpaperLauncher.launch("image/*")
            return
        }
        if (intent?.action == "ACTION_PICK_FONT") {
            pickFontLauncher.launch(
                arrayOf(
                    "font/*",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "*/*"
                )
            )
            return
        }

        val callPermissions = arrayOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.ANSWER_PHONE_CALLS
        )
        androidx.core.app.ActivityCompat.requestPermissions(this, callPermissions, 102)

        // Запуск из лаунчера - Проверка разрешений, Запуск сервиса.
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разрешите показ поверх других окон", Toast.LENGTH_LONG).show()
            val permIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(permIntent)
        } else {
            startSViewService()
            finish()
        }
    }

    private fun startSViewService() {
        val serviceIntent = Intent(this, SViewService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun saveCustomWallpaper(uri: Uri) {
        try {
            val targetDir = getExternalFilesDir(null) ?: filesDir
            val file = File(targetDir, "custom_wallpaper.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Сохраняем выбор в SViewConfig
            val config = SViewConfig.load(this)
            config.wallpaperKey = "custom"
            config.save(this)

            // Сообщаем службе, что картинка обновилась
            val updateIntent = Intent(this, SViewService::class.java).apply {
                action = "ACTION_UPDATE_WALLPAPER"
            }
            startService(updateIntent)
            Toast.makeText(this, "Обои установлены!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
        }
    }
}