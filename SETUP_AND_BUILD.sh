#!/usr/bin/env bash
# Developer_fateh7 (LSPosed variant) — builds the single APK
# (it is BOTH the manager app AND the LSPosed module).
# Run from this folder:  bash SETUP_AND_BUILD.sh
set -e
cd "$(dirname "$0")"

echo "==> Android SDK"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -L -o /tmp/ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -qo /tmp/ct.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "==> build config"
printf 'officialBuild=true\nlocalBuild=true\nsdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew

echo "==> building"
./gradlew :app:assembleRelease --no-daemon --stacktrace

echo ""
echo "==================== OUTPUT ===================="
find app/build/outputs/apk -name '*.apk' -print 2>/dev/null || true
echo "==============================================="
echo "Install this APK and activate it in LSPosed, then reboot."
