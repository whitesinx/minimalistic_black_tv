package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

/**
 * Bigger volume steps (config.xml volume_step): when one press moves the media volume by 1, this moves it on to
 * volume_step in the same direction. For TVs whose volume runs 0-100 one step a press, where holding the button is
 * the only quick way, and the key helper (InputKeyService) stops held keys repeating (MiTV-AXSO0, Android 9). The
 * volume keys never reach the helper there, so this watches the volume itself instead of the keys.
 */
final class VolumeSteps extends BroadcastReceiver {

    // Hidden AudioManager extras, sent with every volume change since Android 4.
    private static final String ACTION = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private static final String EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    private final AudioManager audio;
    private final int step;
    /** The value this receiver last set, so the broadcast its own change causes is not stepped again. */
    private int expected = -1;

    private VolumeSteps(Context context, int step) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.step = step;
    }

    /** Registers on context when volume_step is above 1; returns the receiver to unregister, or null. */
    static VolumeSteps register(Context context) {
        int step = context.getResources().getInteger(R.integer.volume_step);
        if (step <= 1) return null;
        VolumeSteps receiver = new VolumeSteps(context, step);
        context.registerReceiver(receiver, new IntentFilter(ACTION));
        return receiver;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC || audio == null) return;
        int value = intent.getIntExtra(EXTRA_VALUE, -1);
        int prev = intent.getIntExtra(EXTRA_PREV, -1);
        if (value == expected) {
            expected = -1;
            return;
        }
        int target = target(prev, value, step, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        if (target == value) return;
        expected = target;
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
    }

    /** Where a one-step change from prev to value should end: step in that direction, within 0..max. */
    static int target(int prev, int value, int step, int max) {
        if (prev < 0 || value < 0 || Math.abs(value - prev) != 1) return value;
        return Math.max(0, Math.min(max, prev + (value - prev) * step));
    }
}
