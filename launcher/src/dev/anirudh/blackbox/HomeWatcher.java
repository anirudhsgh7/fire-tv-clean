/*
 * The logcat HomeWatcher technique is adapted from
 * whyashthakker/debloat-fire-stick-claude-code (MIT, Copyright (c) 2026 Gathin),
 * which tested it on this exact AFTT / Fire OS 5.2.9.5 target.
 * The alarm launch, transition cover, and Blackbox integration are additions.
 * See THIRD_PARTY_NOTICES.md.
 */
package dev.anirudh.blackbox;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Fire OS 5 keeps HOME bound to Amazon's protected launcher and silently drops third-party
 * accessibility services enabled over ADB. It does allow READ_LOGS, and ActivityManager logs
 * every HOME launch. Follow those lines and put Blackbox back in front.
 *
 * This is the same proven approach used by the Fire OS 5 reference project credited in
 * THIRD_PARTY_NOTICES.md. It sees ActivityManager log lines only; the app has no INTERNET permission.
 */
public final class HomeWatcher extends Service {
    static final String EXTRA_CATCH_UP = "catch_up";
    private static final String TAG = "BlackboxWatch";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final long REPEAT_MS = 1500;

    private static volatile boolean running;
    private static volatile long pausedUntil;
    private static long lastLaunch;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideCoverTask = new Runnable() {
        @Override
        public void run() {
            if (cover != null) cover.setVisibility(View.GONE);
        }
    };
    private final Runnable bootLaunch = new Runnable() {
        @Override
        public void run() {
            launchHome();
        }
    };
    private volatile boolean alive = true;
    private Thread reader;
    private WindowManager windows;
    private View cover;

    static boolean running() {
        return running;
    }

    /** Lets Amazon's home stay on screen until the user leaves it, for the "Amazon Home" button. */
    static void pause(long millis) {
        pausedUntil = SystemClock.elapsedRealtime() + millis;
        if (runningInstance != null) runningInstance.hideCover();
    }

    private static volatile HomeWatcher runningInstance;

    /** Called when Blackbox has actually drawn; the transition cover can go away. */
    static void shown() {
        HomeWatcher watcher = runningInstance;
        if (watcher != null) {
            watcher.hideCover();
            watcher.ui.removeCallbacks(watcher.bootLaunch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        runningInstance = this;
        if (cover == null) addCover();
        final boolean catchUp = intent != null
                && intent.getBooleanExtra(EXTRA_CATCH_UP, false);
        if (reader == null) {
            startForeground(1, new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("home watcher")
                    .setPriority(Notification.PRIORITY_MIN)
                    .setOngoing(true)
                    .build());
            alive = true;
            reader = new Thread(() -> follow(catchUp), "blackbox-home-watcher");
            reader.setDaemon(true);
            reader.start();
        }
        if (catchUp) {
            // Boot broadcasts and Amazon's own start-up can race the log watcher.
            // Put Blackbox on top once the boot burst has settled.
            ui.removeCallbacks(bootLaunch);
            ui.postDelayed(bootLaunch, 15000);
        }
        return START_STICKY;
    }

    private void follow(boolean catchUp) {
        running = true;
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        if (catchUp) catchUp();
        while (alive) {
            java.lang.Process logcat = null;
            try {
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1",
                        "ActivityManager:I", "*:S")
                        .redirectErrorStream(true).start();
                BufferedReader lines = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                for (String line; alive && (line = lines.readLine()) != null; ) {
                    if (isAmazonHome(line)) {
                        Log.i(TAG, "HOME line seen at " + SystemClock.elapsedRealtime());
                        amazonHomeStarted();
                    }
                }
            } catch (Exception ignored) {
                // logcat ended or could not start; retry below
            } finally {
                if (logcat != null) logcat.destroy();
            }
            if (alive) SystemClock.sleep(2000);
        }
        running = false;
    }

    /**
     * If the service starts after boot and Amazon's home is already the last window started,
     * take over once. Do not replay an old Home start over a video the owner has since opened.
     */
    private void catchUp() {
        String last = null;
        try {
            java.lang.Process dump = new ProcessBuilder("logcat", "-d", "-t", "300", "-v", "brief",
                    "ActivityManager:I", "*:S")
                    .redirectErrorStream(true).start();
            BufferedReader lines = new BufferedReader(new InputStreamReader(dump.getInputStream()));
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.contains("START u0")) last = line;
            }
            dump.waitFor();
        } catch (Exception ignored) {
            return;
        }
        if (last != null && isAmazonHome(last)) amazonHomeStarted();
    }

    private static boolean isAmazonHome(String line) {
        return line.contains("START u0")
                && line.contains("android.intent.category.HOME")
                && line.contains("cmp=" + AMAZON_HOME + "/");
    }

    private void amazonHomeStarted() {
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil || now - lastLaunch < REPEAT_MS) return;
        lastLaunch = now;
        showCover();
        Log.i(TAG, "launch requested at " + now);
        launchHome();
    }

    /**
     * A short full-screen black cover. Fire OS delays the activity behind the HOME
     * transition; the cover keeps Amazon's sponsored rows out of sight until Blackbox
     * is actually in front. It is not focusable and cannot receive touches.
     */
    private void addCover() {
        try {
            windows = (WindowManager) getSystemService(WINDOW_SERVICE);
            FrameLayout frame = new FrameLayout(this);
            frame.setBackgroundColor(Color.BLACK);
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            TextView mark = new TextView(this);
            mark.setText("BLACKBOX");
            mark.setTextColor(0xFFFFFFFF);
            mark.setTextSize(22);
            mark.setLetterSpacing(0.18f);
            mark.setGravity(Gravity.CENTER);
            center.addView(mark);
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminate(true);
            if (spinner.getIndeterminateDrawable() != null) {
                spinner.getIndeterminateDrawable().setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
            }
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    Theme.dp(this, 28), Theme.dp(this, 28));
            spinnerParams.topMargin = Theme.dp(this, 20);
            spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
            center.addView(spinner, spinnerParams);
            frame.addView(center, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;
            windows.addView(frame, lp);
            frame.setVisibility(View.GONE);
            cover = frame;
        } catch (Exception e) {
            Log.w(TAG, "draw-over-apps unavailable; transitions may show Amazon's home", e);
            cover = null;
        }
    }

    private void showCover() {
        if (cover == null) return;
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (cover == null) return;
                ui.removeCallbacks(hideCoverTask);
                try {
                    cover.setVisibility(View.VISIBLE);
                    // Never leave a black screen behind if Blackbox fails to draw.
                    ui.postDelayed(hideCoverTask, 10000);
                } catch (Exception ignored) {
                    // the window may have been removed with the service
                }
            }
        });
    }

    private void hideCover() {
        ui.removeCallbacks(hideCoverTask);
        ui.post(hideCoverTask);
    }

    /**
     * Android delays background activity starts for about five seconds after HOME.
     * A PendingIntent fired by AlarmManager is sent by system_server, which is exempt,
     * so Blackbox can cover Amazon's home immediately. This is the same approach
     * dumbtv uses for its Fire OS guard.
     */
    private void launchHome() {
        Intent intent = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
            PendingIntent launch = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            // Alarm-clock alarms are exact and not batched. The show intent is
            // optional on this API level; the TV has no alarm UI to open.
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis(), null), launch);
        } catch (Exception ignored) {
            try {
                startActivity(intent);
            } catch (Exception ignoredAgain) {
                // leave Amazon's home alone if neither path works
            }
        }
    }

    @Override
    public void onDestroy() {
        alive = false;
        running = false;
        if (reader != null) reader.interrupt();
        ui.removeCallbacksAndMessages(null);
        if (windows != null && cover != null) {
            try {
                windows.removeView(cover);
            } catch (Exception ignored) {
                // already detached
            }
        }
        if (runningInstance == this) runningInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
