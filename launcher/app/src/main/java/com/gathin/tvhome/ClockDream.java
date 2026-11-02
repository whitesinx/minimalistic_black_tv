package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene), or the
 * prayer-time clock (PrayerScene) with config.xml prayer_times. No network, no images: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private DreamScene scene;

    /** config.xml ui_density_dpi: the screensaver at the same smaller scale as the home screen. */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(withUiDensity(base));
    }

    static Context withUiDensity(Context base) {
        int dpi = base.getResources().getInteger(R.integer.ui_density_dpi);
        if (dpi <= 0) return base;
        DisplayMetrics m = base.getResources().getDisplayMetrics();
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.densityDpi = dpi;
        c.screenWidthDp = Math.round(m.widthPixels * 160f / dpi);
        c.screenHeightDp = Math.round(m.heightPixels * 160f / dpi);
        c.smallestScreenWidthDp = Math.min(c.screenWidthDp, c.screenHeightDp);
        return base.createConfigurationContext(c);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        scene = DreamScene.show(this, getWindow());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        super.onDetachedFromWindow();
    }
}
