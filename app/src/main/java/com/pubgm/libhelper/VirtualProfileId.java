package com.pubgm.libhelper;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** App-local virtual profile metadata. This does NOT spoof device identifiers. */
public final class VirtualProfileId {
    private VirtualProfileId() {}
    private static final String FILE = "virtual_profile_ids";

    public static String get(Context context, String packageName) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        String id = prefs.getString(packageName, null);
        if (id != null) return id;
        return reset(context, packageName);
    }

    public static String reset(Context context, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
            throw new IllegalArgumentException("Invalid package name");
        }
        String id = UUID.randomUUID().toString();
        if (!context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(packageName, id).commit()) {
            throw new IllegalStateException("Profile save failed");
        }
        return id;
    }
}
