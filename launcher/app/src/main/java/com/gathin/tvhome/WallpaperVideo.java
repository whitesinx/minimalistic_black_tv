package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A theme's wallpaper.mp4, muted and looping behind Home, centre-cropped to the screen. It plays only while Home
 * is resumed (WallpaperPlayer starts and stops it) and fades in over the still wallpaper once the first frame is
 * ready, so a file the TV cannot decode just leaves the picture in place.
 */
final class WallpaperVideo implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TvHome.Video";

    private final TextureView view;
    private final File file;
    private MediaPlayer player;
    private Surface surface;
    private boolean wanted;
    private boolean failed;
    private int videoW, videoH;

    WallpaperVideo(Context context, File file) {
        this.file = file;
        view = new TextureView(context);
        view.setAlpha(0f);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setSurfaceTextureListener(this);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> crop());
    }

    View view() {
        return view;
    }

    void start() {
        wanted = true;
        if (player != null) {
            player.start();
        } else if (surface != null) {
            prepare();
        }
    }

    void stop() {
        wanted = false;
        if (player != null && player.isPlaying()) player.pause();
    }

    void close() {
        wanted = false;
        release();
        if (surface != null) surface.release();
        surface = null;
    }

    private void prepare() {
        if (failed || player != null) return;
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try (FileInputStream in = new FileInputStream(file)) {
            mp.setDataSource(in.getFD());
        } catch (IOException | RuntimeException e) {
            fail("cannot open " + file + ": " + e.getMessage());
            return;
        }
        mp.setSurface(surface);
        mp.setLooping(true);
        mp.setVolume(0f, 0f);
        mp.setOnVideoSizeChangedListener((p, w, h) -> {
            videoW = w;
            videoH = h;
            crop();
        });
        mp.setOnPreparedListener(p -> {
            if (wanted) p.start();
        });
        mp.setOnInfoListener((p, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) view.animate().alpha(1f).setDuration(600).start();
            return false;
        });
        mp.setOnErrorListener((p, what, extra) -> {
            fail("playback error " + what + "/" + extra);
            return true;
        });
        mp.prepareAsync();
    }

    /** Gives up on the video for this Home session; the still wallpaper (or plain colour) stays. */
    private void fail(String why) {
        Log.w(TAG, why);
        failed = true;
        view.setAlpha(0f);
        release();
    }

    private void release() {
        if (player == null) return;
        player.release();
        player = null;
    }

    /** Centre crop: scale the video until it covers the view on both axes, as the still wallpaper does. */
    private void crop() {
        int w = view.getWidth(), h = view.getHeight();
        if (w == 0 || h == 0 || videoW == 0 || videoH == 0) return;
        float scale = Math.max(w / (float) videoW, h / (float) videoH);
        Matrix m = new Matrix();
        m.setScale(videoW * scale / w, videoH * scale / h, w / 2f, h / 2f);
        view.setTransform(m);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        if (wanted) prepare();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        // Home went to the background and the window dropped its layers: let go of the decoder too.
        release();
        view.setAlpha(0f);
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        crop();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }
}
