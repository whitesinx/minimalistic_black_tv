package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headlines for the news panel (config news_feed): an RSS feed fetched over HTTPS while Home is on screen, parsed
 * with plain string matching so the parsing runs in JVM tests. The only part of the launcher that goes online,
 * and only when a theme or config.xml names a feed.
 */
final class NewsFeed {

    static final class Item {
        final String title;
        final String summary;

        Item(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }
    }

    /** A feed larger than this is cut off; formula1.com's is about 8 KB. */
    private static final int MAX_BYTES = 512 * 1024;
    private static final Pattern ITEM = Pattern.compile("<item[\\s>](.*?)</item>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern DESCRIPTION = Pattern.compile("<description[^>]*>(.*?)</description>", Pattern.DOTALL);

    private NewsFeed() {
    }

    /** Only https and http URLs; anything else (file:, content:) is no feed. */
    static boolean validUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    static List<Item> fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "CleanYourTV launcher");
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < MAX_BYTES) out.write(buf, 0, n);
            return parse(new String(out.toByteArray(), StandardCharsets.UTF_8), 15);
        } finally {
            c.disconnect();
        }
    }

    /** The first max items that have a title, in feed order (newest first in every feed seen so far). */
    static List<Item> parse(String xml, int max) {
        List<Item> items = new ArrayList<>();
        Matcher item = ITEM.matcher(xml);
        while (item.find() && items.size() < max) {
            String body = item.group(1);
            Matcher t = TITLE.matcher(body);
            String title = t.find() ? text(t.group(1)) : "";
            if (title.isEmpty()) continue;
            Matcher d = DESCRIPTION.matcher(body);
            items.add(new Item(title, d.find() ? text(d.group(1)) : ""));
        }
        return items;
    }

    /** CDATA unwrapped, HTML tags dropped, entities decoded, whitespace collapsed. */
    static String text(String raw) {
        String s = raw.trim();
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) s = s.substring(9, s.length() - 3);
        s = decode(s);                       // escaped HTML in a description becomes tags here...
        s = s.replaceAll("<[^>]*>", " ");    // ...and goes with the rest
        return s.replaceAll("\\s+", " ").trim();
    }

    static String decode(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int semi = ch == '&' ? s.indexOf(';', i) : -1;
            if (semi < 0 || semi - i > 10) {
                out.append(ch);
                continue;
            }
            String e = s.substring(i + 1, semi);
            String r = null;
            switch (e) {
                case "amp": r = "&"; break;
                case "lt": r = "<"; break;
                case "gt": r = ">"; break;
                case "quot": r = "\""; break;
                case "apos": r = "'"; break;
                case "nbsp": r = " "; break;
                default:
                    try {
                        if (e.startsWith("#x") || e.startsWith("#X")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                        else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                    } catch (IllegalArgumentException ignored) {
                        // not an entity after all
                    }
            }
            if (r == null) {
                out.append(ch);
            } else {
                out.append(r);
                i = semi;
            }
        }
        return out.toString();
    }
}
