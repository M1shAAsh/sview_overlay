package com.sviewoverlay.s5

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import java.io.File
import androidx.core.net.toUri

object SViewUIHelper {

    val wallpapersMap = linkedMapOf(
        "sview_wallpaper_default_000" to R.drawable.sview_wallpaper_default_000,
        "sview_wallpaper_default_001" to R.drawable.sview_wallpaper_default_001,
        "sview_wallpaper_default_002" to R.drawable.sview_wallpaper_default_002,
        "sview_wallpaper_default_003" to R.drawable.sview_wallpaper_default_003,
        "sview_wallpaper_default_004" to R.drawable.sview_wallpaper_default_004,
        "sview_wallpaper_default_005" to R.drawable.sview_wallpaper_default_005,
        "sview_wallpaper_default_006" to R.drawable.sview_wallpaper_default_006,
        "sview_wallpaper_default_007" to R.drawable.sview_wallpaper_default_007,
        "sview_wallpaper_default_008" to R.drawable.sview_wallpaper_default_008,
        "sview_wallpaper_default_009" to R.drawable.sview_wallpaper_default_009
    )

    fun setupUI(context: Context, rootView: View, onCloseForPicker: () -> Unit) {
        val config = SViewConfig.load(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val clockView = rootView.findViewById<TextClock>(R.id.sview_clock)
        val dateView = rootView.findViewById<TextClock>(R.id.sview_date)
        val viewFlipper = rootView.findViewById<ViewFlipper>(R.id.sview_view_flipper)
        val density = context.resources.displayMetrics.density

        val batteryFillView = rootView.findViewById<ImageView>(R.id.sview_battery_fill)
        val batteryClipDrawable = android.graphics.drawable.ClipDrawable(
            Color.WHITE.toDrawable(),
            Gravity.START,
            android.graphics.drawable.ClipDrawable.HORIZONTAL
        )
        batteryFillView?.setImageDrawable(batteryClipDrawable)

        val windowContainer = rootView.findViewById<FrameLayout>(R.id.sview_window_container)
        fun applyGeometry() {
            // Меняем реальную ширину и высоту контейнера
            val containerParams = windowContainer.layoutParams as FrameLayout.LayoutParams
            containerParams.width = (config.widthDp * density).toInt()
            containerParams.height = (config.heightDp * density).toInt()
            containerParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowContainer.layoutParams = containerParams

            // Пермещение окна через translation
            windowContainer.translationX = config.marginStartDp * density
            windowContainer.translationY = config.marginTopDp * density
        }
        applyGeometry()

        // Нахождение поля чисел в "WindowGeometry"
        val editWidth = rootView.findViewById<android.widget.EditText>(R.id.input_width_dp)
        val editHeight = rootView.findViewById<android.widget.EditText>(R.id.input_height_dp)
        val editOffsetX = rootView.findViewById<android.widget.EditText>(R.id.input_offset_x)
        val editOffsetY = rootView.findViewById<android.widget.EditText>(R.id.input_offset_y)

        // Заполнение текущими значениями из JSON
        editWidth.setText(config.widthDp.toString())
        editHeight.setText(config.heightDp.toString())
        editOffsetX.setText(config.marginStartDp.toString())
        editOffsetY.setText(config.marginTopDp.toString())

        // Настройка кнопок "-" И "+".
        fun bindStepButtons(
            minusId: Int,
            plusId: Int,
            input: android.widget.EditText,
            step: Int = 2
        ) {
            rootView.findViewById<View>(minusId).setOnClickListener {
                val current = input.text.toString().toIntOrNull() ?: 0
                input.setText((current - step).toString())
            }
            rootView.findViewById<View>(plusId).setOnClickListener {
                val current = input.text.toString().toIntOrNull() ?: 0
                input.setText((current + step).toString())
            }
        }

        bindStepButtons(R.id.btn_width_minus, R.id.btn_width_plus, editWidth)
        bindStepButtons(R.id.btn_height_minus, R.id.btn_height_plus, editHeight)
        bindStepButtons(R.id.btn_x_minus, R.id.btn_x_plus, editOffsetX)
        bindStepButtons(R.id.btn_y_minus, R.id.btn_y_plus, editOffsetY)

        // Кнопка "Применить", Записывает значения в JSON.
        rootView.findViewById<View>(R.id.btn_apply_geometry).setOnClickListener {
            config.widthDp = editWidth.text.toString().toIntOrNull() ?: 350
            config.heightDp = editHeight.text.toString().toIntOrNull() ?: 315
            config.marginStartDp = editOffsetX.text.toString().toIntOrNull() ?: 0
            config.marginTopDp = editOffsetY.text.toString().toIntOrNull() ?: 0

            config.save(context)
            applyGeometry()
        }

        // Настройка текста.
        clockView.textSize = config.clockSizeSp
        try {
            val color = config.textColor.toColorInt()
            clockView.setTextColor(color)
            dateView.setTextColor(color)
        } catch (_: Exception) {
            clockView.setTextColor(Color.WHITE)
        }

        val galleryContainer = rootView.findViewById<GridLayout>(R.id.wallpapers_gallery_container)
        val wallpaperImage = rootView.findViewById<ImageView>(R.id.sview_wallpaper_image)
        // Обои.
        applyWallpaper(context, wallpaperImage, config.wallpaperKey)

        // Выборка обоев в настройках.
        galleryContainer.removeAllViews()
        for ((wallKey, resId) in wallpapersMap) {
            val thumb = ImageView(context).apply {
                val thumbParams = GridLayout.LayoutParams().apply {
                    width = (66 * density).toInt()
                    height = (66 * density).toInt()
                    val margin = (6 * density).toInt()
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = thumbParams
                setBackgroundColor("#80FFFFFF".toColorInt())
                val pad = (2 * density).toInt()
                setPadding(pad, pad, pad, pad)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageResource(resId)

                setOnClickListener {
                    config.wallpaperKey = wallKey
                    applyWallpaper(context, wallpaperImage, wallKey)
                    config.save(context)
                }
            }
            galleryContainer.addView(thumb)
        }

        // Собственные обои (вызов MainActivity, и применение).
        val addBtn = TextView(context).apply {
            val btnParams = GridLayout.LayoutParams().apply {
                width = (66 * density).toInt()
                height = (66 * density).toInt()
                val margin = (6 * density).toInt()
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = btnParams
            setBackgroundColor("#66000000".toColorInt())
            text = "+"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            setOnClickListener {
                onCloseForPicker()
                val pickIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ACTION_PICK_WALLPAPER"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(pickIntent)
            }
        }
        galleryContainer.addView(addBtn)

        val notifContainer = rootView.findViewById<LinearLayout>(R.id.notifications_list_container)
        // Уведомления.
        fun renderNotifications() {
            notifContainer.removeAllViews()
            val list = SViewNotificationService.notifList

            if (list.isEmpty()) {
                val emptyView = TextView(context).apply {
                    text = context.getString(R.string.sview_no_notifications)
                    setTextColor("#B3FFFFFF".toColorInt())
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, (40 * density).toInt(), 0, 0)
                }
                notifContainer.addView(emptyView)
                return
            }

            for (item in list) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor("#66000000".toColorInt())
                    val pad = (10 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (8 * density).toInt()
                    }
                }

                val titleView = TextView(context).apply {
                    text = item.title
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                }

                val textView = TextView(context).apply {
                    text = item.text
                    setTextColor("#E6FFFFFF".toColorInt())
                    textSize = 14f
                    maxLines = 3
                }

                card.addView(titleView)
                card.addView(textView)
                notifContainer.addView(card)
                // Удаление уведомлений.
                card.setOnClickListener {
                    SViewNotificationService.dismissNotification(item.key)
                }
            }
        }

        renderNotifications()
        SViewNotificationService.onNotificationsUpdated = {
            rootView.post { renderNotifications() }
        }

        val batteryView = rootView.findViewById<TextView>(R.id.sview_battery_text)
        fun updateBatteryInfo(intent: Intent?) {
            if (batteryView == null || intent == null) return
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)

            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL

                batteryClipDrawable.level = pct * 100

                // 2. Меняем цвет шкалы в зависимости от состояния
                val fillColor = when {
                    isCharging -> "#FF4CAF50".toColorInt() // Зелёный при зарядке.
                    pct <= 15 -> "#FFF44336".toColorInt()  // Красный при низкой.
                    else -> "#FFFFFFFF".toColorInt()       // Белый при обычной.
                }
                batteryFillView?.setColorFilter(fillColor)

                // 3. Обновляем текстовую подпись
                batteryView.text = if (isCharging) {
                    "$pct% • ${context.getString(R.string.sview_charging)}"
                } else {
                    "$pct%"
                }
            }
        }

        // Регистрация статуса батареи, Только при активном Overlay.
        val batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                updateBatteryInfo(intent)
            }
        }

        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val stickyIntent = context.registerReceiver(
                    batteryReceiver,
                    android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                updateBatteryInfo(stickyIntent)
            }

            override fun onViewDetachedFromWindow(v: View) {
                try {
                    context.unregisterReceiver(batteryReceiver)
                } catch (_: Exception) {
                }
            }
        })

        val musicContainer = rootView.findViewById<LinearLayout>(R.id.sview_music_container)
        val musicTitleView = rootView.findViewById<TextView>(R.id.sview_music_title)
        val btnMusicPrev = rootView.findViewById<ImageView>(R.id.btn_music_prev)
        val btnMusicPlayPause = rootView.findViewById<ImageView>(R.id.btn_music_play_pause)
        val btnMusicNext = rootView.findViewById<ImageView>(R.id.btn_music_next)

        // Включаем бегущую строку для длинных названий песен
        musicTitleView?.isSelected = true

        val mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, SViewNotificationService::class.java)
        var activeController: android.media.session.MediaController? = null
        var lastTrackTitle = ""
        var hasAlbumArt = false

        fun updateMusicUI() {
            if (musicContainer == null) return
            val controller = activeController
            val state = controller?.playbackState?.state
            val isMediaSessionActive = state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_PAUSED
            val isSystemMusicPlaying = audioManager.isMusicActive

            // 1. Если музыка полностью выключена — прячем плеер и возвращаем стандартные обои
            if (!isMediaSessionActive && !isSystemMusicPlaying) {
                musicContainer.visibility = View.GONE
                if (hasAlbumArt) {
                    hasAlbumArt = false
                    lastTrackTitle = ""
                    applyWallpaper(context, wallpaperImage, config.wallpaperKey)
                }
                return
            }

            musicContainer.visibility = View.VISIBLE

            if (controller != null && isMediaSessionActive) {
                val meta = controller.metadata
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""

                // Если метаданные временно пустые в момент переключения трека — не трогаем текущую картинку!
                if (meta == null && title.isEmpty()) return

                musicTitleView?.text = when {
                    artist.isNotEmpty() && title.isNotEmpty() -> "$artist — $title"
                    title.isNotEmpty() -> title
                    else -> "Music"
                }

                val isPlaying = state == PlaybackState.STATE_PLAYING
                btnMusicPlayPause?.setImageResource(
                    if (isPlaying) R.drawable.music_control_btn_pause_normal else R.drawable.music_control_btn_play_normal
                )

                val albumArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                if (albumArt != null) {
                    wallpaperImage.setImageBitmap(albumArt)
                    hasAlbumArt = true
                    lastTrackTitle = title
                } else {
                    val artUriStr = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                        ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    if (!artUriStr.isNullOrEmpty()) {
                        try {
                            wallpaperImage.setImageURI(artUriStr.toUri())
                            hasAlbumArt = true
                            lastTrackTitle = title
                        } catch (_: Exception) {
                            if (hasAlbumArt) {
                                hasAlbumArt = false
                                applyWallpaper(context, wallpaperImage, config.wallpaperKey)
                            }
                        }
                    } else if (title.isNotEmpty() && title != lastTrackTitle) {
                        // Новый трек точно загрузился, и у него нет обложки — возвращаем обычные обои
                        lastTrackTitle = title
                        if (hasAlbumArt) {
                            hasAlbumArt = false
                            applyWallpaper(context, wallpaperImage, config.wallpaperKey)
                        }
                    }
                }
            } else {
                musicTitleView?.text = "Music"
                btnMusicPlayPause?.setImageResource(
                    if (isSystemMusicPlaying) R.drawable.music_control_btn_pause_normal else R.drawable.music_control_btn_play_normal
                )
            }
        }

        val controllerCallback = object : android.media.session.MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                rootView.post { updateMusicUI() }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                rootView.post { updateMusicUI() }
            }
        }

        fun bindActiveSession(controllers: List<android.media.session.MediaController>?) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = controllers?.firstOrNull()
            activeController?.registerCallback(controllerCallback)
            rootView.post { updateMusicUI() }
        }

        val sessionsListener =
            android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                bindActiveSession(controllers)
            }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            bindActiveSession(mediaSessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            // Если не выдано разрешение "Доступ к уведомлениям", плеер просто не отображается
            musicContainer?.visibility = View.GONE
        }

        // Отписываемся от слушателя при закрытии окна чехла, чтобы не было утечек памяти
        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                try {
                    activeController?.unregisterCallback(controllerCallback)
                    mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
                } catch (_: Exception) {
                }
            }
        })

        // Обработчики кнопок управления плеером
        btnMusicPrev?.setOnClickListener {
            activeController?.transportControls?.skipToPrevious()
        }
        btnMusicPlayPause?.setOnClickListener {
            val isPlaying =
                activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                activeController?.transportControls?.pause()
            } else {
                activeController?.transportControls?.play()
            }
        }
        btnMusicNext?.setOnClickListener {
            activeController?.transportControls?.skipToNext()
        }

        val callStatusView = rootView.findViewById<TextView>(R.id.sview_call_status)
        val callerTextView = rootView.findViewById<TextView>(R.id.sview_caller_number)
        val callerPhotoView = rootView.findViewById<ImageView>(R.id.sview_caller_photo)
        val answerBtn = rootView.findViewById<View>(R.id.btn_call_answer)
        val speakerBtn = rootView.findViewById<ImageView>(R.id.btn_call_speaker)
        val muteBtn = rootView.findViewById<ImageView>(R.id.btn_call_mute)
        val rejectBtn = rootView.findViewById<View>(R.id.btn_call_reject)
        val callTimerView = rootView.findViewById<android.widget.Chronometer>(R.id.sview_call_timer)

        fun updateCallUI() {
            val active = SViewService.isRinging || SViewService.isOffhook
            if (active) {
                val rawNumber = SViewService.currentCallerNumber
                if (rawNumber.isNotEmpty()) {
                    val info = getContactInfo(context, rawNumber)
                    callerTextView.text = info.name
                    if (info.photo != null) {
                        callerPhotoView.setImageBitmap(info.photo)
                    } else {
                        callerPhotoView.setImageResource(R.drawable.call_unknown_person)
                    }
                } else {
                    callerTextView.text = context.getString(R.string.sview_unknown_number)
                    callerPhotoView.setImageResource(R.drawable.call_unknown_person)
                }

                if (SViewService.isRinging) {
                    // Гудки: показываем текст статуса, прячем и останавливаем таймер
                    callStatusView.visibility = View.VISIBLE
                    callStatusView.text = context.getString(R.string.sview_incoming_call)
                    callTimerView.stop()
                    callTimerView.visibility = View.GONE

                    answerBtn.visibility = View.VISIBLE
                    speakerBtn.visibility = View.GONE
                    muteBtn.visibility = View.GONE
                } else {
                    // Идёт разговор (OFFHOOK): прячем текст и запускаем живой таймер с точной точки старта
                    callStatusView.visibility = View.GONE
                    callTimerView.visibility = View.VISIBLE
                    callTimerView.base = if (SViewService.callStartTime > 0L) {
                        SViewService.callStartTime
                    } else {
                        android.os.SystemClock.elapsedRealtime()
                    }
                    callTimerView.start()

                    answerBtn.visibility = View.GONE
                    speakerBtn.visibility = View.VISIBLE
                    muteBtn.visibility = View.VISIBLE

                    speakerBtn.setImageResource(
                        if (audioManager.isSpeakerphoneOn) R.drawable.during_call_btn_speaker_normal_on else R.drawable.during_call_btn_speaker_normal
                    )

                    muteBtn.setImageResource(
                        if (audioManager.isMicrophoneMute) R.drawable.during_call_btn_mute_normal_on else R.drawable.during_call_btn_mute_normal
                    )
                }
                viewFlipper.displayedChild = 5
            } else {
                // Звонок завершён: обязательно останавливаем Chronometer, чтобы не тратил батарею
                callTimerView.stop()
                if (viewFlipper.displayedChild == 5) {
                    viewFlipper.displayedChild = 0
                }
            }
        }

        updateCallUI()
        SViewService.onCallStateChanged = { updateCallUI() }

        // Переключение громкой связи.
        speakerBtn.setOnClickListener {
            audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
            updateCallUI()
        }

        // Переключение микрофона.
        muteBtn.setOnClickListener {
            audioManager.isMicrophoneMute = !audioManager.isMicrophoneMute
            updateCallUI()
        }

        // Принять вызов.
        answerBtn.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val telecom =
                        context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                    telecom.acceptRingingCall()
                } catch (_: SecurityException) {
                }
            }
        }

        // Отклонить или завершить вызов.
        rejectBtn.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val telecom =
                        context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                    telecom.endCall()
                } catch (_: SecurityException) {
                }
            } else {
                // Резервный сброс для ранних API.
                Thread {
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 6")).waitFor()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }


        // Применение шрифта.
        applyFont(context, config.fontKey, clockView, dateView, batteryView)

        val fontsListContainer = rootView.findViewById<LinearLayout>(R.id.fonts_list_container)

        fun renderFontsList() {
            if (fontsListContainer == null) return
            fontsListContainer.removeAllViews()

            val availableFonts = mutableListOf<Pair<String, String>>()

            // 1. Автоматически читаем все файлы из app/src/main/assets/fonts/
            try {
                val assetFiles = context.assets.list("fonts")
                assetFiles?.filter {
                    it.endsWith(".ttf", ignoreCase = true) || it.endsWith(".otf", ignoreCase = true)
                }?.forEach { fileName ->
                    availableFonts.add("asset:$fileName" to fileName.substringBeforeLast("."))
                }
            } catch (_: Exception) {
            }

            // 2. Системные контрольные шрифты (чтобы сразу видеть разницу)
            availableFonts.add("serif" to "System Serif (С засечками)")
            availableFonts.add("monospace" to "System Monospace (Цифровой)")

            // 3. Пользовательские шрифты из папки /sdcard/Android/data/.../files/fonts/
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val fontsDir = File(baseDir, "fonts")
            if (fontsDir.exists()) {
                fontsDir.listFiles()?.filter {
                    it.name.endsWith(".ttf", ignoreCase = true) || it.name.endsWith(
                        ".otf",
                        ignoreCase = true
                    )
                }?.forEach { file ->
                    availableFonts.add(file.name to file.name)
                }
            }

            for ((key, label) in availableFonts) {
                val itemView = TextView(context).apply {
                    text = label
                    textSize = 16f
                    // Выделение текущего шрифта цветом.
                    setTextColor(if (config.fontKey == key) "#FF4FC3F7".toColorInt() else Color.WHITE)
                    val pad = (12 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setBackgroundColor("#44000000".toColorInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }

                    // Предпросмотр шрифта в списке.
                    applyFont(context, key, this)

                    setOnClickListener {
                        config.fontKey = key
                        config.save(context)
                        applyFont(context, key, clockView, dateView, batteryView)
                        renderFontsList()
                    }
                }
                fontsListContainer.addView(itemView)
            }
        }
        renderFontsList()

        // Кнопка, Для вызова MainActivity.
        rootView.findViewById<View>(R.id.btn_pick_custom_font)?.setOnClickListener {
            onCloseForPicker()
            val pickFontIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_PICK_FONT"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(pickFontIntent)
        }

        // Переход на окно с часами.
        listOf(R.id.btn_back_to_clock, R.id.btn_extra_back).forEach { id ->
            rootView.findViewById<View>(id)?.setOnClickListener { viewFlipper.displayedChild = 0 }
        }

        // Переход в настройки.
        listOf(
            R.id.btn_open_settings,
            R.id.btn_wallpaper_back,
            R.id.btn_geometry_back
        ).forEach { id ->
            rootView.findViewById<View>(id)?.setOnClickListener { viewFlipper.displayedChild = 2 }
        }

        // Переход в под-пункты настроек.
        rootView.findViewById<View>(R.id.menu_item_wallpapers)
            ?.setOnClickListener { viewFlipper.displayedChild = 3 }
        rootView.findViewById<View>(R.id.menu_item_geometry)
            ?.setOnClickListener { viewFlipper.displayedChild = 4 }
        // Открытие "Другое", И его под-пункты.
        listOf(R.id.btn_open_extra, R.id.btn_notif_back, R.id.btn_fonts_back).forEach { id ->
            rootView.findViewById<View>(id)?.setOnClickListener { viewFlipper.displayedChild = 6 }
        }
        rootView.findViewById<View>(R.id.menu_item_notifications)
            ?.setOnClickListener { viewFlipper.displayedChild = 1 }
        rootView.findViewById<View>(R.id.menu_item_fonts)
            ?.setOnClickListener { viewFlipper.displayedChild = 7 }

    }

    data class ContactInfo(
        val name: String,
        val photo: android.graphics.Bitmap?
    )

    private fun getContactInfo(context: Context, phoneNumber: String): ContactInfo {
        if (phoneNumber.isEmpty()) return ContactInfo("", null)
        try {
            val lookupUri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                android.provider.ContactsContract.PhoneLookup._ID,
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME
            )

            context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)
                    val displayName = cursor.getString(1) ?: phoneNumber

                    // Пытаемся загрузить полную фотографию контакта по его ID
                    val contactUri = android.content.ContentUris.withAppendedId(
                        android.provider.ContactsContract.Contacts.CONTENT_URI,
                        contactId
                    )
                    val photoStream =
                        android.provider.ContactsContract.Contacts.openContactPhotoInputStream(
                            context.contentResolver,
                            contactUri,
                            true // true = запрашиваем фото высокого качества, если оно есть
                        )
                    val bitmap = photoStream?.use { BitmapFactory.decodeStream(it) }
                    return ContactInfo(displayName, bitmap)
                }
            }
        } catch (_: Exception) {
            // Если нет разрешения READ_CONTACTS — возвращаем только номер без фото
        }
        return ContactInfo(phoneNumber, null)
    }

    fun applyWallpaper(context: Context, wallpaperImage: ImageView, key: String) {
        if (key == "custom") {
            val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(targetDir, "custom_wallpaper.jpg")
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                if (bitmap != null) {
                    wallpaperImage.setImageBitmap(bitmap)
                    return
                }
            }
        }
        val resId = wallpapersMap[key] ?: R.drawable.sview_wallpaper_default_000
        wallpaperImage.setImageResource(resId)
    }

    fun applyFont(context: Context, fontKey: String, vararg textViews: TextView) {
        val typeface: Typeface = try {
            when {
                // Шрифты встроенные в приложение.
                fontKey.startsWith("asset:") -> {
                    val fileName = fontKey.removePrefix("asset:")
                    Typeface.createFromAsset(context.assets, "fonts/$fileName")
                }
                // Моноширные системные шрифты.
                fontKey == "monospace" -> Typeface.MONOSPACE
                fontKey == "serif" -> Typeface.SERIF
                // Пользовательские ttf/otf Шрифты, Из папки сервиса.
                else -> {
                    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val fontFile = File(File(baseDir, "fonts"), fontKey)
                    if (fontFile.exists()) {
                        Typeface.createFromFile(fontFile)
                    } else {
                        Typeface.DEFAULT
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Typeface.DEFAULT
        }

        for (tv in textViews) {
            // Сбрасываем fontFamily и принудительно ставим новый Typeface
            tv.typeface = typeface
        }
    }

}
