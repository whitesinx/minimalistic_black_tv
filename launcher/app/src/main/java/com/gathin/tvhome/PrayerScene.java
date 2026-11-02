package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Random;

/**
 * The prayer-time screensaver (layout dream_prayer): a large thin 24-hour clock in the centre of a pure black
 * screen, today's five prayer times in a row under it with the next one lit. Computed on the TV (PrayerTimes)
 * from latitude, longitude and prayer_timezone in config.xml; no network. Every minute the block moves a few
 * pixels around the centre so nothing burns in.
 */
final class PrayerScene implements DreamScene {

    private static final long TICK_MS = 60_000L;
    /** How far the block may drift from the centre, in dp. */
    private static final int DRIFT_DP = 14;
    private static final int LIT = 0xFFFFFFFF, DIM = 0x73FFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final TextView[] names = new TextView[5];
    private final TextView[] times = new TextView[5];
    private final LinearLayout[] cells = new LinearLayout[5];
    private final GradientDrawable litBackground = new GradientDrawable();
    private final float density;
    private final double latitude, longitude;
    private final boolean placed;
    /** config.xml screensaver_drift: false keeps the block still in the exact centre. */
    private final boolean driftOn;
    private final ZoneId zone;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (driftOn) drift();
            // On the minute, so the highlight moves on when a prayer time arrives.
            handler.postDelayed(this, TICK_MS - System.currentTimeMillis() % TICK_MS);
        }
    };

    PrayerScene(Context context, View root) {
        Resources res = context.getResources();
        density = res.getDisplayMetrics().density;
        driftOn = res.getBoolean(R.bool.screensaver_drift);
        if (!driftOn) {
            // Still, in the exact centre of what is seen: the font keeps room above the digits for taller letters,
            // which would centre the block low by half that room.
            TextView clock = (TextView) ((LinearLayout) root.findViewById(R.id.prayerContent)).getChildAt(0);
            android.graphics.Rect digits = new android.graphics.Rect();
            clock.getPaint().getTextBounds("0123456789", 0, 10, digits);
            float roomAbove = digits.top - clock.getPaint().getFontMetrics().ascent;
            root.findViewById(R.id.prayerContent).setTranslationY(-roomAbove / 2f);
        }
        content = root.findViewById(R.id.prayerContent);
        double lat = 0, lon = 0;
        boolean ok;
        try {
            lat = Double.parseDouble(res.getString(R.string.latitude));
            lon = Double.parseDouble(res.getString(R.string.longitude));
            ok = true;
        } catch (NumberFormatException e) {
            ok = false;
        }
        latitude = lat;
        longitude = lon;
        placed = ok;
        ZoneId z;
        try {
            z = ZoneId.of(res.getString(R.string.prayer_timezone));
        } catch (RuntimeException e) {
            z = ZoneId.systemDefault();
        }
        zone = z;

        litBackground.setCornerRadius(12 * density);
        litBackground.setStroke(Math.max(1, Math.round(density)), 0x59FFFFFF);
        litBackground.setColor(0x14FFFFFF);

        LinearLayout row = root.findViewById(R.id.prayerRow);
        String[] labels = res.getStringArray(R.array.prayer_names);
        for (int i = 0; i < cells.length; i++) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int padH = Math.round(18 * density), padV = Math.round(8 * density);
            cell.setPadding(padH, padV, padH, padV);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(i == 0 ? 0 : Math.round(6 * density));
            names[i] = label(context, labels[i], 14, R.font.outfit_medium, 0.14f);
            times[i] = label(context, "--:--", 26, R.font.outfit_light, 0.02f);
            cell.addView(names[i]);
            cell.addView(times[i]);
            row.addView(cell, lp);
            cells[i] = cell;
        }
    }

    private static TextView label(Context context, String text, int sp, int font, float spacing) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTypeface(context.getResources().getFont(font));
        v.setLetterSpacing(spacing);
        v.setAllCaps(sp < 20);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        return v;
    }

    @Override
    public void start() {
        refresh();
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(1200).start();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS - System.currentTimeMillis() % TICK_MS);
    }

    @Override
    public void stop() {
        handler.removeCallbacks(tick);
    }

    /** Today's times, recomputed each minute so the row follows midnight. */
    private void refresh() {
        int lit = -1;
        int[] t = null;
        if (placed) {
            LocalDate today = LocalDate.now(zone);
            LocalTime now = LocalTime.now(zone);
            int minute = now.getHour() * 60 + now.getMinute();
            t = PrayerTimes.forDay(today, latitude, longitude, zone);
            lit = PrayerTimes.next(t, minute);
            // After Isya the next prayer is tomorrow's Subuh, so show that one.
            if (lit == PrayerTimes.SUBUH && minute >= t[PrayerTimes.SUBUH]) {
                t[PrayerTimes.SUBUH] = PrayerTimes.forDay(today.plusDays(1), latitude, longitude, zone)[PrayerTimes.SUBUH];
            }
        }
        for (int i = 0; i < cells.length; i++) {
            int m = t == null ? -1 : t[i];
            times[i].setText(m < 0 ? "--:--" : String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60));
            boolean on = i == lit;
            names[i].setTextColor(on ? LIT : DIM);
            times[i].setTextColor(on ? LIT : DIM);
            cells[i].setBackground(on ? litBackground : null);
        }
    }

    /** A new spot within DRIFT_DP of the centre (the layout centres the block; this only offsets it). */
    private void drift() {
        float range = DRIFT_DP * density;
        content.animate()
                .translationX((random.nextFloat() * 2 - 1) * range)
                .translationY((random.nextFloat() * 2 - 1) * range)
                .setDuration(2000).start();
    }
}
