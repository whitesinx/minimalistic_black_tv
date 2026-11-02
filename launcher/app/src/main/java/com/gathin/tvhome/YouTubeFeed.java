package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The video card beside the news (config youtube_channels): the newest upload of the named YouTube channels, from
 * YouTube's public channel feeds (no account, no API key). Watch history and recommendations need the owner's
 * Google login, which a launcher does not have, so a channel's latest video is the closest one-click pick.
 */
final class YouTubeFeed {

    static final class Video {
        final String id;
        final String title;
        final String channel;
        /** ISO-8601 as the feed writes it; compares correctly as a string. */
        final String published;

        Video(String id, String title, String channel, String published) {
            this.id = id;
            this.title = title;
            this.channel = channel;
            this.published = published;
        }

        String watchUrl() {
            return "https://www.youtube.com/watch?v=" + id;
        }
    }

    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern ENTRY = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern VIDEO_ID = Pattern.compile("<yt:videoId>([A-Za-z0-9_-]{6,20})</yt:videoId>");
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern PUBLISHED = Pattern.compile("<published>([^<]+)</published>");
    private static final Pattern AUTHOR = Pattern.compile("<author>\\s*<name>(.*?)</name>", Pattern.DOTALL);

    private YouTubeFeed() {
    }

    static boolean validChannel(String id) {
        return id != null && CHANNEL_ID.matcher(id).matches();
    }

    static List<Video> parse(String xml) {
        List<Video> out = new ArrayList<>();
        Matcher e = ENTRY.matcher(xml);
        while (e.find()) {
            String body = e.group(1);
            Matcher id = VIDEO_ID.matcher(body), t = TITLE.matcher(body), p = PUBLISHED.matcher(body),
                    a = AUTHOR.matcher(body);
            if (!id.find() || !t.find()) continue;
            out.add(new Video(id.group(1), NewsFeed.text(t.group(1)),
                    a.find() ? NewsFeed.text(a.group(1)) : "", p.find() ? p.group(1).trim() : ""));
        }
        return out;
    }

    /**
     * The newest video that is not a Short. Shorts are vertical and get hashtag titles ("Pole feels good in Texas
     * #f1"), so a title with a '#' is skipped while anything else is left; with nothing else, the newest of all.
     */
    static Video pick(List<Video> videos) {
        Video best = null, any = null;
        for (Video v : videos) {
            if (any == null || v.published.compareTo(any.published) > 0) any = v;
            if (v.title.indexOf('#') >= 0) continue;
            if (best == null || v.published.compareTo(best.published) > 0) best = v;
        }
        return best != null ? best : any;
    }

    /** Newest first, hashtag titles (likely Shorts) after the rest; Shorts are told apart for sure on the network. */
    static List<Video> rank(List<Video> videos) {
        List<Video> out = new ArrayList<>(videos);
        out.sort(Comparator.comparing((Video v) -> v.title.indexOf('#') >= 0)
                .thenComparing((Video v) -> v.published, Comparator.reverseOrder()));
        return out;
    }

    /**
     * Up to max of the channels' newest videos that are not Shorts, newest first. A Short answers its /shorts/ URL
     * itself while any other video is redirected to /watch, which tells them apart whatever the title says.
     */
    static List<Video> fetchCandidates(List<String> channels, int max) throws IOException {
        List<Video> out = new ArrayList<>();
        List<Video> ranked = rank(fetchAll(channels));
        // A share for each channel, so one that uploads a lot (or keeps its videos off other sites) cannot fill the
        // list on its own.
        int perChannel = Math.max(1, (max + channels.size() - 1) / channels.size());
        java.util.Map<String, Integer> taken = new java.util.HashMap<>();
        for (int i = 0, checked = 0; i < ranked.size() && checked < max * 3 && out.size() < max; i++) {
            Video v = ranked.get(i);
            int n = taken.getOrDefault(v.channel, 0);
            if (n >= perChannel) continue;
            checked++;
            if (isShort(v.id)) continue;
            taken.put(v.channel, n + 1);
            out.add(v);
        }
        if (out.isEmpty() && !ranked.isEmpty()) out.add(ranked.get(0));
        return out;
    }

    private static boolean isShort(String id) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://www.youtube.com/shorts/" + id).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("HEAD");
            // Android's own User-Agent gets a 302 to the mobile site for every video, Short or not.
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            try {
                return c.getResponseCode() == 200;
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            return false;   // unknown: let it through rather than lose the card
        }
    }

    private static List<Video> fetchAll(List<String> channels) throws IOException {
        List<Video> all = new ArrayList<>();
        IOException last = null;
        for (String channel : channels) {
            try {
                all.addAll(parse(uploads(channel)));
            } catch (IOException e) {
                last = e;   // one channel down still leaves the others
            }
        }
        if (all.isEmpty() && last != null) throw last;
        return all;
    }

    /** The video's 16:9 thumbnail, the 1280x720 one when YouTube has it, sampled down to about maxWidth. */
    static Bitmap thumbnail(String id, int maxWidth) {
        for (String name : new String[]{"hq720.jpg", "mqdefault.jpg"}) {
            try {
                byte[] bytes = getBytes("https://i.ytimg.com/vi/" + id + "/" + name, 2 * 1024 * 1024);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                o.inSampleSize = 1;
                while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                if (b != null) return b;
            } catch (IOException ignored) {
                // hq720 is missing for older or SD uploads: try the small one
            }
        }
        return null;
    }

    /**
     * The channel's long videos: YouTube keeps them in a playlist named UULF plus the channel ID without its "UC",
     * so a channel that posts mostly Shorts still has its real videos in reach. The channel feed when that fails.
     */
    private static String uploads(String channel) throws IOException {
        try {
            return get("https://www.youtube.com/feeds/videos.xml?playlist_id=UULF" + channel.substring(2), 512 * 1024);
        } catch (IOException e) {
            return get("https://www.youtube.com/feeds/videos.xml?channel_id=" + channel, 512 * 1024);
        }
    }

    private static String get(String url, int max) throws IOException {
        return new String(getBytes(url, max), StandardCharsets.UTF_8);
    }

    private static byte[] getBytes(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > max) throw new IOException("larger than " + max + " bytes: " + url);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
