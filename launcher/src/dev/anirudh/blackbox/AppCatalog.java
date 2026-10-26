package dev.anirudh.blackbox;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds launchable apps without touching the network. */
final class AppCatalog {
    private static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList(
            "dev.anirudh.blackbox",
            "com.amazon.tv.launcher",
            "com.amazon.tv.settings",
            "com.amazon.tv.settings.v2",
            "com.amazon.tv.settings.core",
            "com.android.settings",
            "com.android.systemui",
            "android"
    ));

    private AppCatalog() {}

    static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        add(pm, found, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
        add(pm, found, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));

        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo resolved : found.values()) {
            ApplicationInfo info = resolved.activityInfo.applicationInfo;
            if (info == null) continue;
            String pkg = info.packageName;
            if (pkg == null || EXCLUDED.contains(pkg)) continue;

            CharSequence label = resolved.loadLabel(pm);
            if (label == null || label.toString().trim().isEmpty()) continue;

            Drawable banner = null;
            try {
                banner = info.loadBanner(pm);
            } catch (Exception ignored) {
                // fall through to the icon
            }
            Drawable art = banner;
            if (art == null) {
                try {
                    art = info.loadIcon(pm);
                } catch (Exception ignored) {
                    art = null;
                }
            }
            apps.add(new AppEntry(pkg, label.toString().trim(), art, banner != null));
        }

        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private static void add(PackageManager pm, Map<String, ResolveInfo> found, Intent intent) {
        for (ResolveInfo resolved : pm.queryIntentActivities(intent, 0)) {
            if (resolved.activityInfo == null) continue;
            String pkg = resolved.activityInfo.packageName;
            if (pkg == null || EXCLUDED.contains(pkg) || found.containsKey(pkg)) continue;
            found.put(pkg, resolved);
        }
    }
}
