#!/usr/bin/env bash
set -e

# Достаём версию пакета из build.gradle.kts
VER_NAME=$(grep -E 'versionName\s*=' app/build.gradle.kts | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')
VER_CODE=$(grep -E 'versionCode\s*=' app/build.gradle.kts | head -n 1 | sed -E 's/[^0-9]//g')

# Запасные значения
VER_NAME=${VER_NAME:-"1.0"}
VER_CODE=${VER_CODE:-"1"}

echo "- Версия пакета: v${VER_NAME} (versionCode: ${VER_CODE})"

# Обновляем значение версии в module.prop
sed -i -E "s/^version=.*/version=v${VER_NAME}/" module/module.prop
sed -i -E "s/^versionCode=.*/versionCode=${VER_CODE}/" module/module.prop

echo "- Сборка Release APK"
./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
fi

if [ ! -f "$APK_PATH" ]; then
    echo "Ошибка: Release APK не найден!"
    exit 1
fi

echo "- Копирование APK В папку модуля"
rm -rf module/system/priv-app/SViewOverlay
mkdir -p module/system/priv-app/SViewOverlay
cp "$APK_PATH" "module/system/priv-app/SViewOverlay/SViewOverlay-${VER_NAME}.apk"

echo "- Упаковка модуля в ZIP."
mkdir -p dist
ARCHIVE_NAME="SView-Overlay-v${VER_NAME}-Module.zip"
rm -f "dist/${ARCHIVE_NAME}"

cd module
zip -r "../dist/${ARCHIVE_NAME}" META-INF module.prop customize.sh service.sh system
cd ..

echo " - Модуль в: dist/${ARCHIVE_NAME}"
