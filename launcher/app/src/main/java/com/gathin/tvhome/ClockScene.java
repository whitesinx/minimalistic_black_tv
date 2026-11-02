package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene implements DreamScene {

    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    /** false (screensaver "digital"): plain black, the clock still drifts so nothing burns in. */
    private final boolean withGlow;
    /** true (screensaver "digital"): the clock stays in the middle, nudged a few pixels a minute instead of drifting. */
    private final boolean fixed;
    private int colorIndex;
    /** withQuotes: the quote under the clock and its author line; null in layouts without them. */
    private TextView quote, quoteBy;
    private java.util.List<String> quotes = java.util.Collections.emptyList();
    private int quoteIndex;
    /** screensaver_quote_by: the author for quotes that do not name their own. */
    private String quoteAuthor = "";
    private int minutes;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (fixed) placeClock();
            else moveClock();
            if (withGlow) shiftGlow();
            if (quotes.size() > 1 && ++minutes % QUOTE_MINUTES == 0) nextQuote();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock (or dream_digital) layout. */
    ClockScene(View root, boolean withGlow, boolean fixed) {
        this.withGlow = withGlow;
        this.fixed = fixed;
        content = root.findViewById(R.id.dreamContent);
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        if (withGlow) ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    /** A new quote every this many minutes. */
    private static final int QUOTE_MINUTES = 10;

    /** Shows quotes under the clock (screensaver_quotes), starting from a random one; none leaves the clock alone. */
    ClockScene withQuotes(java.util.List<String> quotes, String by) {
        View root = content.getRootView();
        quote = root.findViewById(R.id.dreamQuote);
        quoteBy = root.findViewById(R.id.dreamQuoteBy);
        if (quote == null || quotes.isEmpty()) return this;
        this.quotes = quotes;
        quoteAuthor = by;
        quoteIndex = random.nextInt(quotes.size());
        showQuote();
        quote.setVisibility(View.VISIBLE);
        return this;
    }

    private void nextQuote() {
        quoteIndex = (quoteIndex + 1) % quotes.size();
        if (quoteBy != null) quoteBy.animate().alpha(0f).setDuration(900).start();
        quote.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            showQuote();
            quote.animate().alpha(1f).setDuration(1200).start();
            if (quoteBy != null) quoteBy.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** The current quote, and its author under it: its own (" — Author" at the end) or screensaver_quote_by. */
    private void showQuote() {
        String[] parts = Quotes.split(quotes.get(quoteIndex), quoteAuthor);
        quote.setText(parts[0]);
        if (quoteBy == null) return;
        quoteBy.setText(parts[1].isEmpty() ? "" : "\u2014 " + parts[1]);
        quoteBy.setVisibility(parts[1].isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void stop() {
        handler.removeCallbacks(tick);
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        if (fixed) {
            // Centred by layout_gravity; up to 3dp either way so the same pixels are not lit for hours.
            float nudge = 3 * content.getResources().getDisplayMetrics().density;
            content.setTranslationX((random.nextFloat() * 2 - 1) * nudge);
            content.setTranslationY((random.nextFloat() * 2 - 1) * nudge);
            return;
        }
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
