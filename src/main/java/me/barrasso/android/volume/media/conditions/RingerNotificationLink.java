package me.barrasso.android.volume.media.conditions;

import android.media.AudioManager;
import static android.media.AudioManager.*;

import com.android.internal.util.Predicate;

import me.barrasso.android.volume.LogUtils;

/**
 * Test if the Ringer & Notification volumes are linked.
 */
public class RingerNotificationLink implements Predicate<AudioManager> {
    public RingerNotificationLink() { }

    /**
     * @param manager AudioManager
     * @return True if the two streams are linked.
     */
    @Override
    public boolean apply(AudioManager manager) {
        // Remember the volumes that we started at.
        final int notificationVolume = manager.getStreamVolume(STREAM_NOTIFICATION);
        final int ringerVolume = manager.getStreamVolume(STREAM_RING);
        // Set both to 1 (don't affect ringer).
        manager.setStreamVolume(STREAM_NOTIFICATION, 1, 0);
        manager.setStreamVolume(STREAM_RING, 1, 0);
        // Set the volumes differently to check what they are.
        manager.setStreamVolume(STREAM_NOTIFICATION, 2, 0);
        manager.setStreamVolume(STREAM_RING, 3, 0);
        // Check what the updated volumes are.
        final int notificationVolumeFinal = manager.getStreamVolume(STREAM_NOTIFICATION);
        final int ringerVolumeFinal = manager.getStreamVolume(STREAM_RING);
        // Set the volumes back to what they started as.
        manager.setStreamVolume(STREAM_NOTIFICATION, notificationVolume, 0);
        manager.setStreamVolume(STREAM_RING, ringerVolume, 0);
        boolean ret = (notificationVolumeFinal == ringerVolumeFinal);
        LogUtils.LOGI("RingerNotificationLink", String.valueOf(ret));
        return ret;
    }
}