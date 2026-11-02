package com.gathin.tvhome;

import android.content.Context;
import android.view.Window;

/**
 * What the clock screensaver shows, for ClockDream and ClockActivity alike: the prayer-time clock
 * (config.xml prayer_times), the drifting clock over an ambient glow, or (screensaver "digital") a 24-hour
 * clock alone on black.
 */
interface DreamScene {

    void start();

    void stop();

    /** Sets the window's layout and returns the scene that drives it. */
    static DreamScene show(Context context, Window window) {
        Config cfg = Config.load(context);
        if ("digital".equals(cfg.screensaver)) {
            window.setContentView(R.layout.dream_digital);
            return new ClockScene(window.getDecorView(), false, true)
                    .withQuotes(cfg.screensaverQuotes, cfg.screensaverQuoteBy);
        }
        if (context.getResources().getBoolean(R.bool.prayer_times)) {
            window.setContentView(R.layout.dream_prayer);
            return new PrayerScene(context, window.getDecorView());
        }
        window.setContentView(R.layout.dream_clock);
        return new ClockScene(window.getDecorView(), true, false);
    }
}
