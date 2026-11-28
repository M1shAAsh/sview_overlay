package com.sviewoverlay.s5

import android.content.Context
import org.json.JSONObject
import java.io.File

// Модель конфигурации.
data class SViewConfig(
    var widthDp: Int = 350,
    var heightDp: Int = 315,
    var marginTopDp: Int = 0,
    var marginStartDp: Int = 0,
    var clockSizeSp: Float = 76f,
    var textColor: String = "#FFFFFFFF",
    var fontKey: String = "sans-serif-light",
    var wallpaperKey: String = "wall_1"
) {
    companion object {
        private const val USER_FILE_NAME = "config.json"
        private const val ASSET_DEFAULT_NAME = "default_config.json"

        // Возвращает открытую папку /sdcard/Android/data/com.sviewoverlay.s5/files/
        private fun getConfigDir(context: Context): File {
            return context.getExternalFilesDir(null) ?: context.filesDir
        }

        // Загрузка конфига с автоматическим восстановлением при утере или сбое
        fun load(context: Context): SViewConfig {
            val userFile = File(getConfigDir(context), USER_FILE_NAME)

            // 1. Если пользовательского файла нет — восстанавливаем из резерва в assets
            if (!userFile.exists() || userFile.length() == 0L) {
                restoreDefaultFromAssets(context, userFile)
            }

            // 2. Пытаемся прочитать JSON. Если файл поврежден (ошибка синтаксиса) — восстанавливаем
            return try {
                val jsonString = userFile.readText()
                parseJson(jsonString)
            } catch (e: Exception) {
                restoreDefaultFromAssets(context, userFile)
                try {
                    parseJson(userFile.readText())
                } catch (fallbackEx: Exception) {
                    // Крайний резерв в памяти, если даже assets недоступен
                    SViewConfig()
                }
            }
        }

        // Копирование эталонного файла из assets во внутреннюю память
        private fun restoreDefaultFromAssets(context: Context, targetFile: File) {
            try {
                context.assets.open(ASSET_DEFAULT_NAME).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Превращение текста JSON в объект SViewConfig
        private fun parseJson(jsonStr: String): SViewConfig {
            val json = JSONObject(jsonStr)
            return SViewConfig(
                widthDp = json.optInt("width_dp", 350),
                heightDp = json.optInt("height_dp", 315),
                marginTopDp = json.optInt("margin_top_dp", 0),
                marginStartDp = json.optInt("margin_start_dp", 0),
                clockSizeSp = json.optDouble("clock_size_sp", 76.0).toFloat(),
                textColor = json.optString("text_color", "#FFFFFFFF"),
                fontKey = json.optString("font_key", "sans-serif-light"),
                wallpaperKey = json.optString("wallpaper_key", "wall_1")
            )
        }
    }

    // Сохранение переменных в конфиг
    fun save(context: Context) {
        val json = JSONObject().apply {
            put("width_dp", widthDp)
            put("height_dp", heightDp)
            put("margin_top_dp", marginTopDp)
            put("margin_start_dp", marginStartDp)
            put("clock_size_sp", clockSizeSp.toDouble())
            put("text_color", textColor)
            put("font_key", fontKey)
            put("wallpaper_key", wallpaperKey)
        }
        val userFile = File(getConfigDir(context), USER_FILE_NAME)
        userFile.writeText(json.toString(4))
    }
}