package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The RAM and network speed pill's text. */
public class NetStatsTest {

    @Test
    public void ratesPickTheirUnit() {
        assertEquals("0 B/s", NetStats.rate(0));
        assertEquals("900 B/s", NetStats.rate(900));
        assertEquals("40 KB/s", NetStats.rate(40 * 1024 + 300));
        assertEquals("1.2 MB/s", NetStats.rate(1_258_291));
        assertEquals("–", NetStats.rate(-1));
    }

    @Test
    public void lineReadsLikeTheSpec() {
        long gb = 1024L * 1024 * 1024;
        assertEquals("RAM 0.8/1.5 GB   ↓ 1.2 MB/s  ↑ 40 KB/s",
                NetStats.line(gb * 8 / 10, gb * 3 / 2, 1_258_291, 40 * 1024));
    }

    @Test
    public void perSecondOverTwoSeconds() {
        assertEquals(1000, NetStats.perSecond(5000, 7000, 2000));
        assertEquals(-1, NetStats.perSecond(-1, 7000, 2000));    // TrafficStats.UNSUPPORTED
        assertEquals(-1, NetStats.perSecond(9000, 7000, 2000));  // counters reset
    }

    @Test
    public void networkOnlyLineLeavesRamOut() {
        org.junit.Assert.assertEquals("↓ 1.2 MB/s  ↑ 40 KB/s", NetStats.line(1_258_291, 40 * 1024));
    }
}
