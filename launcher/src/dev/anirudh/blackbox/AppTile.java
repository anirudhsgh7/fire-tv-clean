package dev.anirudh.blackbox;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A D-pad focusable app card: artwork on top, label underneath. */
final class AppTile extends LinearLayout {
    private final TextView label;
    private final boolean animate;
    private final int normalHeight;

    AppTile(Context context, AppEntry entry, int width, int height,
            boolean animate, final OnClickListener click) {
        super(context);
        this.animate = animate;
        this.normalHeight = height;
        setOrientation(VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setBackground(Theme.card(context, false));
        setPadding(Theme.dp(context, 6), Theme.dp(context, 6), Theme.dp(context, 6), 0);
        setClipChildren(true);

        ImageView art = new ImageView(context);
        LayoutParams artParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        art.setLayoutParams(artParams);
        art.setScaleType(entry.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        if (!entry.banner) art.setPadding(Theme.dp(context, 20), Theme.dp(context, 8),
                Theme.dp(context, 20), Theme.dp(context, 8));
        art.setImageDrawable(entry.art);
        art.setBackground(Theme.rounded(context, 0x00000000, 6));
        art.setClipToOutline(true);
        addView(art);

        label = Theme.text(context, 12, Theme.TEXT_DIM, "sans-serif");
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(Theme.dp(context, 4), 0, Theme.dp(context, 4), 0);
        label.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 24)));
        addView(label);
        label.setText(entry.label);
        setContentDescription(entry.label);

        setOnClickListener(click);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focused) {
                v.setBackground(Theme.card(context, focused));
                label.setTextColor(focused ? Theme.TEXT : Theme.TEXT_DIM);
                if (animate) {
                    float scale = focused ? 1.04f : 1f;
                    v.animate().scaleX(scale).scaleY(scale).setDuration(100).start();
                } else {
                    v.setScaleX(focused ? 1.04f : 1f);
                    v.setScaleY(focused ? 1.04f : 1f);
                }
            }
        });
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        if (params != null && params.height > 0) params.height = normalHeight;
    }
}
