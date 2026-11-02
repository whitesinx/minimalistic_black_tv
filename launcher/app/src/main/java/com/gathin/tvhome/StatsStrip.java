package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * A small infographic under the clock (config.xml stats_strip), in the clock's type instead of a top bar pill:
 * MEMORY, NETWORK and STORAGE, each a tiny caption, a value and (memory, storage) a hairline meter.
 */
final class StatsStrip {
    private static final double GB = 1024.0 * 1024 * 1024;

    private final TextView memValue, netValue, diskValue;
    private final Meter memMeter, diskMeter;

    StatsStrip(LinearLayout into) {
        Context c = into.getContext();
        into.removeAllViews();
        into.setOrientation(LinearLayout.HORIZONTAL);
        memValue = value(c);
        netValue = value(c);
        // Fixed width, so Storage does not move each time the speeds change length.
        netValue.setMinWidth(Math.round(dp(c, 128)));
        diskValue = value(c);
        memMeter = new Meter(c);
        diskMeter = new Meter(c);
        into.addView(item(c, "MEMORY", memValue, memMeter, false));
        into.addView(divider(c));
        into.addView(item(c, "NETWORK", netValue, null, false));
        into.addView(divider(c));
        into.addView(item(c, "STORAGE", diskValue, diskMeter, true));
    }

    void update(long memUsed, long memTotal, long rxPerSec, long txPerSec, long diskFree, long diskTotal) {
        memValue.setText(String.format(Locale.ROOT, "%.1f / %.1f GB", memUsed / GB, memTotal / GB));
        memMeter.set(memTotal > 0 ? (float) memUsed / memTotal : 0f);
        netValue.setText("↓ " + NetStats.rate(rxPerSec) + "   ↑ " + NetStats.rate(txPerSec));
        diskValue.setText(String.format(Locale.ROOT, "%.1f GB free", diskFree / GB));
        diskMeter.set(diskTotal > 0 ? 1f - (float) diskFree / diskTotal : 0f);
    }

    private static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    private static TextView value(Context c) {
        TextView t = new TextView(c);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(c.getResources().getFont(R.font.outfit_regular));
        t.setFontFeatureSettings("tnum");
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static View item(Context c, String caption, TextView value, Meter meter, boolean last) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView cap = new TextView(c);
        cap.setText(caption);
        cap.setTextColor(0x8CFFFFFF);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        cap.setLetterSpacing(0.22f);
        cap.setTypeface(c.getResources().getFont(R.font.outfit_medium));
        cap.setIncludeFontPadding(false);
        box.addView(cap);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Math.round(dp(c, 5));
        box.addView(value, vlp);
        // Network has no meter: an invisible one of the same size keeps the three items the same height and level.
        View bar = meter;
        if (bar == null) {
            bar = new View(c);
            bar.setVisibility(View.INVISIBLE);
        }
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(meter != null ? Math.round(dp(c, 84)) : 1,
                Math.round(dp(c, 2)));
        mlp.topMargin = Math.round(dp(c, 6));
        box.addView(bar, mlp);
        box.setGravity(Gravity.START);
        return box;
    }

    private static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(dp(c, 1)), Math.round(dp(c, 26)));
        lp.leftMargin = lp.rightMargin = Math.round(dp(c, 16));
        lp.gravity = Gravity.CENTER_VERTICAL;
        v.setLayoutParams(lp);
        return v;
    }

    /** A 2dp track with the used part in white. */
    private static final class Meter extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fraction;

        Meter(Context c) {
            super(c);
            track.setColor(0x26FFFFFF);
            fill.setColor(0xCCFFFFFF);
        }

        void set(float f) {
            f = Math.max(0f, Math.min(1f, f));
            if (Math.abs(f - fraction) < 0.002f) return;
            fraction = f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight(), rad = h / 2f;
            r.set(0, 0, getWidth(), h);
            canvas.drawRoundRect(r, rad, rad, track);
            r.right = getWidth() * fraction;
            if (r.right > 0) canvas.drawRoundRect(r, rad, rad, fill);
        }
    }
}
