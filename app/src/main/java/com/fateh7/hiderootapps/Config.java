package com.fateh7.hiderootapps;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The set of package names to hide from scoped apps.
 * Edit this list and rebuild to add or remove entries.
 */
public final class Config {

    private Config() {}

    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
        // --- Magisk ---
        // NOTE: after "Hide the Magisk app", Magisk uses a RANDOM package name,
        // which cannot be matched here by name. Use DenyList/Shamiko for that.
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",

        // --- KernelSU / APatch ---
        "me.weishu.kernelsu",
        "me.bmax.apatch",

        // --- Classic superuser / su providers ---
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingouser.com",

        // --- Xposed / LSPosed frameworks and managers ---
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "io.va.exposed",
        "com.saurik.substrate",

        // --- Root cloaking / hiding tools ---
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "com.formyhm.hideroot",
        "com.formyhm.hiddenroot",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.amphoras.hidemyrootadfree",

        // --- Lucky Patcher and known aliases ---
        "com.chelpus.lackypatch",
        "com.dimonvideo.luckypatcher",
        "com.forpda.lp",
        "com.android.vending.billing.InAppBillingService.LUCK",
        "com.android.vending.billing.InAppBillingService.LACK",
        "com.android.vending.billing.InAppBillingService.COIN",
        "com.android.vending.billing.InAppBillingService.CLON",

        // --- Busybox front-ends ---
        "stericson.busybox",
        "burrows.apps.busybox"
    ));

    public static boolean isHidden(String pkg) {
        return pkg != null && HIDDEN.contains(pkg);
    }

    /** Built-in defaults, merged with the user's templates at runtime. */
    public static Set<String> defaults() {
        return HIDDEN;
    }
}
