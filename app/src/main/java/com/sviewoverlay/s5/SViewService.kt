package com.sviewoverlay.s5

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors

class SViewService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    // Обработчик для безопасного управления окнами из фонового потока
    private val mainHandler = Handler(Looper.getMainLooper())


    // Переменные для опроса датчика Холла
    @Volatile
    private var isMonitoring = false
    private var sensorThread: Thread? = null
    private var lastCoverClosedState: Boolean? = null

    private lateinit var headsUpController: HeadsUpController

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val callFilter =
            android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callReceiver, callFilter)

        // Сброс HeadsUp.
        headsUpController = HeadsUpController()
        // Запускаем фоновый опрос датчика Холла сразу при старте службы
        startHallSensorMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_SHOW" -> showOverlay()
            "ACTION_HIDE" -> hideOverlay()
            "ACTION_UPDATE_WALLPAPER" -> {
                overlayView?.let { view ->
                    val wallpaperImage =
                        view.findViewById<android.widget.ImageView>(R.id.sview_wallpaper_image)
                    SViewUIHelper.applyWallpaper(this, wallpaperImage, "custom")
                }
            }
        }
        return START_STICKY
    }

    private fun startHallSensorMonitor() {
        if (isMonitoring) return
        isMonitoring = true

        sensorThread = Thread {
            while (isMonitoring) {
                var suProcess: Process? = null
                try {
                    suProcess = Runtime.getRuntime().exec("su")
                    val os = DataOutputStream(suProcess.outputStream)
                    val reader = BufferedReader(InputStreamReader(suProcess.inputStream))

                    os.writeBytes("setenforce 0\n")
                    os.writeBytes("grep -l 'SW  (0005):' /sys/class/input/event*/device/capabilities/sw 2>/dev/null | head -n 1\n")
                    os.writeBytes("echo '---DEV_END---'\n")
                    os.flush()

                    var devLine: String?
                    var foundEventPath = "/dev/input/event13"
                    while (reader.readLine().also { devLine = it } != null) {
                        if (devLine == "---DEV_END---") break
                        val clean = devLine?.trim() ?: ""
                        if (clean.contains("event")) {
                            val eventName = clean.substringAfter("/sys/class/input/").substringBefore("/")
                            if (eventName.startsWith("event")) {
                                foundEventPath = "/dev/input/$eventName"
                            }
                        }
                    }

                    while (isMonitoring) {
                        os.writeBytes("getevent -p $foundEventPath | grep 'SW  (0005):'\n")
                        os.writeBytes("echo '---END---'\n")
                        os.flush()

                        var line: String?
                        var rawResult = ""
                        var streamClosed = false
                        while (true) {
                            line = reader.readLine()
                            if (line == null) {
                                streamClosed = true
                                break
                            }
                            if (line == "---END---") break
                            rawResult += line
                        }
                        if (streamClosed) break // Выходим из внутреннего цикла для перезапуска su

                        if (rawResult.isNotEmpty()) {
                            val isClosed = rawResult.contains("*")
                            if (isClosed != lastCoverClosedState) {
                                lastCoverClosedState = isClosed
                                if (isClosed) {
                                    os.writeBytes("echo 'glove_mode,1' > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result > /dev/null\n")
                                    os.writeBytes("settings put system high_touch_sensitivity 1\n")
                                    os.flush()
                                    mainHandler.post { showOverlay() }
                                } else {
                                    os.writeBytes("echo 'glove_mode,0' > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result > /dev/null\n")
                                    os.writeBytes("settings put system high_touch_sensitivity 0\n")
                                    os.flush()
                                    mainHandler.post { hideOverlay() }
                                }
                            }
                        }
                        Thread.sleep(200)
                    }

                    os.writeBytes("exit\n")
                    os.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { suProcess?.destroy() } catch (_: Exception) {}
                }

                // Если поток прервался, Но сервис работает, То ждём и перезапускаем поток.
                if (isMonitoring) {
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                }
            }
        }.apply { start() }
    }

    private class HeadsUpController {
        private val executor = Executors.newSingleThreadExecutor()

        @Volatile
        private var previousValue: String? = null

        @Volatile
        private var isSuppressedByUs = false

        fun suppress() {
            if (isSuppressedByUs) return
            executor.execute {
                try {
                    // Читаем текущее значение до изменения
                    val current = runSuRead("settings get global heads_up_notifications_enabled")
                    if (current != "0") {
                        previousValue = if (current == "1" || current == "0") current else "1"
                        if (runSuWrite("settings put global heads_up_notifications_enabled 0")) {
                            isSuppressedByUs = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SViewService", "Failed to suppress heads-up", e)
                }
            }
        }

        fun restore() {
            if (!isSuppressedByUs) return
            executor.execute {
                try {
                    val target = previousValue ?: "1"
                    if (runSuWrite("settings put global heads_up_notifications_enabled $target")) {
                        isSuppressedByUs = false
                        previousValue = null
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SViewService", "Failed to restore heads-up", e)
                }
            }
        }

        fun shutdown() {
            // Если сервис останавливается — пробуем вернуть значение синхронно до закрытия executor
            if (isSuppressedByUs) {
                try {
                    val target = previousValue ?: "1"
                    runSuWrite("settings put global heads_up_notifications_enabled $target")
                    isSuppressedByUs = false
                    previousValue = null
                } catch (_: Exception) {
                }
            }
            executor.shutdown()
        }

        private fun runSuRead(cmd: String): String? {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            return if (exitCode == 0 && output.isNotEmpty() && output != "null") output else null
        }

        private fun runSuWrite(cmd: String): Boolean {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()
            return exitCode == 0
        }
    }


    @Suppress("DEPRECATION")
    private fun showOverlay() {
        headsUpController.suppress() // Не показываем всплывашки при активном Overlay.
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager

        // Если телефон на экране блокировки - Запустить SViewLockService
        if (keyguardManager.isKeyguardLocked) {
            val lockIntent = Intent(this, SViewLockService::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(lockIntent)
            return
        }

        // Если телефон разблокирован - Запустить WindowManager
        if (overlayView != null) return

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_sview, FrameLayout(this), false)

        view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )

        SViewUIHelper.setupUI(this, view) {
            hideOverlay()
        }

        overlayView = view
        windowManager.addView(overlayView, params)
    }

    private fun hideOverlay() {
        headsUpController.restore() // Показываем всплывашки, После выхода Overlay.
        // Закрытие SViewLockService.
        sendBroadcast(Intent("com.sviewoverlay.s5.CLOSE_LOCK_ACTIVITY").setPackage(packageName))

        // Так-же закрытие обычного WindowManager.
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }

    companion object {
        var currentCallerNumber: String = ""
        var isRinging: Boolean = false
        var isOffhook: Boolean = false
        var callStartTime: Long = 0L // <-- Запоминаем время начала беседы
        var onCallStateChanged: (() -> Unit)? = null
    }


    @Suppress("DEPRECATION")
    private val callReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)

            @Suppress("DEPRECATION")
            val incomingNumber =
                intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (state) {
                android.telephony.TelephonyManager.EXTRA_STATE_RINGING -> {
                    isRinging = true
                    isOffhook = false
                    if (!incomingNumber.isNullOrEmpty()) {
                        currentCallerNumber = incomingNumber
                    }
                    if (lastCoverClosedState == true) {
                        mainHandler.post {
                            showOverlay()
                            onCallStateChanged?.invoke()
                        }
                    }
                }

                android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    isRinging = false
                    if (!isOffhook) {
                        isOffhook = true
                        // Засекаем точное системное время начала разговора
                        callStartTime = android.os.SystemClock.elapsedRealtime()
                    }
                    if (!incomingNumber.isNullOrEmpty()) {
                        currentCallerNumber = incomingNumber
                    }
                    if (lastCoverClosedState == true) {
                        mainHandler.post {
                            showOverlay()
                            onCallStateChanged?.invoke()
                        }
                    }
                }

                android.telephony.TelephonyManager.EXTRA_STATE_IDLE -> {
                    isRinging = false
                    isOffhook = false
                    callStartTime = 0L // Сбрасываем таймер после завершения звонка
                    currentCallerNumber = ""
                    mainHandler.post { onCallStateChanged?.invoke() }
                }

            }
        }
    }


    private fun startForegroundServiceNotification() {
        val channelId = "sview_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "S-View Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("SView Overlay Активен")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callReceiver)
        isMonitoring = false
        sensorThread?.interrupt()
        headsUpController.shutdown()
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}