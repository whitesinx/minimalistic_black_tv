package com.gathin.tvhome;

/** Screensaver quotes (config screensaver_quotes): "text — Author", or the text alone for screensaver_quote_by. */
final class Quotes {
    private static final String BY = " — ";

    private Quotes() {
    }

    /** {text, author}: the author after the last " — ", else fallback (which may be empty). */
    static String[] split(String entry, String fallback) {
        int at = entry.lastIndexOf(BY);
        if (at > 0 && at + BY.length() < entry.length()) {
            return new String[]{entry.substring(0, at).trim(), entry.substring(at + BY.length()).trim()};
        }
        return new String[]{entry.trim(), fallback == null ? "" : fallback.trim()};
    }
}
