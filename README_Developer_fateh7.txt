hide_root fateh7 — modified HMA-OSS (LSPosed variant)

WHAT THIS IS
  The single app APK is BOTH the manager app and the LSPosed module.
  Install the APK, enable it in LSPosed Manager, reboot. No separate zip.

ALREADY DONE
  - Rebranded: applicationId com.developer.fateh7, label Developer_fateh7
    (edit build.gradle.kts -> appId, and res/values/strings.xml -> app_name)
  - New feature "Invalidate inline hooks" (Settings -> Service -> row ->
    app picker -> cleans libart.so for the selected apps)
  - Offline-safe git so it builds in CI / Codespace.

BUILD ONLINE (easiest)
  Push this folder to a GitHub repo -> Actions -> download artifact
  "Developer_fateh7-app".

BUILD LOCALLY
  bash SETUP_AND_BUILD.sh
