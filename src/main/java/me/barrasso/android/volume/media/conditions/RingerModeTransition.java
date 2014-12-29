package me.barrasso.android.volume.media.conditions;

import static android.media.AudioManager.*;
import android.media.AudioManager;

/**
 * Similar to a {@link com.android.internal.util.Predicate} that checks
 * the order of the system ringer mode transition.
 */
public class RingerModeTransition {

    public RingerModeTransition() { }

    /**
     * @return An ordered list of the ringer modes, from
     * highest to lowest (volumes [1], [0], [0] again).
     */
    public int[] apply(AudioManager manager) {
        // The algorithm here is to go from volume 0 (again) => 0 => 1,
        // and with each change, record the ringer modes.
        final int[] MODES = new int[3];
        MODES[0] = RINGER_MODE_NORMAL;
        final int STREAM = STREAM_RING;
        final int MODE = manager.getRingerMode();
        final int startVolume = manager.getStreamVolume(STREAM);
        // API quirk: volume must be decremented from 1 to get ringer mode change
        manager.setStreamVolume(STREAM, 1, FLAG_SHOW_UI);
        manager.setRingerMode(RINGER_MODE_NORMAL);
        manager.adjustStreamVolume(STREAM, ADJUST_LOWER, FLAG_SHOW_UI & FLAG_ALLOW_RINGER_MODES);
        manager.adjustStreamVolume(STREAM, ADJUST_LOWER, FLAG_SHOW_UI & FLAG_ALLOW_RINGER_MODES);
        MODES[2] = manager.getRingerMode();
        manager.adjustStreamVolume(STREAM, ADJUST_RAISE, FLAG_SHOW_UI & FLAG_ALLOW_RINGER_MODES);
        MODES[1] = manager.getRingerMode();
        // There are two possible ways the device may work. It may have a silent/vibrate
        // mode or it may have distinct silent and vibrate modes.
        manager.setStreamVolume(STREAM, startVolume, 0);
        manager.setRingerMode(MODE);
        return MODES;
    }
}