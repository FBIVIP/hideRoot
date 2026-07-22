# Hide Root — LSPosed module + app

A root/tamper-hiding LSPosed module with a full in-app UI. For each app you
protect, it hides root packages, root file paths & shell commands, spoofs
"clean" Settings values, and can make the bootloader look locked.

## App screens

- **Manage Templates** — create named templates (e.g. "hide root"); each is a
  set of root apps to hide. Their union is the effective hide-list.
- **Manage Apps** — pick the detector apps to protect. Each app has switches:
  Protect, Bootloader-locked, Spoof-settings.
- **My Account** — developer info + Telegram (@fateh7).

## Build

Easiest: push to GitHub and let the included Actions workflow build it
(`.github/workflows/build.yml`) → download the `HideRootApps-debug` artifact.

Locally:
```bash
gradle wrapper --gradle-version 8.2
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Install & use

1. Install the APK, then enable the module in **LSPosed → Modules**.
2. In the module's **Scope**, tick the same detector apps you will protect.
3. Open the app: create a template, then in **Manage Apps** protect the
   detector apps and set their switches.
4. **Force-stop** the detector apps (or reboot) so hooks reload the new config.

The app stores its config with `MODE_WORLD_READABLE`; LSPosed exposes it to the
hook via `XSharedPreferences`. If you change settings, force-stop the target app
so it re-reads them.

If no app is protected yet, the module applies defaults to every scoped app so
it still works out of the box.

## What it covers / doesn't

Covers the **Java layer**: `PackageManager`, `java.io.File`, `Runtime.exec`,
`ProcessBuilder`, `Settings.*`, `SystemProperties`, `Build.TAGS/TYPE`.

Does **not** cover native (JNI) checks — `access`/`stat`/`open` called from C,
or `/proc` parsing in native code. Those need a **Zygisk** native module and
are complementary to **DenyList + Shamiko**.

## Tuning (edit + rebuild)

- `Config.java` — built-in default packages to hide.
- `PathConfig.java` — root file paths, keywords, and shell commands.
- `SpoofConfig.java` — Settings values + bootloader/verified-boot properties.
