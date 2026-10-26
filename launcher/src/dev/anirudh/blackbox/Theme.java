package dev.anirudh.blackbox;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Minimal-black design tokens. Change these first when making the UI your own. */
final class Theme {
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF000000;
    static final int CARD_FOCUS = 0xFF000000;
    static final int BORDER = 0xFF202020;
    static final int BORDER_FOCUS = 0xFFFFFFFF;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFF8A8A8A;
    static final int TEXT_FAINT = 0xFF5C5C5C;
    static final int WHITE = 0xFFFFFFFF;
    static final int BLACK = 0xFF000000;

    private Theme() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable card(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? CARD_FOCUS : CARD);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, focused ? 2 : 1), focused ? BORDER_FOCUS : BORDER);
        return drawable;
    }

    static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable pill(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? WHITE : 0x00000000);
        drawable.setCornerRadius(dp(context, 22));
        drawable.setStroke(dp(context, 1), focused ? WHITE : BORDER);
        return drawable;
    }

    static GradientDrawable row(Context context, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? 0x14FFFFFF : 0x00000000);
        drawable.setCornerRadius(dp(context, 6));
        drawable.setStroke(dp(context, 1), focused ? 0x66FFFFFF : BORDER);
        return drawable;
    }

    static TextView text(Context context, float sp, int color, String family) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }
}
