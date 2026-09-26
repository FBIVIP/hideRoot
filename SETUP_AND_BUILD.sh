#!/usr/bin/env bash
# Developer_fateh7 — full modified HMA-OSS
# Fetches the missing submodule, installs the SDK, and builds.
# Run from THIS folder:  bash SETUP_AND_BUILD.sh
set -e
cd "$(dirname "$0")"

echo "==> [1/4] AndroidVMTools submodule"
if [ ! -f external/AndroidVMTools/gradle/libs.versions.toml ]; then
  rm -rf external/AndroidVMTools
  git clone --depth 1 --recurse-submodules \
    https://github.com/aerath-stuff/AndroidVMTools external/AndroidVMTools
fi
ls external/AndroidVMTools/gradle/libs.versions.toml

echo "==> [2/4] Android SDK"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -L -o /tmp/ct.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -qo /tmp/ct.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "==> [3/4] Build config"
printf 'officialBuild=true\nlocalBuild=true\nsdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew

echo "==> [4/4] Building"
./gradlew :app:assembleRelease --no-daemon --stacktrace
./gradlew :zygote:assembleRelease --no-daemon --stacktrace \
  || echo "   (zygote step failed — the manager APK is still built)"

echo ""
echo "==================== OUTPUTS ===================="
find app/build/outputs/apk -name '*.apk' 2>/dev/null || true
find zygote/build/outputs -type f \( -name '*.zip' -o -name '*.apk' \) 2>/dev/null || true
echo "================================================"
