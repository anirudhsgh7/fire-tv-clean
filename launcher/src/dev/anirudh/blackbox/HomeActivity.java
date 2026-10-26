package dev.anirudh.blackbox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal black home screen for Fire OS 5. */
public final class HomeActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int GRID_LEFT_INSET_DP = 8;
    private static final int GRID_RIGHT_INSET_DP = 40;
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateDate();
            ui.postDelayed(this, 30000);
        }
    };

    private Config cfg;
    private List<AppEntry> apps;
    private boolean loadingApps;
    private boolean receiverRegistered;
    private String lastConfigKey;

    private FrameLayout root;
    private GridLayout grid;
    private TextClock clock;
    private TextView dateLine;
    private TextView loading;
    private TextView allAppsPill;
    private TextView settingsPill;
    private TextView amazonPill;
    private View overlay;
    private View firstTile;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.load(this);
        buildChrome();
        applyClock();
        updateDate();
        registerPackageReceiver();
        startService(new Intent(this, HomeWatcher.class));
        // Let the clock and empty home draw before the app list work begins.
        grid.postDelayed(this::loadApps, 150);
    }

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Theme.dp(this, 48), Theme.dp(this, 32),
                Theme.dp(this, 48), Theme.dp(this, 18));

        clock = new TextClock(this);
        clock.setTextSize(64);
        clock.setTextColor(Theme.TEXT);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clock.setFocusable(false);
        header.addView(clock, new LinearLayout.LayoutParams(MATCH, WRAP));

        dateLine = Theme.text(this, 16, Theme.TEXT_DIM, "sans-serif");
        dateLine.setPadding(0, Theme.dp(this, 2), 0, 0);
        header.addView(dateLine, new LinearLayout.LayoutParams(MATCH, WRAP));
        column.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        View divider = new View(this);
        divider.setBackgroundColor(Theme.BORDER);
        column.addView(divider, new LinearLayout.LayoutParams(MATCH, Theme.dp(this, 1)));

        FrameLayout content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        content.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.addView(grid, new ScrollView.LayoutParams(MATCH, WRAP));

        loading = Theme.text(this, 14, Theme.TEXT_DIM, "sans-serif");
        loading.setText("Loading apps…");
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(Theme.dp(this, 40), Theme.dp(this, 8),
                Theme.dp(this, 40), Theme.dp(this, 26));

        allAppsPill = pill("All Apps", v -> showAllApps());
        settingsPill = pill("Settings", v -> showSettings());
        amazonPill = pill("Amazon Home", v -> openAmazonHome());
        bottom.addView(allAppsPill);
        bottom.addView(settingsPill);
        bottom.addView(amazonPill);
        column.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
    }

    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        if (loading != null) loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            final List<AppEntry> loaded = AppCatalog.load(getApplicationContext());
            ui.post(() -> {
                loadingApps = false;
                if (isFinishing()) return;
                apps = loaded;
                if (loading != null) loading.setVisibility(View.GONE);
                if (overlay != null) closeOverlay();
                rebuildGrid();
            });
        }, "blackbox-apps").start();
    }

    private void rebuildGrid() {
        if (grid == null || apps == null) return;
        List<AppEntry> visible = visibleApps();
        firstTile = fillGrid(grid, visible, cfg.columns,
                GRID_LEFT_INSET_DP, GRID_RIGHT_INSET_DP, false);
        if (visible.isEmpty()) {
            TextView empty = Theme.text(this, 14, Theme.TEXT_DIM, "sans-serif");
            empty.setText("No apps. Open Settings → Hide apps to bring tiles back.");
            empty.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(0, cfg.columns));
            lp.width = MATCH;
            lp.height = Theme.dp(this, 80);
            grid.addView(empty, lp);
        } else {
            int count = visible.size();
            int lastRow = (count - 1) / cfg.columns;
            for (int i = lastRow * cfg.columns; i < count; i++) {
                View child = grid.getChildAt(i);
                if (child != null) child.setNextFocusDownId(allAppsPill.getId());
            }
            View last = grid.getChildAt(count - 1);
            if (last != null) {
                allAppsPill.setNextFocusUpId(last.getId());
                settingsPill.setNextFocusUpId(last.getId());
                amazonPill.setNextFocusUpId(last.getId());
            }
        }
        lastConfigKey = cfg.key();
        if (overlay == null && firstTile != null) firstTile.requestFocus();
    }

    private List<AppEntry> visibleApps() {
        List<AppEntry> visible = new ArrayList<>();
        for (AppEntry app : apps) {
            if (!cfg.isHidden(app.pkg)) visible.add(app);
        }
        final Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < cfg.order.size(); i++) order.put(cfg.order.get(i), i);
        Collections.sort(visible, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                Integer ai = order.get(a.pkg), bi = order.get(b.pkg);
                if (ai != null && bi != null) return ai - bi;
                if (ai != null) return -1;
                if (bi != null) return 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return visible;
    }

    private View fillGrid(GridLayout target, List<AppEntry> list, int cols,
            int leftInsetDp, int rightInsetDp, final boolean closeOnLaunch) {
        target.removeAllViews();
        target.setColumnCount(cols);
        int leftInset = Theme.dp(this, leftInsetDp);
        int rightInset = Theme.dp(this, rightInsetDp);
        target.setPadding(leftInset, 0, rightInset, 0);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gap = Theme.dp(this, 12);
        int tileW = (screenW - leftInset - rightInset - cols * gap) / cols;
        if (tileW < 1) tileW = 1;
        int tileH = Math.max(Theme.dp(this, 80), tileW * 9 / 16 + Theme.dp(this, 24));
        View first = null;
        for (int i = 0; i < list.size(); i++) {
            final AppEntry entry = list.get(i);
            AppTile tile = new AppTile(this, entry, tileW, tileH, cfg.animations, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (closeOnLaunch) closeOverlay();
                    launch(entry);
                }
            });
            tile.setId(View.generateViewId());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / cols), GridLayout.spec(i % cols));
            lp.width = tileW;
            lp.height = tileH;
            int half = gap / 2;
            lp.setMargins(half, half, half, half);
            target.addView(tile, lp);
            if (first == null) first = tile;
        }
        return first;
    }

    private void showAllApps() {
        if (apps == null) {
            toast("Apps are still loading");
            return;
        }
        LinearLayout column = newOverlay("ALL APPS");
        TextView hint = Theme.text(this, 12, Theme.TEXT_FAINT, "sans-serif");
        hint.setText("Back returns home");
        hint.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 10));
        column.addView(hint, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        GridLayout all = new GridLayout(this);
        all.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        all.setUseDefaultMargins(false);
        all.setClipChildren(false);
        all.setClipToPadding(false);
        scroll.addView(all, new ScrollView.LayoutParams(MATCH, WRAP));

        View first = fillGrid(all, new ArrayList<>(apps), cfg.columns,
                GRID_LEFT_INSET_DP, GRID_RIGHT_INSET_DP, true);
        if (first != null) first.requestFocus();
    }

    private void showSettings() {
        if (cfg == null) return;
        LinearLayout column = newOverlay("SETTINGS");
        TextView hint = Theme.text(this, 11, Theme.TEXT_FAINT, "sans-serif");
        hint.setText(String.valueOf(Config.file(this)));
        hint.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 10));
        column.addView(hint, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 8));
        scroll.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView columns = row("Columns    " + cfg.columns, v -> {
            cfg.columns = cfg.columns >= 6 ? 4 : cfg.columns + 1;
            save();
            rebuildGrid();
            ((TextView) v).setText("Columns    " + cfg.columns);
        });
        TextView clockRow = row("Clock    " + (cfg.clock24 ? "24-hour" : "12-hour"), v -> {
            cfg.clock24 = !cfg.clock24;
            save();
            applyClock();
            ((TextView) v).setText("Clock    " + (cfg.clock24 ? "24-hour" : "12-hour"));
        });
        TextView dateRow = row("Date    " + (cfg.showDate ? "Shown" : "Hidden"), v -> {
            cfg.showDate = !cfg.showDate;
            save();
            applyClock();
            ((TextView) v).setText("Date    " + (cfg.showDate ? "Shown" : "Hidden"));
        });
        TextView animationRow = row("Animations    " + (cfg.animations ? "On" : "Off"), v -> {
            cfg.animations = !cfg.animations;
            save();
            rebuildGrid();
            ((TextView) v).setText("Animations    " + (cfg.animations ? "On" : "Off"));
        });
        TextView hideApps = row("Hide apps    Choose", v -> showHiddenApps());
        TextView reload = row("Reload config from Download", v -> {
            if (Config.importFromDownload(this)) {
                cfg = Config.load(this);
                applyClock();
                rebuildGrid();
                closeOverlay();
                showSettings();
                toast("Config reloaded");
            } else {
                toast("No /sdcard/Download/blackbox-home.conf");
            }
        });
        TextView reset = row("Reset hidden apps", v -> {
            cfg.hidden.clear();
            save();
            rebuildGrid();
            toast("All app tiles restored");
        });
        TextView fireTvSettings = row("Fire TV Settings", v -> {
            closeOverlay();
            openSystemSettings();
        });
        TextView amazonHome = row("Amazon Home", v -> {
            closeOverlay();
            openAmazonHome();
        });
        TextView about = row("About Blackbox", v -> showAbout());
        TextView close = row("Close", v -> closeOverlay());

        list.addView(columns);
        list.addView(clockRow);
        list.addView(dateRow);
        list.addView(animationRow);
        list.addView(hideApps);
        list.addView(reload);
        list.addView(reset);
        list.addView(fireTvSettings);
        list.addView(amazonHome);
        list.addView(about);
        list.addView(close);
        columns.requestFocus();
    }

    private void showHiddenApps() {
        if (apps == null) {
            toast("Apps are still loading");
            return;
        }
        LinearLayout column = newOverlay("HIDE APPS");
        TextView hint = Theme.text(this, 12, Theme.TEXT_FAINT, "sans-serif");
        hint.setText("Hidden apps stay installed. This only removes their tile from Blackbox.");
        hint.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 10));
        column.addView(hint, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 8));
        scroll.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView first = null;
        for (final AppEntry app : apps) {
            final TextView line = row(rowText(app), v -> {
                cfg.toggleHidden(app.pkg);
                save();
                rebuildGrid();
                ((TextView) v).setText(rowText(app));
            });
            list.addView(line);
            if (first == null) first = line;
        }
        TextView back = row("Back", v -> closeOverlay());
        list.addView(back);
        (first == null ? back : first).requestFocus();
    }

    private void showAbout() {
        LinearLayout column = newOverlay("ABOUT");
        TextView body = Theme.text(this, 15, Theme.TEXT_DIM, "sans-serif");
        body.setText("Blackbox 0.1\n\n"
                + "A minimal home screen for the Fire TV Stick 2nd gen.\n\n"
                + "No internet permission. No analytics. No ads.\n\n"
                + "Inspired by dumbtv, cleanyourtv, and the Fire OS 5 logcat HomeWatcher reference. "
                + "See CREDITS.md and THIRD_PARTY_NOTICES.md in the project for attribution and licences.\n\n"
                + "Amazon, Fire TV, Fire OS, Alexa, and Prime Video are trademarks of Amazon.com, Inc. "
                + "or its affiliates. Blackbox is not affiliated with Amazon.\n\n"
                + "Back closes this screen.");
        body.setLineSpacing(Theme.dp(this, 4), 1f);
        body.setPadding(Theme.dp(this, 48), Theme.dp(this, 4), Theme.dp(this, 48), Theme.dp(this, 12));
        column.addView(body, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Theme.dp(this, 48), 0, Theme.dp(this, 48), Theme.dp(this, 8));
        TextView back = row("Back", v -> closeOverlay());
        list.addView(back);
        column.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));
        back.requestFocus();
    }

    private LinearLayout newOverlay(String heading) {
        closeOverlay();
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(Theme.BG);
        root.addView(layer, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        layer.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView title = Theme.text(this, 28, Theme.TEXT, "sans-serif-thin");
        title.setText(heading);
        title.setLetterSpacing(0.12f);
        title.setPadding(Theme.dp(this, 48), Theme.dp(this, 32),
                Theme.dp(this, 48), Theme.dp(this, 10));
        column.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        overlay = layer;
        return column;
    }

    private void closeOverlay() {
        if (overlay == null) return;
        root.removeView(overlay);
        overlay = null;
        if (firstTile != null) firstTile.requestFocus();
    }

    private TextView pill(String text, View.OnClickListener click) {
        TextView view = Theme.text(this, 16, Theme.TEXT_DIM, "sans-serif");
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Theme.dp(this, 20), Theme.dp(this, 10),
                Theme.dp(this, 20), Theme.dp(this, 10));
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setBackground(Theme.pill(this, false));
        view.setOnClickListener(click);
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(Theme.pill(this, focused));
            ((TextView) v).setTextColor(focused ? Theme.BLACK : Theme.TEXT_DIM);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(Theme.dp(this, 6), 0, Theme.dp(this, 6), 0);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView row(String text, View.OnClickListener click) {
        TextView view = Theme.text(this, 16, Theme.TEXT, "sans-serif");
        view.setText(text);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(Theme.dp(this, 18), 0, Theme.dp(this, 18), 0);
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setBackground(Theme.row(this, false));
        view.setOnClickListener(click);
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(Theme.row(this, focused)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, Theme.dp(this, 50));
        lp.setMargins(0, Theme.dp(this, 2), 0, Theme.dp(this, 2));
        view.setLayoutParams(lp);
        return view;
    }

    private String rowText(AppEntry app) {
        return (cfg.isHidden(app.pkg) ? "[ ] " : "[x] ") + app.label;
    }

    private void save() {
        if (!cfg.save(this)) toast("Couldn't save home.conf");
    }

    private void applyClock() {
        if (clock == null) return;
        String format = cfg.clock24 ? "HH:mm" : "h:mm";
        clock.setFormat12Hour(format);
        clock.setFormat24Hour(format);
        if (dateLine != null) dateLine.setVisibility(cfg.showDate ? View.VISIBLE : View.GONE);
    }

    private void updateDate() {
        if (dateLine != null) dateLine.setText(dateFormat.format(new Date()));
    }

    private void launch(AppEntry entry) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(entry.pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(entry.pkg);
        if (intent == null) {
            toast("Couldn't open " + entry.label);
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Couldn't open " + entry.label);
        }
    }

    private void openSystemSettings() {
        launch(new Intent(Settings.ACTION_SETTINGS), "Couldn't open Fire TV Settings.");
    }

    private void openAmazonHome() {
        closeOverlay();
        HomeWatcher.pause(AMAZON_PAUSE_MS);
        toast("Amazon's home for 10 minutes");
        launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(AMAZON_HOME), "Couldn't open Amazon's home.");
    }

    private void launch(Intent intent, String failure) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            HomeWatcher.pause(0);
            toast(failure);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void registerPackageReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeWatcher.pause(0);
        HomeWatcher.shown();
        cfg = Config.load(this);
        applyClock();
        updateDate();
        ui.removeCallbacks(clockTick);
        ui.post(clockTick);
        if (apps != null && !cfg.key().equals(lastConfigKey)) rebuildGrid();
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) {
            closeOverlay();
            return;
        }
        // This is the home screen; Back has nowhere useful to go.
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(packageReceiver);
        super.onDestroy();
    }
}
