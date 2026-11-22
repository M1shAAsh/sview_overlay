package com.sviewoverlay.s5

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SViewNotificationService : NotificationListenerService() {

    data class NotifItem(
        val key: String,
        val title: String,
        val text: String
    )


    companion object {
        val notifList = mutableListOf<NotifItem>()
        var onNotificationsUpdated: (() -> Unit)? = null
        var instance: SViewNotificationService? = null

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshAllNotifications()
    }


    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshAllNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }


    private fun refreshAllNotifications() {
        val currentList = mutableListOf<NotifItem>()
        try {
            val rankingMap = currentRanking
            val ranking = Ranking()

            for (sbn in activeNotifications) {
                // 1. Базовая фильтрация: неубираемые и наши собственные
                if (!sbn.isClearable || sbn.packageName == packageName) continue
                if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue

                // 2. Отсеиваем системные службы, которые любят оставлять невидимые хвосты
                val pkg = sbn.packageName
                if (pkg == "com.android.providers.downloads" ||
                    pkg == "com.android.systemui" ||
                    pkg == "android"
                ) continue

                // 3. Проверка ранга и визуальной видимости (Android 8/9)
                if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) {
                    // Отсеиваем всё, что ниже стандартного приоритета или скрыто системой
                    if (ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) continue
                    if (ranking.isAmbient) continue
                    // Если система подавила визуальное отображение уведомления (например, в шторке оно скрыто)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        if ((ranking.suppressedVisualEffects and android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST) != 0) continue
                    }

                }

                // 4. Проверка времени: игнорируем уведомления с нулевым временем (системные заглушки)
                if (sbn.notification.`when` == 0L && sbn.postTime == 0L) continue

                val extras = sbn.notification.extras
                val title =
                    extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

                // Игнорируем карточки, где заголовок и текст совпадают или пусты (часто бывает у фоновых сервисов)
                if (title.isEmpty() && text.isEmpty()) continue

                currentList.add(NotifItem(sbn.key, title, text))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        notifList.clear()
        notifList.addAll(currentList)
        onNotificationsUpdated?.invoke()
    }
}
