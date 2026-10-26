package dev.anirudh.blackbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the watcher at boot; it opens Blackbox if Amazon's home came up. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, HomeWatcher.class)
                .putExtra(HomeWatcher.EXTRA_CATCH_UP, true));

        // On Fire OS 5 the boot broadcast queue can be very slow. Once BOOT_COMPLETED
        // reaches us, put the home task on screen directly as well as starting the watcher.
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                context.startActivity(new Intent(context, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (Exception ignored) {
                // the watcher is still running and will cover the next HOME press
            }
        }
    }
}
