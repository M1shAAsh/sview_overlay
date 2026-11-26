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

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val callFilter =
            android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callReceiver, callFilter)

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
            try {
                // Запускаем сессию SU.
                val suProcess = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(suProcess.outputStream)
                val reader = BufferedReader(InputStreamReader(suProcess.inputStream))

                // Переводим SELinux в Permissive.
                os.writeBytes("setenforce 0\n")
                os.flush()

                // Поиск SW_LID.
                os.writeBytes("grep -l 'SW  (0005):' /sys/class/input/event*/device/capabilities/sw 2>/dev/null | head -n 1\n")
                os.writeBytes("echo '---DEV_END---'\n")
                os.flush()

                var devLine: String?
                var foundEventPath = "/dev/input/event13" // Резервный путь к сенсору.
                while (reader.readLine().also { devLine = it } != null) {
                    if (devLine == "---DEV_END---") break
                    val clean = devLine?.trim() ?: ""
                    if (clean.contains("event")) {
                        // Извлекаем путь сенсора.
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
                    while (reader.readLine().also { line = it } != null) {
                        if (line == "---END---") break
                        rawResult += line
                    }

                    // Если есть полученное значение, Проверяем статус (event*)
                    if (rawResult.isNotEmpty()) {
                        val isClosed = rawResult.contains("*")

                        // Внутри цикла while (isMonitoring) в методе startHallSensorMonitor():
                        if (isClosed != lastCoverClosedState) {
                            lastCoverClosedState = isClosed
                            val stateInt = if (isClosed) 1 else 0

                            // Сообщаем контроллеру дисплея, О повышенной чуствительности.
                            os.writeBytes("echo 'glove_mode,$stateInt' > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result > /dev/null\n")
                            os.flush()

                            // Теперь сообщаем об этом системе, Через API.
                            try {
                                android.provider.Settings.System.putInt(
                                    contentResolver,
                                    "high_touch_sensitivity",
                                    stateInt
                                )
                            } catch (_: Exception) {
                                os.writeBytes("settings put system high_touch_sensitivity $stateInt\n")
                                os.flush()
                            }

                            if (isClosed) {
                                mainHandler.post { showOverlay() }
                            } else {
                                mainHandler.post { hideOverlay() }
                            }
                        }
                    }

                    // Пауза для отдыха процессора.
                    Thread.sleep(200)
                }

                os.writeBytes("exit\n")
                os.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.apply { start() }
    }

    private fun setHeadsUpEnabled(enabled: Boolean) {
        val value = if (enabled) 1 else 0
        try {
            android.provider.Settings.Global.putInt(
                contentResolver,
                "heads_up_notifications_enabled",
                value
            )
        } catch (_: Exception) {
            // Запасной запрет, Если записать настройку не удалось.
            Thread {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "settings put global heads_up_notifications_enabled $value")
                    ).waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }



    @Suppress("DEPRECATION")
    private fun showOverlay() {
        setHeadsUpEnabled(false) // Не показываем всплывашки при активном Overlay.
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
        setHeadsUpEnabled(true) // Показываем всплывашки, После выхода Overlay.
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
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}