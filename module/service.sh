#!/system/bin/sh
# Wait for system boot
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 2
done

PKG="com.sviewoverlay.s5"
LISTENER="$PKG/$PKG.SViewNotificationService"
ROOT_UID=$(dumpsys package $PKG | grep -m1 "userId=" | sed 's/.*userId=//' | tr -dc '0-9')

sleep 3


if [ -n "$ROOT_UID" ]; then
  # Magisk auto-grant
  if command -v magisk >/dev/null 2>&1; then
    magisk --sqlite "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($ROOT_UID, 2, 0, 1, 0);" 2>/dev/null
    magisk --sqlite "INSERT OR REPLACE INTO policies (uid, package_name, policy, until, logging, notification) VALUES ($ROOT_UID, '$PKG', 2, 0, 1, 0);" 2>/dev/null
  fi

  # APatch auto-grant
  AP_CONF="/data/adb/ap/package_config"
  if [ -f "$AP_CONF" ] && ! grep -q "^$PKG," "$AP_CONF"; then
    echo "$PKG,0,1,$ROOT_UID,0,u:r:magisk:s0" >> "$AP_CONF"
  fi
fi

# Grant permissions
appops set $PKG SYSTEM_ALERT_WINDOW allow
appops set $PKG WRITE_SETTINGS allow
pm grant $PKG android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
pm grant $PKG android.permission.READ_PHONE_STATE 2>/dev/null
pm grant $PKG android.permission.READ_CALL_LOG 2>/dev/null
pm grant $PKG android.permission.READ_CONTACTS 2>/dev/null
pm grant $PKG android.permission.ANSWER_PHONE_CALLS 2>/dev/null

# Grant to listen notifications
cmd notification allow_listener "$LISTENER" 2>/dev/null
CURRENT=$(settings get secure enabled_notification_listeners)
case "$CURRENT" in
  *"$LISTENER"*) ;;
  "null"|"") settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) settings put secure enabled_notification_listeners "$CURRENT:$LISTENER" ;;
esac

# Launch overlay service
am stopservice -n "$PKG/$PKG.SViewService" 2>/dev/null
am start-foreground-service -n "$PKG/$PKG.SViewService" 2>/dev/null || am startservice -n "$PKG/$PKG.SViewService" 2>/dev/null
