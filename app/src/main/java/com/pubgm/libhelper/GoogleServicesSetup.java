package com.pubgm.libhelper;

import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/** Best-effort setup for an existing, licensed host Google Play services installation.
 * Does not download, sideload, or bypass Google Play services signature checks.
 */
public final class GoogleServicesSetup {
    private static final String TAG = "LoaderGms";
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private GoogleServicesSetup() {}

    public static void installIfAvailable() {
        if (!STARTED.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                BlackBoxCore box = BlackBoxCore.get();
                if (!box.isSupportGms()) {
                    Log.i(TAG, "Host Google Play services not present; skipping virtual setup.");
                    return;
                }
                if (box.isInstallGms(0)) {
                    Log.i(TAG, "Google Play services already installed in virtual user 0.");
                    return;
                }
                InstallResult result = box.installGms(0);
                if (result != null && result.success && box.isInstallGms(0)) {
                    Log.i(TAG, "Virtual GMS packages installed (auth must be tested separately).");
                } else {
                    Log.w(TAG, "Virtual GMS unavailable: " + (result == null ? "no result" : result.msg));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Virtual GMS setup failed; Google sign-in may still work on host.", t);
            }
        }, "virtual-gms-setup").start();
    }
}
