# RootHider Suite

Root-hiding for a device **you own**, driven from **one app**.

- **RootHider app (one APK)** — this is BOTH the LSPosed module AND the home
  page you manage everything from (`lsposed/`). It hides root, hides apps you
  choose from a target, and adds **anti-hook-detection**.
- **RootHider Zygisk module** — the native layer (`zygisk/`), flashed once in
  the background. It reads its target list from a file the app writes.

You install the app once and flash the Zygisk module once; after that you work
only from the app's home page.

## What each layer does

**App / LSPosed layer** (`lsposed/RootHiderHook.java`): File/Runtime/Process,
PackageManager (root + chosen-app hiding), SystemProperties incl. **locked-
bootloader spoof**, Settings, `/proc` line filtering, Debug/SELinux, and
**anti-hook-detection** (scrubs stack traces and blocks `Class.forName` for
Xposed/LSPosed/Frida detector classes).

**Zygisk layer** (`zygisk/jni/module.cpp`): libc hooks
(access/stat/open/openat/fopen/readlinkat/statfs/`syscall`), scrubbed
`/proc/self/{maps,mounts,mountinfo,status}`, locked-bootloader props,
self-hide, and **auto re-hook of libraries loaded later** via a `dlopen` hook.

## Build (GitHub Actions)

Push to GitHub. Two artifacts are produced:
- **RootHider-LSPosed-APK** — the single manager app.
- **RootHider-Zygisk** — the flashable native module.

## Install (once)

1. Magisk → enable **Zygisk** → reboot.
2. Flash **RootHider-Zygisk** → reboot.
3. Install **RootHider-LSPosed-APK**, open the **LSPosed manager**, enable the
   module, and **scope it to your target app(s)**.
4. Open the **RootHider app** → add your protected apps + apps to hide →
   **Save & Apply** (grant root when asked) → force-stop the target app.

## Complete the setup

- One unmount/hider (ReZygisk + NoHello, or Zygisk Next) for memory-based
  Zygisk detection. Do not mix Shamiko with ReZygisk.
- PlayIntegrityFix for attestation.

## Honest limits

Inline `svc` syscalls (need seccomp), memory-based Zygisk detection (the
unmounter's job), and Play Integrity STRONG (hardware-backed) are not solved
by hooks. This is strong against most on-device checks, not a guarantee.

## Note on XSharedPreferences

The app saves your "hide" list to its own prefs; the LSPosed module reads it
via XSharedPreferences (that is why `xposedminversion` is 93). The Zygisk
target list is written to `/data/adb/roothider/target.txt` with root.
