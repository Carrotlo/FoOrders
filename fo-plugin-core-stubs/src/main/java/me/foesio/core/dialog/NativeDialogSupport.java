package me.foesio.core.dialog;

import org.bukkit.plugin.Plugin;

public class NativeDialogSupport {
    NativeDialogSupport() {}

    public static NativeDialogSupport detect(Plugin plugin, NativeDialogSettings settings) {
        return new NativeDialogSupport();
    }

    public boolean configEnabled() {
        return false;
    }

    public boolean canUseNativeDialogs() {
        return false;
    }

    public boolean serverSupportsNativeDialogs() {
        return false;
    }

    public boolean runtimeDisabled() {
        return true;
    }

    public String runtimeUnavailableReason() {
        return "stub";
    }

    public String unavailableReason() {
        return "stub";
    }

    public boolean warnOnFallback() {
        return false;
    }

    public void disableForSession(String reason) {}
}
