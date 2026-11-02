package com.gathin.tvhome;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * The top bar's Debugging toggle and Power menu (config.xml minimal_top_bar), and the ADB access they and the
 * Play Store toggle share. Debugging on/off is Settings.Global.ADB_ENABLED, which needs WRITE_SECURE_SETTINGS
 * (granted once over ADB: pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS). While debugging
 * is off, a command that needs ADB switches it on for a moment and off again.
 */
final class SystemActions {
    private static final String PREFS = "system_actions";
    /** Set before a restart that had to switch debugging on: the next start switches it off again. */
    private static final String RESTORE_ADB_OFF = "restore_adb_off";

    private SystemActions() {}

    static boolean adbOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    static boolean canSwitchAdb(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** On launcher start: finish a restart that switched debugging on only to run. */
    static void restoreAfterRestart(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean(RESTORE_ADB_OFF, false)) return;
        p.edit().remove(RESTORE_ADB_OFF).apply();
        if (canSwitchAdb(c)) Settings.Global.putInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
    }

    /**
     * Runs a shell command through LocalAdb, switching debugging on first when it is off (and off again after,
     * unless {@code restart}: then the next start does it). Blocking: call off the main thread.
     */
    static String run(Context c, String command, boolean restart) throws IOException {
        if (adbOn(c)) return LocalAdb.shell(c, command);
        if (!canSwitchAdb(c)) throw new LocalAdb.AdbException("Debugging is off", false);
        ContentResolver cr = c.getContentResolver();
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1);
        if (restart) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RESTORE_ADB_OFF, true).commit();
        }
        try {
            waitForAdbd();
            return LocalAdb.shell(c, command);
        } finally {
            if (!restart) Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0);
        }
    }

    private static void waitForAdbd() {
        for (int i = 0; i < 30; i++) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", 5555), 500);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ---- What debugging exposes: the TV's address and who is connected ------------------------------------

    /** "ADB 192.168.0.104:5555" and the connected computers, or null when debugging is off. */
    static String debugInfo(Context c) {
        if (!adbOn(c)) return null;
        String ip = null;
        android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
        android.net.LinkProperties lp = cm == null ? null : cm.getLinkProperties(cm.getActiveNetwork());
        if (lp != null) {
            for (android.net.LinkAddress a : lp.getLinkAddresses()) {
                if (a.getAddress() instanceof java.net.Inet4Address) ip = a.getAddress().getHostAddress();
            }
        }
        java.util.Set<String> peers = adbPeers();
        String first = "ADB  " + (ip != null ? ip : "no network") + ":5555";
        if (peers == null) return first;
        return first + "\n" + (peers.isEmpty() ? "No device connected"
                : (peers.size() == 1 ? "Connected: " : peers.size() + " connected: ") + String.join(", ", peers));
    }

    /** Remote addresses with an open connection to adbd's port 5555 (not the launcher's own), or null if unreadable. */
    static java.util.Set<String> adbPeers() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        boolean read = false;
        for (String file : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(file))) {
                read = true;
                String line = r.readLine();   // header
                while ((line = r.readLine()) != null) {
                    String peer = adbPeer(line);
                    if (peer != null) out.add(peer);
                }
            } catch (IOException ignored) {
            }
        }
        return read ? out : null;
    }

    /** One /proc/net/tcp(6) row: the IPv4 peer when it is an established connection to local port 5555. */
    static String adbPeer(String row) {
        String[] f = row.trim().split("\\s+");
        if (f.length < 4 || !"01".equals(f[3])) return null;
        int lc = f[1].lastIndexOf(':'), rc = f[2].lastIndexOf(':');
        if (lc < 0 || rc < 0 || !"15B3".equalsIgnoreCase(f[1].substring(lc + 1))) return null;
        String hex = f[2].substring(0, rc);
        if (hex.length() == 32) {
            if (!hex.substring(0, 24).equalsIgnoreCase("0000000000000000FFFF0000")) return null;
            hex = hex.substring(24);
        }
        if (hex.length() != 8) return null;
        long v = Long.parseLong(hex, 16);
        String ip = (v & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 24) & 0xFF);
        return ip.startsWith("127.") ? null : ip;
    }

    // ---- Debugging toggle ------------------------------------------------------------------------------

    static void bindDebugToggle(TextView button) {
        Runnable refresh = () -> button.setText(adbOn(button.getContext())
                ? R.string.debugging_on : R.string.debugging_off);
        refresh.run();
        button.setTag(refresh);
        button.setOnClickListener(v -> {
            Context c = v.getContext();
            if (!canSwitchAdb(c)) {
                Toast.makeText(c, R.string.debugging_no_permission, Toast.LENGTH_LONG).show();
                return;
            }
            Settings.Global.putInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, adbOn(c) ? 0 : 1);
            refresh.run();
        });
    }

    static void refreshDebugToggle(TextView button) {
        if (button != null && button.getTag() instanceof Runnable) ((Runnable) button.getTag()).run();
    }

    // ---- Power -----------------------------------------------------------------------------------------

    static void showPowerMenu(Context activity) {
        String[] items = {activity.getString(R.string.power_off), activity.getString(R.string.power_restart)};
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setItems(items, (d, which) -> power(activity.getApplicationContext(), which == 1))
                .show();
    }

    /** Off is the remote's power key (standby, the remote wakes it); restart is a full reboot. */
    private static void power(Context c, boolean restart) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                run(c, restart ? "reboot" : "input keyevent KEYCODE_POWER", restart);
            } catch (IOException e) {
                // A reboot drops the connection before the shell answers; that is success.
                if (restart && !(e instanceof LocalAdb.AdbException)) return;
                main.post(() -> Toast.makeText(c, R.string.power_failed, Toast.LENGTH_LONG).show());
            }
        }, "power").start();
    }
}
