package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Toggles WiZ lights on the home network from a remote button (config.xml light_keys, through InputKeyService):
 * every light, or only those whose MACs are listed (a room). Any of them on turns them all off, all off turns them
 * all on. WiZ answers JSON over UDP port 38899 on the local network, so this needs no cloud account; the lights are
 * found by broadcast each time, so new addresses from the router do not matter.
 */
final class WizLights {

    private static final int PORT = 38899;
    private static final int LISTEN_MS = 700;
    private static final Pattern STATE_ON = Pattern.compile("\"state\"\\s*:\\s*true");
    private static final Pattern MAC = Pattern.compile("\"mac\"\\s*:\\s*\"([0-9a-fA-F]{12})\"");
    static final String ALL = "all";

    private WizLights() {
    }

    /** target: "all", or light MACs separated by commas. Runs off the main thread, shows "Lights on/off" when done. */
    static void toggle(Context context, String target) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String result;
            try {
                result = toggleNow(app, target);
            } catch (Exception e) {
                result = app.getString(R.string.lights_failed);
            }
            String text = result;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_SHORT).show());
        }, "wiz").start();
    }

    private static String toggleNow(Context context, String target) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(150);
            InetAddress everyone = InetAddress.getByName("255.255.255.255");
            byte[] ask = "{\"method\":\"getPilot\",\"params\":{}}".getBytes(StandardCharsets.UTF_8);
            Map<InetAddress, String> replies = new LinkedHashMap<>();
            byte[] buf = new byte[1024];
            long end = System.currentTimeMillis() + LISTEN_MS;
            int asked = 0;
            while (System.currentTimeMillis() < end) {
                // UDP can drop a packet: ask a few times while listening.
                if (asked < 3) {
                    socket.send(new DatagramPacket(ask, ask.length, everyone, PORT));
                    asked++;
                }
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(in);
                    String reply = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
                    if (targets(target, reply)) replies.put(in.getAddress(), reply);
                } catch (SocketTimeoutException ignored) {
                }
            }
            if (replies.isEmpty()) return context.getString(R.string.lights_none);
            boolean turnOn = !anyOn(replies.values());
            byte[] set = pilot(turnOn).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 3; i++) {
                for (InetAddress light : replies.keySet()) socket.send(new DatagramPacket(set, set.length, light, PORT));
                Thread.sleep(60);
            }
            return context.getString(turnOn ? R.string.lights_on : R.string.lights_off);
        }
    }

    /** Whether a getPilot reply comes from a light the target names. */
    static boolean targets(String target, String reply) {
        if (ALL.equals(target.trim())) return true;
        Matcher m = MAC.matcher(reply);
        if (!m.find()) return false;
        for (String mac : target.split(",")) if (mac.trim().equalsIgnoreCase(m.group(1))) return true;
        return false;
    }

    /** True when any getPilot reply says the light is on. */
    static boolean anyOn(Iterable<String> replies) {
        for (String reply : replies) if (STATE_ON.matcher(reply).find()) return true;
        return false;
    }

    static String pilot(boolean on) {
        return "{\"method\":\"setPilot\",\"params\":{\"state\":" + on + "}}";
    }
}
