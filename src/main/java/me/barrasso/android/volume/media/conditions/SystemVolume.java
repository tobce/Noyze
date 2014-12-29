package me.barrasso.android.volume.media.conditions;

import android.media.AudioManager;
import static android.media.AudioManager.*;

import com.android.internal.util.Predicate;

/**
 * Determines if {@link android.media.AudioManager} has a valid system volume.
 */
public class SystemVolume implements Predicate<AudioManager> {

    /**
     * @param manager AudioManager
     * @return True if {@link android.media.AudioManager#STREAM_SYSTEM} exists.
     */
    @Override
    public boolean apply(AudioManager manager) {
        // Remember the volumes that we started at.
        final int systemVolume = manager.getStreamVolume(STREAM_SYSTEM);
        final int[] streams = new int[] {
                STREAM_RING, STREAM_MUSIC, STREAM_ALARM, STREAM_NOTIFICATION
        };

        for (int stream : streams) {
            // Set each stream volume differently, see if system is linked.
            final int prevVolume = manager.getStreamVolume(stream);
            manager.setStreamVolume(STREAM_SYSTEM, 4, 0);
            manager.setStreamVolume(stream, 2, 0);
            final int newSystemVolume = manager.getStreamVolume(STREAM_SYSTEM);
            final int newVolume = manager.getStreamVolume(stream);
            manager.setStreamVolume(stream, prevVolume, 0);
            if (newVolume == newSystemVolume) return false;
        }

        manager.setStreamVolume(STREAM_SYSTEM, systemVolume, 0);
        return true;
    }
}