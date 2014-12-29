package me.barrasso.android.volume.media.conditions;

import android.annotation.TargetApi;
import android.media.AudioManager;
import android.os.Build;

import com.android.internal.util.Predicate;

import me.barrasso.android.volume.utils.AudioHelper;

/**
 * Indicates if the device implements a fixed volume policy.
 * Some devices may not have volume control and may operate
 * at a fixed volume, and may not enable muting or changing
 * the volume of audio streams. This method will return true
 * on such devices.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FixedVolume implements Predicate<AudioManager> {

    public FixedVolume() { }

    /**
     * @param manager AudioManager
     * @return True if the two streams are linked.
     */
    @Override
    public boolean apply(AudioManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return AudioHelper._useFixedVolume();
        return manager.isVolumeFixed();
    }

}