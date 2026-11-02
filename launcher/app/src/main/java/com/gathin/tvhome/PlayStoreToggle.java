package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The pill under the dock (config.xml play_store_toggle) that turns the Play Store off and on, so it stops
 * running in the background and holding RAM. Off is pm disable-user, through LocalAdb; apps that are already
 * installed (Netflix, SmartTube, YouTube) keep working, they just don't update until it is on again.
 */
final class PlayStoreToggle {
    static final String PACKAGE = "com.android.vending";

    private final TextView button;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    PlayStoreToggle(TextView button) {
        this.button = button;
        button.setOnClickListener(v -> toggle());
        refresh();
    }

    /** Shows the current state; call on start and when packages change. */
    void refresh() {
        if (busy) return;
        button.setText(isEnabled(button.getContext()) ? R.string.play_store_on : R.string.play_store_off);
    }

    static boolean isEnabled(Context context) {
        try {
            int state = context.getPackageManager().getApplicationEnabledSetting(PACKAGE);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    || state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (IllegalArgumentException notInstalled) {
            return false;
        }
    }

    private void toggle() {
        if (busy) return;
        Context context = button.getContext().getApplicationContext();
        boolean turnOn = !isEnabled(context);
        busy = true;
        button.setText(turnOn ? R.string.play_store_turning_on : R.string.play_store_turning_off);
        String command = turnOn ? "pm enable " + PACKAGE : "pm disable-user --user 0 " + PACKAGE;
        new Thread(() -> {
            int error = 0;
            try {
                SystemActions.run(context, command, false);
            } catch (LocalAdb.AdbException e) {
                error = e.notAllowed ? R.string.play_store_allow_prompt : R.string.play_store_need_debugging;
            } catch (Exception e) {
                error = R.string.play_store_failed;
            }
            final int message = error;
            main.post(() -> {
                busy = false;
                refresh();
                if (message != 0) Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            });
        }, "play-store-toggle").start();
    }
}
