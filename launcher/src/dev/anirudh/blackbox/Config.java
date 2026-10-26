package dev.anirudh.blackbox;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A plain key=value file in the app's external files directory, so it can be edited over ADB:
 * /sdcard/Android/data/dev.anirudh.blackbox/files/home.conf
 */
final class Config {
    private static final String TAG = "BlackboxConfig";
    private static final String FILE = "home.conf";

    int columns = 5;
    boolean clock24 = true;
    boolean showDate = true;
    boolean animations = true;
    final Set<String> hidden = new LinkedHashSet<>();
    final List<String> order = new ArrayList<>();

    /**
     * The curated first-run grid. Hidden apps stay installed; only their tiles are removed.
     * The owner can change this in Settings or by editing home.conf.
     */
    private static final String[] DEFAULT_HIDDEN = {
            "com.amazon.ftv.screensaver",
            "com.amazon.bueller.photos",
            "com.amazon.bueller.music",
            "com.amazon.bueller.notification",
            "com.amazon.tahoe",
            "com.amazon.minitv.android.app",
            "com.amazon.gamehub",
            "com.amazon.shoptv.firetv.client",
            "com.amazon.shoptv.client",
            "com.amazon.alexashopping",
            "com.amazon.imdb.tv.android.app",
            "com.imdb.livingroom.firetv",
            "com.amazon.tv.easyupgrade",
            "com.amazon.hedwig",
            "com.amazon.ftv.profilepicker",
            "com.amazon.cloud9",
            "com.amazon.ssm",
            "com.amazon.avod",
            "com.android.providers.downloads.ui",
            "com.aajtak.firetv",
            "com.abp.firetv",
            "com.july.ndtv",
            "com.mxtech.videoplayer.television",
            "tv.airtel.xstream.tvapp",
            "com.jio.media.stb.ondemand"
    };
    private static final String[] DEFAULT_ORDER = {
            "com.amazon.firetv.youtube",
            "com.netflix.ninja",
            "in.startv.hotstar",
            "com.onemainstream.sonyliv.android",
            "com.amazon.firebat",
            "com.amazon.venezia"
    };

    private Config() {}

    static File file(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        return dir == null ? null : new File(dir, FILE);
    }

    static Config load(Context context) {
        Config config = new Config();
        File file = file(context);
        if (file == null) return config;
        if (!file.isFile()) {
            for (String pkg : DEFAULT_HIDDEN) config.hidden.add(pkg);
            for (String pkg : DEFAULT_ORDER) config.order.add(pkg);
            config.save(context);
            return config;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int split = line.indexOf('=');
                if (split < 0) continue;
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                switch (key) {
                    case "columns":
                        config.columns = clamp(parseInt(value, config.columns), 4, 6);
                        break;
                    case "clock24":
                        config.clock24 = parseBool(value, config.clock24);
                        break;
                    case "show_date":
                        config.showDate = parseBool(value, config.showDate);
                        break;
                    case "animations":
                        config.animations = parseBool(value, config.animations);
                        break;
                    case "hidden":
                        config.hidden.clear();
                        for (String pkg : value.split(",")) {
                            if (!pkg.trim().isEmpty()) config.hidden.add(pkg.trim());
                        }
                        break;
                    case "order":
                        config.order.clear();
                        for (String pkg : value.split(",")) {
                            if (!pkg.trim().isEmpty()) config.order.add(pkg.trim());
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "could not read " + file, e);
        }
        return config;
    }

    /**
     * Fire OS 5 gives the app ownership of its external files directory, so adb shell
     * cannot drop a config into it. The owner can push a file to /sdcard/Download
     * and import it from Settings instead.
     */
    static boolean importFromDownload(Context context) {
        File source = new File(Environment.getExternalStorageDirectory(),
                "Download/blackbox-home.conf");
        File target = file(context);
        if (target == null || !source.isFile()) return false;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            for (int read; (read = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "could not import " + source, e);
            return false;
        }
    }

    boolean save(Context context) {
        File file = file(context);
        if (file == null) return false;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("# Blackbox home.conf\n");
            writer.write("columns=" + columns + "\n");
            writer.write("clock24=" + (clock24 ? "1" : "0") + "\n");
            writer.write("show_date=" + (showDate ? "1" : "0") + "\n");
            writer.write("animations=" + (animations ? "1" : "0") + "\n");
            writer.write("hidden=" + join(hidden) + "\n");
            writer.write("order=" + join(order) + "\n");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "could not save " + file, e);
            return false;
        }
    }

    boolean isHidden(String pkg) {
        return hidden.contains(pkg);
    }

    void toggleHidden(String pkg) {
        if (!hidden.remove(pkg)) hidden.add(pkg);
    }

    String key() {
        return columns + "|" + hidden + "|" + order;
    }

    private static String join(Iterable<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        if ("1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) return true;
        if ("0".equals(value) || "false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
