Developer_fateh7 — full modified HMA-OSS (ready to build)
========================================================

WHAT IS ALREADY DONE FOR YOU
  - Rebranded: applicationId = com.developer.fateh7, app label = Developer_fateh7
      (change these in build.gradle.kts -> appId, and res/values/strings.xml -> app_name)
  - New feature "Invalidate inline hooks" (Settings -> Service -> row -> app picker
      -> cleans libart.so for the selected apps)
  - Build fixes baked in so it compiles on a normal SDK:
      * targetSdk/compileSdk = 36 (was preview 37)
      * VERSION_CODES.CINNAMON_BUN replaced by its value 37
      * removed the preview-only "resourcesUnused" configChange
  - git version logic made offline-safe

THE ONLY EXTERNAL THING NEEDED
  external/AndroidVMTools is a git submodule and is NOT inside this copy.
  It is fetched automatically by:
      - SETUP_AND_BUILD.sh (local build), or
      - .github/workflows/build-fateh7.yml (online build)

BUILD ONLINE (easiest)
  1. Create a new GitHub repo and push this whole folder into it.
  2. Open the repo -> Actions tab -> wait for the green run.
  3. Download artifact "Developer_fateh7-build": manager APK + Zygisk module.

BUILD LOCALLY (Codespace / Linux / Termux)
      bash SETUP_AND_BUILD.sh
  Outputs:
      app/build/outputs/apk/release/*.apk      (install on the phone)
      zygote/build/outputs/**                   (flash in Magisk/KernelSU)

NOTE
  Release is signed with the debug key (installable). For your own key, add to
  local.properties: fileDir / storePassword / keyAlias / keyPassword.
