package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The five daily prayer times by the Kemenag RI (Indonesian Ministry of Religious Affairs) method: Fajr at
 * the sun 20 degrees below the horizon, Isha at 18, Asr by the Shafi'i shadow (object length plus noon shadow),
 * and 2 minutes of ihtiyat (safety margin) added to every time, rounded up to the minute. Sun position from the
 * standard low-precision almanac equations (praytimes.org). Pure maths, no Android types and no network, like Sun.
 */
final class PrayerTimes {

    static final int SUBUH = 0, DZUHUR = 1, ASHAR = 2, MAGHRIB = 3, ISYA = 4;

    private static final double FAJR_ANGLE = 20, ISHA_ANGLE = 18;
    /** The sun's upper edge on the horizon, with refraction. */
    private static final double SUNSET_ANGLE = 0.833;
    private static final double IHTIYAT_MIN = 2;

    private PrayerTimes() {
    }

    /**
     * Local minutes of the day of Subuh, Dzuhur, Ashar, Maghrib and Isya, in that order. A time the sun never
     * reaches that day (far from the equator) is -1.
     */
    static int[] forDay(LocalDate date, double latitude, double longitude, ZoneId zone) {
        double jd = julianDay(date) - longitude / (15 * 24.0);
        double offsetHours = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 3600.0;
        double[] hours = {
                sunAngleTime(jd, 5 / 24.0, FAJR_ANGLE, latitude, true),
                noon(jd, 12 / 24.0),
                asrTime(jd, 13 / 24.0, latitude),
                sunAngleTime(jd, 18 / 24.0, SUNSET_ANGLE, latitude, false),
                sunAngleTime(jd, 18 / 24.0, ISHA_ANGLE, latitude, false)};
        int[] minutes = new int[hours.length];
        for (int i = 0; i < hours.length; i++) {
            if (Double.isNaN(hours[i])) {
                minutes[i] = -1;
                continue;
            }
            // Solar time at the meridian of the place, to the zone's clock.
            double local = hours[i] + offsetHours - longitude / 15;
            minutes[i] = Math.floorMod((int) Math.ceil(local * 60 + IHTIYAT_MIN - 1e-9), 24 * 60);
        }
        return minutes;
    }

    /** Index of the next prayer after minute of the day now, wrapping to Subuh after Isya; skips missing times. */
    static int next(int[] times, int now) {
        for (int i = 0; i < times.length; i++) {
            if (times[i] >= 0 && times[i] > now) return i;
        }
        return SUBUH;
    }

    private static double julianDay(LocalDate date) {
        int y = date.getYear(), m = date.getMonthValue(), d = date.getDayOfMonth();
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {declination in degrees, equation of time in hours} at Julian day jd. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15;
        double eqt = q / 15 - fixHour(ra);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        return new double[]{decl, eqt};
    }

    /** Solar noon in hours, dayPart being the rough fraction of the day used for the sun's position. */
    private static double noon(double jd, double dayPart) {
        return fixHour(12 - sunPosition(jd + dayPart)[1]);
    }

    /** When the sun stands angle degrees below the horizon, before noon (morning) or after it. */
    private static double sunAngleTime(double jd, double dayPart, double angle, double latitude, boolean morning) {
        double decl = sunPosition(jd + dayPart)[0];
        double cosT = (-sin(angle) - sin(decl) * sin(latitude)) / (cos(decl) * cos(latitude));
        if (cosT < -1 || cosT > 1) return Double.NaN;
        double t = Math.toDegrees(Math.acos(cosT)) / 15;
        return noon(jd, dayPart) + (morning ? -t : t);
    }

    /** Shafi'i Asr: an object's shadow equals its length plus its shadow at noon. */
    private static double asrTime(double jd, double dayPart, double latitude) {
        double decl = sunPosition(jd + dayPart)[0];
        double altitude = Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(latitude - decl))))));
        return sunAngleTime(jd, dayPart, -altitude, latitude, false);
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double fixAngle(double a) {
        return a - 360 * Math.floor(a / 360);
    }

    private static double fixHour(double h) {
        return h - 24 * Math.floor(h / 24);
    }
}
