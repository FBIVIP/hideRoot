#!/system/bin/sh
# RootHider install script

if [ "$ARCH" != "arm64" ] && [ "$ARCH" != "arm" ]; then
  ui_print "! Unsupported ABI: $ARCH (arm64 / arm only)"
  abort   "! Aborting."
fi

# Require Zygisk to be enabled.
ui_print "- Make sure Zygisk is ENABLED in Magisk settings."

# Seed the runtime target list (do not overwrite an existing one).
TARGET_DIR=/data/adb/roothider
if [ ! -f "$TARGET_DIR/target.txt" ]; then
  mkdir -p "$TARGET_DIR"
  cat > "$TARGET_DIR/target.txt" << 'LIST'
# One target package per line. Lines starting with # are ignored.
# Example:
# com.target.app
LIST
  chmod 0600 "$TARGET_DIR/target.txt"
  ui_print "- Seeded $TARGET_DIR/target.txt (edit it to add your targets)."
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644

ui_print "- For a complete setup, also install ONE unmount/hider:"
ui_print "    * ReZygisk + NoHello  (2026 recommended), OR"
ui_print "    * Zygisk Next / Zygisk Assistant"
ui_print "  plus PlayIntegrityFix (attestation)."
ui_print "  Add your targets to the Magisk/KSU DenyList too."
ui_print "- Note: do NOT mix Shamiko with ReZygisk."
ui_print "- Done. Reboot to activate."
