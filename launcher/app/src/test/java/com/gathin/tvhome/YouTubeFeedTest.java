package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class YouTubeFeedTest {

    private static String entry(String id, String title, String published) {
        return "<entry><id>yt:video:" + id + "</id><yt:videoId>" + id + "</yt:videoId>"
                + "<title>" + title + "</title><author>\n <name>Mercedes-AMG PETRONAS F1 Team</name></author>"
                + "<published>" + published + "</published></entry>";
    }

    @Test
    public void parsesEntries() {
        String xml = "<feed><title>Channel</title>"
                + entry("abcDEF123_-", "Russell &amp; Kimi | Debrief", "2026-10-24T18:00:00+00:00")
                + "<entry><title>no id</title></entry></feed>";
        List<YouTubeFeed.Video> v = YouTubeFeed.parse(xml);
        assertEquals(1, v.size());
        assertEquals("abcDEF123_-", v.get(0).id);
        assertEquals("Russell & Kimi | Debrief", v.get(0).title);
        assertEquals("Mercedes-AMG PETRONAS F1 Team", v.get(0).channel);
        assertEquals("https://www.youtube.com/watch?v=abcDEF123_-", v.get(0).watchUrl());
    }

    @Test
    public void picksNewestThatIsNotAShort() {
        List<YouTubeFeed.Video> v = YouTubeFeed.parse(
                entry("aaaaaaaaaaa", "Race highlights", "2026-10-23T10:00:00+00:00")
                        + entry("bbbbbbbbbbb", "Pole in Texas #f1 #shorts", "2026-10-25T10:00:00+00:00")
                        + entry("ccccccccccc", "Qualifying debrief", "2026-10-24T10:00:00+00:00"));
        assertEquals("ccccccccccc", YouTubeFeed.pick(v).id);
    }

    @Test
    public void onlyShortsFallsBackToNewest() {
        List<YouTubeFeed.Video> v = YouTubeFeed.parse(
                entry("aaaaaaaaaaa", "#one", "2026-10-23T10:00:00+00:00")
                        + entry("bbbbbbbbbbb", "#two", "2026-10-25T10:00:00+00:00"));
        assertEquals("bbbbbbbbbbb", YouTubeFeed.pick(v).id);
        assertNull(YouTubeFeed.pick(new ArrayList<>()));
    }

    @Test
    public void channelIds() {
        assertTrue(YouTubeFeed.validChannel("UC7_VGkwCoKfqh1eZEkzey3A"));
        assertFalse(YouTubeFeed.validChannel("UC7_VGkwCoKfqh1eZEkzey3"));
        assertFalse(YouTubeFeed.validChannel("@MercedesAMGF1"));
        assertFalse(YouTubeFeed.validChannel(null));
    }
}
