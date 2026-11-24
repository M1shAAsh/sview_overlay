<div align="center">
  <img src="app_icon.png" width="128" height="128" alt="S-View Overlay Icon" />
  <h1>SView Overlay</h1>
<p>More interesting alternative of FlipFlap, With design from TouchWiz.</p>
</div>

## Features.
* **Hardware:** Overlay can be shown, Only when cover is closed. Also, It has to autodetect a hall sensor.
* **Tap through window!** To be able tapping, It used system setting (high touch sensitivity), And that setting working only with overlay showing (after, setting is disabled).
* **Showing on lockscreen.** Overlay also can be shown when phone is unlock, Anв while on lockscreen.
* **Calling window:** If you on Android 9 (full compatibility), You can to answer calls, View to contact name and picture (if it doesn't, show number and placeholder image), Also you can mute microphone and turn to other speaker.
* **Notifications:** You can view notifications, While your cover is close. Also, you can clean them, By touching.
* **Music player:** Control music (pause/play, switch to other audio), And view album art.
* **Customization:**
  - Change wallpapers from bundled one, And your own from device!
  * Change fonts from bundled, Or select .ttf/.otf yours.
  * Also, You can change a width/height and position of window, if not fit.
* **Launch after boot device:** Automatic start of service, After device boot (`BOOT_COMPLETED`).
## Screenshots.
<img width="360" height="640" alt="photo_2026-11-24_19-45-09" src="https://github.com/user-attachments/assets/6652bad1-ad0b-442f-bbfe-22d0fda90cdb" />
<img width="360" height="640" alt="photo_2026-11-24_19-45-10" src="https://github.com/user-attachments/assets/fadc3915-cd42-4a20-8944-610213975c92" />


## Requirements.
* **OS:** Android 7.1-8.1: Call window is limited (only root reject call).
* Android 9+: **Full Functionality.**
* **Root Access:** Need for getevent (show overlay) and set "high touch sensitivity" (tapping through window).
* **Permissions to Overlay:**
  * On other windows (`SYSTEM_ALERT_WINDOW`).
  * Access to notifications, and music  (`NotificationListenerService`).
  * Phone, Contacts - For to call window working, To your notice.

## Configuration on other models (Note4+, S5+).
* **Hall Sensor** Detect automatically after service launch (if not, go to `/dev/input/event13`).
* **Window Geometry** - For change geometry, You can change from overlay settings, Or edit `/sdcard/Android/data/com.sviewoverlay.s5/files/user_config.json`.
* **Your Fonts** - You can place .ttf/.otf in `/sdcard/Android/data/com.sviewoverlay.s5/files/fonts/`, or through overlay menu.

## Disclaimer.
AI assistance was used during the development of this project. This is a non-commercial fan project created to restore stock S-View Look on custom ROMs. Some of the resources belong to **Samsung Electronics Co., Ltd.**
