package com.gathin.tvhome;

import java.util.Locale;

/**
 * Text for the read-only top bar pill (config.xml ram_net_pill): memory in use and the live network speed,
 * e.g. "RAM 0.8/1.5 GB   ↓ 1.2 MB/s  ↑ 40 KB/s". Pure formatting, so it runs in JVM tests.
 */
final class NetStats {

    private static final double GB = 1024.0 * 1024 * 1024;

    private NetStats() {
    }

    static String line(long usedBytes, long totalBytes, long rxPerSec, long txPerSec) {
        return String.format(Locale.ROOT, "RAM %.1f/%.1f GB   ↓ %s  ↑ %s",
                usedBytes / GB, totalBytes / GB, rate(rxPerSec), rate(txPerSec));
    }

    /** net_pill_ram false: the network speed alone, e.g. "↓ 1.2 MB/s  ↑ 40 KB/s". */
    static String line(long rxPerSec, long txPerSec) {
        return "↓ " + rate(rxPerSec) + "  ↑ " + rate(txPerSec);
    }

    /** Bytes a second as B/s, KB/s (whole) or MB/s (one decimal). */
    static String rate(long bytesPerSec) {
        if (bytesPerSec < 0) return "–";
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024) return (bytesPerSec / 1024) + " KB/s";
        return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSec / (1024.0 * 1024));
    }

    /** Bytes a second between two counter readings ms apart; -1 when either reading is missing or went back. */
    static long perSecond(long before, long after, long ms) {
        if (before < 0 || after < before || ms <= 0) return -1;
        return (after - before) * 1000 / ms;
    }
}
