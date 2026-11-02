package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Kemenag prayer times: Jakarta against the published schedule, ordering, and the next prayer. */
public class PrayerTimesTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final double LAT = -6.2088, LON = 106.8456;

    /** Bimas Islam (Kemenag RI) schedule for Jakarta; within a minute, as its own ihtiyat differs a little. */
    private static void matches(LocalDate date, String... expected) {
        int[] t = PrayerTimes.forDay(date, LAT, LON, JAKARTA);
        for (int i = 0; i < expected.length; i++) {
            String[] hm = expected[i].split(":");
            int want = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
            assertTrue(date + " #" + i + " " + t[i] / 60 + ":" + t[i] % 60, Math.abs(want - t[i]) <= 1);
        }
    }

    @Test
    public void jakartaOctober() {
        matches(LocalDate.of(2026, 10, 1), "04:23", "11:46", "14:51", "17:50", "18:59");
        matches(LocalDate.of(2026, 10, 22), "04:13", "11:41", "14:50", "17:49", "18:59");
        matches(LocalDate.of(2026, 10, 31), "04:09", "11:40", "14:54", "17:49", "19:00");
    }

    @Test
    public void jakartaMidJuly() {
        matches(LocalDate.of(2026, 7, 15), "04:45", "12:02", "15:23", "17:56", "19:09");
    }

    @Test
    public void timesAreInOrder() {
        for (int day = 1; day <= 365; day += 7) {
            int[] t = PrayerTimes.forDay(LocalDate.ofYearDay(2026, day), LAT, LON, JAKARTA);
            for (int i = 1; i < t.length; i++) assertTrue("day " + day, t[i] > t[i - 1]);
        }
    }

    @Test
    public void nextPrayerWrapsToSubuhAfterIsya() {
        int[] t = {250, 700, 887, 1070, 1139};
        assertEquals(PrayerTimes.SUBUH, PrayerTimes.next(t, 100));
        assertEquals(PrayerTimes.DZUHUR, PrayerTimes.next(t, 250));
        assertEquals(PrayerTimes.ISYA, PrayerTimes.next(t, 1100));
        assertEquals(PrayerTimes.SUBUH, PrayerTimes.next(t, 1200));
    }

    @Test
    public void missingTimesAreMinusOneFarNorth() {
        int[] t = PrayerTimes.forDay(LocalDate.of(2026, 6, 21), 64.0, 25.0, ZoneId.of("Europe/Helsinki"));
        assertEquals(-1, t[PrayerTimes.ISYA]);
    }
}
