package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WhiteIconTest {
    @Test
    public void tileIsTheBackgroundAShadeDarker() {
        assertEquals(0xFFCC577E, WhiteIcon.tileColor(0xFFE8638F));
        assertEquals(0xFF000000, WhiteIcon.tileColor(0xFF000000));
        assertEquals(0xFFE0E0E0, WhiteIcon.tileColor(0xFFFFFFFF));
    }

    @Test
    public void onlyANearlyFullSquareCountsAsSolid() {
        assertTrue(WhiteIcon.isSolid(100, 100));
        assertTrue(WhiteIcon.isSolid(85, 100));
        assertFalse(WhiteIcon.isSolid(60, 100));
        assertFalse(WhiteIcon.isSolid(0, 0));
    }

    @Test
    public void aSolidIconKeepsOnlyWhatStandsOutFromItsEdge() {
        // 4x4 black square with a red 2x2 middle: the edge is black, so only the middle stays.
        int k = 0xFF000000, r = 0xFFE50914;
        int[] px = {k, k, k, k, k, r, r, k, k, r, r, k, k, k, k, k};
        assertEquals(1, WhiteIcon.edgeColors(px, 4).length);
        assertEquals(0xFF000000, WhiteIcon.edgeColors(px, 4)[0]);
        WhiteIcon.glyph(px, 4);
        assertEquals(0, px[0] >>> 24);
        assertEquals(255, px[5] >>> 24);
        assertEquals(0xFFFFFF, px[5] & 0xFFFFFF);
    }

    @Test
    public void boundsCoverOnlyTheDrawnPixels() {
        int[] px = new int[16];
        px[5] = 0xFFFFFFFF;
        px[10] = 0xFFFFFFFF;
        assertArrayEquals(new int[]{1, 1, 3, 3}, WhiteIcon.bounds(px, 4));
        assertNull(WhiteIcon.bounds(new int[16], 4));
    }
}
