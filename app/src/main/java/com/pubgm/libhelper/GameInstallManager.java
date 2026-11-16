package com.pubgm.libhelper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import com.pubgm.activity.MainActivity;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies ONLY the exact selected game's main/patch OBB to the existing loader's
 * virtual OBB path. Direct Android/obb is attempted first. Android 11+ often
 * restricts that source; a user-selected SAF document is the fallback.
 * No root, privilege escalation or Android/data bypass is attempted.
 */
public final class GameInstallManager {
    private GameInstallManager() {}
    private static final String PICK_PREFIX = "SELECT_OBB:";
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Result { void finished(boolean success, String message); }
    public interface Progress { void changed(int percent); }
    private static void report(Result cb, boolean success, String msg) {
        MAIN.post(() -> cb.finished(success, msg));
    }
    public static boolean needsObbPicker(String resultMessage) {
        return resultMessage != null && resultMessage.startsWith(PICK_PREFIX);
    }
    public static String humanMessage(String msg) {
        return needsObbPicker(msg) ? msg.substring(PICK_PREFIX.length()) : msg;
    }

    public static void installApkOnly(MainActivity activity, String packageName, Result cb) {
        new Thread(() -> {
            try {
                activity.getPackageManager().getPackageInfo(packageName, 0);
                if (ApkEnv.getInstance().isInstalled(packageName)) {
                    report(cb, true, "Selected game is already installed.");
                    return;
                }
                boolean ok = ApkEnv.getInstance().installByPackage(packageName);
                report(cb, ok, ok ? "APK installed for " + packageName : "APK install failed. Check installation.");
            } catch (PackageManager.NameNotFoundException e) {
                report(cb, false, "Install the selected game's official APK on the device first.");
            } catch (Exception e) {
                report(cb, false, "Install error: " + e.getMessage());
            }
        }, "selected-game-apk-install").start();
    }

    private static boolean obbNameMatches(String name, String pkg) {
        return name != null &&
             (name.startsWith("main.") || name.startsWith("patch.")) &&
             name.endsWith("." + pkg + ".obb") && !name.contains("/") && !name.contains("\\");
    }

    public static void copySelectedGameObb(MainActivity activity, String packageName, Result cb,
                                             Progress progress) {
        new Thread(() -> {
            try {
                if (!ApkEnv.getInstance().isInstalled(packageName)) {
                    report(cb, false, "Install the selected game in the loader first.");
                    return;
                }
                File externalRoot = android.os.Environment.getExternalStorageDirectory();
                File folder = new File(externalRoot, "Android/obb/" + packageName);
                File[] sources = null;
                try { sources = folder.listFiles(); }
                catch (SecurityException denied) { /* Android restricts this folder; use picker */ }
                List<File> matching = new ArrayList<>();
                if (sources != null) {
                    for (File f : sources) {
                        if (f.isFile() && f.canRead() && f.length() > 0 && obbNameMatches(f.getName(), packageName)) {
                            // canRead() may be misleading under scoped-storage enforcement.
                            try (InputStream check = new FileInputStream(f)) {
                                matching.add(f);
                            } catch (Exception denied) { /* use SAF fallback instead */ }
                        }
                    }
                }
                if (matching.size() > 0) {
                    long total = 0;
                    for (File f : matching) total += f.length();
                    long done = 0;
                    File targetDir = ensureTarget(packageName);
                    for (File source : matching) {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(source), BUFFER_SIZE)) {
                            done += copyVerified(in, source.getName(), source.length(), targetDir, total, done, progress);
                        }
                    }
                    report(cb, true, "Copied " + matching.size() + " OBB file(s) for " + packageName);
                    return;
                }
                // A prior picker selection is reusable via persistable read permission.
                String saved = activity.getPreferences(Activity.MODE_PRIVATE)
                        .getString("obb_uri_" + packageName, "");
                if (!saved.isEmpty()) {
                    try {
                        copySelectedDocument(activity, packageName, Uri.parse(saved), progress);
                        report(cb, true, "Selected game's saved OBB copied successfully.");
                        return;
                    } catch (Exception e) {
                        activity.getPreferences(Activity.MODE_PRIVATE).edit()
                                .remove("obb_uri_" + packageName).apply();
                    }
                }
                report(cb, false, PICK_PREFIX + "Android/obb/" + packageName
                    + " is missing or blocked by Android. Select this game's OBB file manually.");
            } catch (Exception e) {
                report(cb, false, "OBB copy failed: " + e.getMessage());
            }
        }, "selected-game-obb-copy").start();
    }

    public static void copySelectedGameObb(MainActivity activity, String packageName, Result cb) {
        copySelectedGameObb(activity, packageName, cb, null);
    }

    public static void copyFromUserSelection(MainActivity activity, String pkg, Uri uri,
                                             Result cb, Progress progress) {
        new Thread(() -> {
            try {
                if (!ApkEnv.getInstance().isInstalled(pkg)) {
                    report(cb, false, "Install " + pkg + " first.");
                    return;
                }
                copySelectedDocument(activity, pkg, uri, progress);
                report(cb, true, "OBB copied for " + pkg);
            } catch (Exception e) {
                report(cb, false, "OBB copy failed: " + e.getMessage());
            }
        }, "obb-selected-document-copy").start();
    }

    private static void copySelectedDocument(MainActivity activity, String pkg, Uri uri,
                                              Progress progress) throws Exception {
        String name = null;
        long size = -1;
        try (Cursor c = activity.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        }
        if (!obbNameMatches(name, pkg)) {
            throw new IllegalArgumentException("Wrong game OBB. Choose main/patch.*." + pkg + ".obb");
        }
        if (size == 0) throw new IllegalArgumentException("Selected OBB is empty.");
        File destination = ensureTarget(pkg);
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Cannot open the selected document.");
            copyVerified(in, name, size, destination, size, 0, progress);
        }
    }

    private static File ensureTarget(String pkg) throws Exception {
        File target = ApkEnv.getInstance().getObbContainerPath(pkg);
        if (target == null || (!target.isDirectory() && !target.mkdirs())) {
            throw new IllegalStateException("Cannot create loader OBB folder for " + pkg);
        }
        if (!target.isDirectory() || !target.canWrite()) {
            throw new IllegalStateException("Loader OBB destination is not writable.");
        }
        return target;
    }

    /** Write a temp file, verify bytes, then atomically make the final .obb visible. */
    private static long copyVerified(InputStream input, String name, long expectedSize, File targetDir,
                                     long aggregateTotal, long earlierBytes, Progress progress) throws Exception {
        if (name == null || !name.endsWith(".obb")) throw new IllegalArgumentException("Invalid OBB name.");
        File finalFile = new File(targetDir, name);
        File staging = new File(targetDir, name + ".partial");
        long copied = 0;
        long lastProgressUpdate = 0;
        try {
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(staging, false), BUFFER_SIZE)) {
                byte[] bytes = new byte[BUFFER_SIZE];
                int n;
                while ((n = input.read(bytes)) != -1) {
                    if (n == 0) continue;
                    output.write(bytes, 0, n);
                    copied += n;
                    long now = android.os.SystemClock.uptimeMillis();
                    if (progress != null && aggregateTotal > 0 && now - lastProgressUpdate >= 160) {
                        int percent = (int) Math.min(99, (earlierBytes + copied) * 100 / aggregateTotal);
                        MAIN.post(() -> progress.changed(percent));
                        lastProgressUpdate = now;
                    }
                }
                output.flush();
            }
            if (copied == 0 || (expectedSize >= 0 && copied != expectedSize)) {
                throw new IllegalStateException("OBB data incomplete. Expected " + expectedSize + ", received " + copied);
            }
            if (finalFile.exists() && !finalFile.delete()) {
                throw new IllegalStateException("Could not replace old OBB.");
            }
            if (!staging.renameTo(finalFile)) {
                throw new IllegalStateException("Cannot finalize OBB file.");
            }
            if (progress != null && aggregateTotal > 0) {
                int percent = (int) Math.min(100, (earlierBytes + copied) * 100 / aggregateTotal);
                MAIN.post(() -> progress.changed(percent));
            }
            return copied;
        } finally {
            if (staging.exists()) staging.delete();
        }
    }
}
