package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class NewsFeedTest {
    private static final String FEED = "<rss><channel><title>Latest F1 News</title>"
            + "<item>\n  <title>How Lawson helped Lindblad</title>\n"
            + "  <description>Liam Lawson didn&#39;t venture out &amp; more.</description></item>"
            + "<item><title><![CDATA[Antonelli pole: 'finally seeing results']]></title>"
            + "<description><![CDATA[<p>Mercedes <b>upgrades</b></p>]]></description></item>"
            + "<item><title></title></item>"
            + "<item><title>Third</title></item></channel></rss>";

    @Test
    public void itemsComeInFeedOrderWithTheChannelTitleLeftOut() {
        List<NewsFeed.Item> items = NewsFeed.parse(FEED, 10);
        assertEquals(3, items.size());
        assertEquals("How Lawson helped Lindblad", items.get(0).title);
        assertEquals("Liam Lawson didn't venture out & more.", items.get(0).summary);
        assertEquals("Antonelli pole: 'finally seeing results'", items.get(1).title);
        assertEquals("Mercedes upgrades", items.get(1).summary);
        assertEquals("", items.get(2).summary);
    }

    @Test
    public void maxLimitsTheItems() {
        assertEquals(1, NewsFeed.parse(FEED, 1).size());
    }

    @Test
    public void entitiesDecodeAndStrayAmpersandsStay() {
        assertEquals("‘We’ & <b>", NewsFeed.decode("&#x2018;We&#8217; &amp; &lt;b&gt;"));
        assertEquals("R&D & co", NewsFeed.decode("R&D & co"));
    }

    @Test
    public void onlyWebUrlsAreFeeds() {
        assertTrue(NewsFeed.validUrl("https://www.formula1.com/en/latest/all.xml"));
        assertFalse(NewsFeed.validUrl("file:///sdcard/x.xml"));
        assertFalse(NewsFeed.validUrl(""));
    }
}
