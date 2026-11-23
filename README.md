<div align="center">
  <img src="app_icon.png" width="128" height="128" alt="S-View Overlay Icon" />
  <h1>SView Overlay</h1>
<p>More interesting alternative of FlipFlap, With design from TouchWiz.</p>
</div>

## Features
* **Hardware:** Overlay can be showed, Only when cover is closed. Thanks to the Hall sensor.
* **Tap through window!** To able tapping, I used a system setting (high touch sensitivity), And that setting working only with overlay showing (after, setting is disabled).
* **Showing on lockscreen.** Overlay also can be showed when phone is unlock, Anв while on lockscreen.
* **Calling window:** If you on Android 9 (full compatibility), You can to answer calls, View to contact name and picture (if doesnt, show number and placeholder image), Also you can mute microphone and turn to other speaker
* **Notifications:** You can view notifications, While your cover is close. Also you can clean them, By touching.
* **Customization:**
  - Change wallpapers from bundled one, And your own from device!
  * Change fonts from bundled, Or select .ttf/.otf yours.
  * Also, You can change a width/height and position of window, if default is not will fit.
* **Launch after boot device:** Automatic start of service, After device boot (`BOOT_COMPLETED`).

## Требования
* **OS:** Android 7.1 – 9.0+ ;
* **Root Access:** Need for getevent (show overlay) and set "high touch sensitivity" (tapping through window).
* **Permissions to Overlay:**
  * On other windows (`SYSTEM_ALERT_WINDOW`).
  * Access to notifications (`NotificationListenerService`).
  * Calling, Contacts - For to call window working, To your notice.

## Configuration on other models (Note4+, S5+)
* **Hall Sensor** On default read `/dev/input/event13` (Compatible for KLTE Galaxy S5). For other models, Check from command `getevent -pl`, And update `SViewService.kt`.
* **Window Geometry** - For change geometry, You can change from overlay settings, Or edit `/sdcard/Android/data/com.sviewoverlay.s5/files/user_config.json`.
* **Your Fonts** - You can place .ttf/.otf in `/sdcard/Android/data/com.sviewoverlay.s5/files/fonts/`, or through overlay menu.

## Disclaimer.
This project was created to restore functionality and appearance, that was present in the stock firmware. Most pictures from that project belongs to **Samsung Electronics Co., Ltd.**
