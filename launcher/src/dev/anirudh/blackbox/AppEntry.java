package dev.anirudh.blackbox;

import android.graphics.drawable.Drawable;

/** One launchable app and the artwork Blackbox shows for it. */
final class AppEntry {
    final String pkg;
    final String label;
    final Drawable art;
    final boolean banner;

    AppEntry(String pkg, String label, Drawable art, boolean banner) {
        this.pkg = pkg;
        this.label = label;
        this.art = art;
        this.banner = banner;
    }
}
