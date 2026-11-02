package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.util.Arrays;

/**
 * white_icons: an app's icon as a flat white glyph. The monochrome layer of an adaptive icon (Android 13+)
 * when the app ships one, else the shape of its foreground layer, else the alpha of the whole icon, and for
 * an icon with no transparency at all, whatever stands out from its background colour. The result is an
 * ALPHA_8 bitmap tinted white, so a tile costs size*size bytes.
 */
final class WhiteIcon {
    private static final String TAG = "TvHome.WhiteIcon";

    /** Share of the square a glyph may cover before it counts as a solid block (an icon with no transparency). */
    static final float SOLID_COVERAGE = 0.85f;

    private WhiteIcon() {
    }

    /** The tile behind a white icon: the background a shade darker (12%), opaque. */
    static int tileColor(int background) {
        int r = Math.round(((background >> 16) & 0xFF) * 0.88f);
        int g = Math.round(((background >> 8) & 0xFF) * 0.88f);
        int b = Math.round((background & 0xFF) * 0.88f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static boolean isSolid(int covered, int total) {
        return total > 0 && covered >= total * SOLID_COVERAGE;
    }

    /** A white glyph of the icon at sizePx square, or null when there is no icon to draw. */
    static Drawable make(Resources res, Drawable icon, int sizePx, String pkg) {
        if (icon == null || sizePx <= 0) return null;
        Drawable src = icon;
        String source = "icon";
        boolean adaptive = icon instanceof AdaptiveIconDrawable;
        if (adaptive) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Drawable mono = Build.VERSION.SDK_INT >= 33 ? a.getMonochrome() : null;
            // The background layer is opaque by design, so the foreground holds the shape.
            src = mono != null ? mono : a.getForeground();
            source = mono != null ? "monochrome" : "foreground";
            if (src == null) return null;
        }
        Bitmap art = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(art);
        if (adaptive) {
            // A layer is 108dp with the visible 72dp in its middle: let a sixth spill off each side.
            int spill = sizePx / 4;
            src.setBounds(-spill, -spill, sizePx + spill, sizePx + spill);
        } else {
            src.setBounds(0, 0, sizePx, sizePx);
        }
        src.draw(canvas);
        int total = sizePx * sizePx;
        int[] px = new int[total];
        art.getPixels(px, 0, sizePx, 0, 0, sizePx, sizePx);
        art.recycle();
        if (isSolid(coverage(px), total)) {
            // An icon with no transparency (most legacy TV icons) would be a white square: keep only what stands
            // out from its own background, the colour that fills most of its edge.
            source += " minus background";
            glyph(px, sizePx);
        } else if (cutLight(px)) {
            source += " with light cut-outs";
        }
        int covered = coverage(px);
        boolean bad = isSolid(covered, total) || covered < total * 0.02f;
        // Logged for every app so a bad glyph can be traced to its source: logcat -s TvHome.WhiteIcon
        Log.i(TAG, pkg + ": " + source + (bad ? ", BAD" : "") + " coverage " + (100 * covered / total) + "%");
        Bitmap full = Bitmap.createBitmap(px, sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Bitmap fitted = fit(full, px, sizePx);
        Bitmap alpha = fitted.extractAlpha();
        if (fitted != full) fitted.recycle();
        full.recycle();
        BitmapDrawable out = new BitmapDrawable(res, alpha);
        out.setTint(Color.WHITE);
        return out;
    }

    /**
     * The glyph cropped to what it draws and scaled until its longer side is the square's, so a wordmark that a
     * legacy icon pads to a third of its width comes out as large as any other glyph. The bitmap keeps the
     * glyph's own shape: the tile's icon view is wider than tall, and a wordmark uses that width.
     */
    private static Bitmap fit(Bitmap full, int[] px, int size) {
        int[] box = bounds(px, size);
        if (box == null) return full;
        int w = box[2] - box[0], h = box[3] - box[1];
        float scale = size / (float) Math.max(w, h);
        int dw = Math.max(1, Math.round(w * scale)), dh = Math.max(1, Math.round(h * scale));
        Bitmap out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(full, new Rect(box[0], box[1], box[2], box[3]), new Rect(0, 0, dw, dh),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** {left, top, right, bottom} (right and bottom exclusive) of the pixels above a faint alpha, or null for none. */
    static int[] bounds(int[] px, int size) {
        int l = size, t = size, r = -1, b = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((px[y * size + x] >>> 24) <= 0x30) continue;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < 0 ? null : new int[]{l, t, r + 1, b + 1};
    }

    /**
     * A coloured glyph with white details on it (SmartTube's white S on a red TV) would come out as one white
     * blob: turn those near-white, unsaturated pixels into holes. Skipped for light icons, and when the light part
     * is a speck or most of the icon. Returns whether anything was cut.
     */
    static boolean cutLight(int[] px) {
        int opaque = 0, light = 0;
        double lumSum = 0;
        for (int p : px) {
            if ((p >>> 24) <= 0x80) continue;
            opaque++;
            double lum = lum(p);
            lumSum += lum;
            if (lum > 0.85 && sat(p) < 48) light++;
        }
        if (opaque == 0 || lumSum / opaque > 0.6 || light < opaque * 0.03f || light > opaque * 0.45f) return false;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if (sat(p) >= 48) continue;
            double keep = 1 - Math.max(0, Math.min(1, (lum(p) - 0.65) / 0.2));
            px[i] = ((int) Math.round((p >>> 24) * keep)) << 24 | (p & 0xFFFFFF);
        }
        return true;
    }

    private static double lum(int p) {
        return (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF)) / 255;
    }

    private static int sat(int p) {
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private static int coverage(int[] px) {
        int covered = 0;
        for (int p : px) if ((p >>> 24) > 0x80) covered++;
        return covered;
    }

    /**
     * Replaces each pixel's alpha with how far its colour is from the nearest background colour (edgeColors):
     * nothing within 40 of one, full from 120 up, a ramp between for anti-aliased edges.
     */
    static void glyph(int[] px, int size) {
        int[] bg = edgeColors(px, size);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int d = 255;
            for (int c : bg) {
                d = Math.min(d, Math.max(Math.abs(((p >> 16) & 0xFF) - ((c >> 16) & 0xFF)),
                        Math.max(Math.abs(((p >> 8) & 0xFF) - ((c >> 8) & 0xFF)), Math.abs((p & 0xFF) - (c & 0xFF)))));
            }
            int a = Math.max(0, Math.min(255, (d - 40) * 255 / 80)) * (p >>> 24) / 255;
            px[i] = a << 24 | 0xFFFFFF;
        }
    }

    /**
     * Every colour (to 4 bits a channel, averaged) that makes up at least 3% of the opaque pixels of the outermost
     * ring: one for a flat background, several along a gradient (Disney+ Hotstar fades from navy to teal).
     */
    static int[] edgeColors(int[] px, int size) {
        int[] count = new int[4096];
        long[] r = new long[4096], g = new long[4096], b = new long[4096];
        int ring = 0;
        for (int i = 0; i < size; i++) {
            for (int idx : new int[]{i, (size - 1) * size + i, i * size, i * size + size - 1}) {
                int p = px[idx];
                if ((p >>> 24) <= 0x80) continue;
                int k = (p >> 12 & 0xF00) | (p >> 8 & 0xF0) | (p >> 4 & 0xF);
                count[k]++;
                r[k] += (p >> 16) & 0xFF;
                g[k] += (p >> 8) & 0xFF;
                b[k] += p & 0xFF;
                ring++;
            }
        }
        int[] out = new int[64];
        int n = 0;
        for (int k = 0; k < count.length && n < out.length; k++) {
            if (count[k] == 0 || count[k] < ring * 0.03f) continue;
            out[n++] = (int) (0xFF000000L | (r[k] / count[k]) << 16 | (g[k] / count[k]) << 8 | (b[k] / count[k]));
        }
        return n == 0 ? new int[]{0xFF000000} : Arrays.copyOf(out, n);
    }
}
