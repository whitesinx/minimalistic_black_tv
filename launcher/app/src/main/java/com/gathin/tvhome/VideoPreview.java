package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * The video card's moving picture (config youtube_preview): the card's video muted and looping in YouTube's embed
 * player, laid over the thumbnail only once it is really playing. A video whose owner blocks embedding, or no
 * network, just leaves the thumbnail. The WebView exists only while Home shows the card, so a 2 GB box gets its
 * memory back as soon as an app opens.
 */
final class VideoPreview {
    private static final String TAG = "TvHome.Preview";
    /** YouTube's title bar sits on the player for its first seconds; the picture is shown after that. */
    private static final long REVEAL_DELAY_MS = 2500;

    private final FrameLayout card;
    private final View hideWhenPlaying;
    /** Told the id of a video that cannot play in the card, so another can take its place. */
    private final java.util.function.Consumer<String> onFailed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private String videoId;
    /** A video that would not play in the embed; not tried again until another one comes. */
    private String failedId;

    VideoPreview(FrameLayout card, View hideWhenPlaying, java.util.function.Consumer<String> onFailed) {
        this.card = card;
        this.hideWhenPlaying = hideWhenPlaying;
        this.onFailed = onFailed;
    }

    /** Plays id in the card, or keeps playing when it already is. */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    void play(String id) {
        if (id.equals(failedId)) return;
        if (web != null && id.equals(videoId)) return;
        stop();
        videoId = id;
        WebView w;
        try {
            w = new WebView(card.getContext());
        } catch (RuntimeException e) {
            Log.w(TAG, "no WebView: " + e.getMessage());   // WebView being updated or missing
            failedId = id;
            return;
        }
        web = w;
        w.setAlpha(0f);
        w.setBackgroundColor(Color.BLACK);
        w.setFocusable(false);
        w.setFocusableInTouchMode(false);
        w.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        w.setWebChromeClient(new WebChromeClient());
        w.addJavascriptInterface(new Bridge(w), "Tv");
        // Above the thumbnail, under the scrim and the title.
        card.addView(w, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The embed wants a page origin to send as Referer (no Referer: "error 153"); the page is local, the origin
        // only names it.
        w.loadDataWithBaseURL("https://com.gathin.tvhome/", page(id), "text/html", "utf-8", null);
    }

    /** Gone, and its memory with it; the thumbnail shows again. */
    void stop() {
        handler.removeCallbacksAndMessages(null);
        hideWhenPlaying.setAlpha(1f);
        if (web == null) return;
        WebView w = web;
        web = null;
        card.removeView(w);
        w.stopLoading();
        w.loadUrl("about:blank");
        w.destroy();
    }

    /** The video id is checked before it gets here (YouTubeFeed's pattern), so it is safe inside the page. */
    private static String page(String id) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                // Slightly larger than the card so YouTube's corner badges fall outside it.
                + "#p{position:absolute;left:-6%;top:-6%;width:112%;height:112%;border:0;pointer-events:none}</style>"
                + "</head><body><div id=p></div>"
                + "<script>"
                + "function onYouTubeIframeAPIReady(){new YT.Player('p',{videoId:'" + id + "',"
                + "playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,loop:1,playlist:'" + id + "',"
                + "playsinline:1,rel:0,origin:'https://com.gathin.tvhome'},"
                + "events:{onReady:function(e){e.target.mute();e.target.playVideo();},"
                + "onStateChange:function(e){Tv.state(e.data);},"
                + "onError:function(e){Tv.error(e.data);}}});}"
                + "</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
    }

    /** Called from the page on a WebView thread; everything goes back to the main thread. */
    private final class Bridge {
        private final WebView owner;

        Bridge(WebView owner) {
            this.owner = owner;
        }

        @JavascriptInterface
        public void state(int state) {
            handler.post(() -> {
                if (web != owner) return;
                if (state == 1 && owner.getAlpha() == 0f) {          // YT.PlayerState.PLAYING
                    handler.postDelayed(() -> {
                        if (web != owner) return;
                        owner.animate().alpha(1f).setDuration(700).start();
                        hideWhenPlaying.animate().alpha(0f).setDuration(400).start();
                    }, REVEAL_DELAY_MS);
                }
            });
        }

        @JavascriptInterface
        public void error(int code) {
            handler.post(() -> {
                if (web != owner) return;
                // 101/150: the owner does not allow embedding; 2/5/100: bad id, HTML5 error, removed.
                Log.w(TAG, "embed error " + code + " for " + videoId);
                String failed = videoId;
                failedId = failed;
                stop();
                onFailed.accept(failed);
            });
        }
    }
}
