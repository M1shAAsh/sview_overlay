#!/system/bin/sh
# Wait for system boot
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 2
done

PKG="com.sviewoverlay.s5"
LISTENER="$PKG/$PKG.SViewNotificationService"

sleep 3

# Grant permissions
appops set $PKG SYSTEM_ALERT_WINDOW allow
appops set $PKG WRITE_SETTINGS allow 2>/dev/null
pm grant $PKG android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
pm grant $PKG android.permission.READ_PHONE_STATE 2>/dev/null
pm grant $PKG android.permission.READ_CALL_LOG 2>/dev/null
pm grant $PKG android.permission.READ_CONTACTS 2>/dev/null
pm grant $PKG android.permission.ANSWER_PHONE_CALLS 2>/dev/null

# Grant to listen notifications
CURRENT=$(settings get secure enabled_notification_listeners)
case "$CURRENT" in
  *"$LISTENER"*) ;;
  "null"|"") settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) settings put Dsecure enabled_notification_listeners "$CURRENT:$LISTENER" ;;
esac

# Launch overlay service
am stopservice -n "$PKG/$PKG.SViewService" 2>/dev/null
am start-foreground-service -n "$PKG/$PKG.SViewService" 2>/dev/null || am startservice -n "$PKG/$PKG.SViewService" 2>/dev/null
