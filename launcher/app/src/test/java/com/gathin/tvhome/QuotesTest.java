package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class QuotesTest {

    @Test
    public void authorAfterTheDash() {
        assertArrayEquals(new String[]{"Love one another.", "Kahlil Gibran"},
                Quotes.split("Love one another. — Kahlil Gibran", "Haemin Sunim"));
    }

    @Test
    public void noAuthorUsesTheFallback() {
        assertArrayEquals(new String[]{"Be good to yourself first.", "Haemin Sunim"},
                Quotes.split("Be good to yourself first.", "Haemin Sunim"));
        assertArrayEquals(new String[]{"A dash at the end —", ""}, Quotes.split("A dash at the end —", ""));
    }
}
