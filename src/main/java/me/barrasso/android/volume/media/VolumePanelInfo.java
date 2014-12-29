package me.barrasso.android.volume.media;

import android.text.TextUtils;
import android.util.Property;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import me.barrasso.android.volume.popup.BlackberryVolumePanel;
import me.barrasso.android.volume.popup.CircleVolumePanel;
import me.barrasso.android.volume.popup.HeadsUpVolumePanel;
import me.barrasso.android.volume.popup.InvisibleVolumePanel;
import me.barrasso.android.volume.popup.OppoVolumePanel;
import me.barrasso.android.volume.popup.ParanoidVolumePanel;
import me.barrasso.android.volume.popup.PopupWindowManager;
import me.barrasso.android.volume.popup.StatusBarPlusVolumePanel;
import me.barrasso.android.volume.popup.StatusBarVolumePanel;
import me.barrasso.android.volume.popup.UberVolumePanel;
import me.barrasso.android.volume.popup.VolumeBarPanel;
import me.barrasso.android.volume.popup.VolumePanel;
import me.barrasso.android.volume.popup.WPVolumePanel;
import me.barrasso.android.volume.popup.iOSVolumePanel;

import static me.barrasso.android.volume.LogUtils.LOGE;

/** Information about a {@link me.barrasso.android.volume.popup.VolumePanel} subclass. */
public final class VolumePanelInfo<T extends VolumePanel> {

    public VolumePanelInfo(Class<T> clazzz) {
        clazz = clazzz;
        prefName = clazzz.getSimpleName();
    }

    // TODO: update these values when themes support new features, new themes are added, etc.
    public static final String FEATURE_FOREGROUND_COLOR = "VolumePanel_color";
    public static final String FEATURE_BACKGROUND_COLOR = "VolumePanel_backgroundColor";
    public static final String FEATURE_SEEK = "VolumePanel_seek";
    public static final String FEATURE_BAR_HEIGHT = "VolumeBarPanel_barHeight";
    public static final String FEATURE_STRETCH = "VolumePanel_stretch";
    public static final String FEATURE_TERTIARY_COLOR = "VolumePanel_tertiaryColor";
    public static final String FEATURE_ALWAYS_EXPANDED = "VolumePanel_alwaysExpanded";

    /**
     * @return A list of all supported features for themes.
     */
    public static String[] getAllFeatures() {
        return new String[] {
                FEATURE_FOREGROUND_COLOR, FEATURE_BACKGROUND_COLOR, FEATURE_SEEK,
                FEATURE_BAR_HEIGHT, FEATURE_STRETCH, FEATURE_TERTIARY_COLOR, FEATURE_ALWAYS_EXPANDED
        };
    }

    /**
     * @param name Can be null, the name of the specified theme.
     * @return A List of supported features for a given volume panel.
     */
    public static List<String> getSupportedFeatures(String name) {
        List<String> features = new ArrayList<String>();
        if (TextUtils.isEmpty(name)) name = StatusBarVolumePanel.class.getSimpleName();
        if (BlackberryVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
        } else if (CircleVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
        } else if (HeadsUpVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_SEEK);
            features.add(FEATURE_STRETCH);
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
        } else if (InvisibleVolumePanel.class.getSimpleName().equals(name)) {
            // nothing
        } else if (iOSVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BACKGROUND_COLOR);
        } else if (OppoVolumePanel.class.getSimpleName().equals(name)) {
            // nothing
            features.add(FEATURE_ALWAYS_EXPANDED);
        } else if (ParanoidVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
            features.add(FEATURE_STRETCH);
            features.add(FEATURE_ALWAYS_EXPANDED);
        } else if (StatusBarPlusVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_STRETCH);
        } else if (StatusBarVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_STRETCH);
            features.add(FEATURE_SEEK);
        } else if (VolumeBarPanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BAR_HEIGHT);
        } else if (WPVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
            features.add(FEATURE_STRETCH);
        } else if (UberVolumePanel.class.getSimpleName().equals(name)) {
            features.add(FEATURE_FOREGROUND_COLOR);
            features.add(FEATURE_BACKGROUND_COLOR);
            features.add(FEATURE_TERTIARY_COLOR);
            features.add(FEATURE_SEEK);
            features.add(FEATURE_STRETCH);
        }
        return features;
    }

    public VolumePanel getInstance(PopupWindowManager pwm) {
        try {
            Constructor<T> constructor = clazz.getConstructor(PopupWindowManager.class);
            return constructor.newInstance(pwm);
        } catch (Throwable t) {
            LOGE("VolumePanelInfo", "Failed to construct " + clazz.getSimpleName(), t);
        }
        return null;
    }

    public final Class<T> clazz;
    public final String prefName;

    // These properties represent unique settings for each VolumePanel,
    // or null if no such settings exist.
    public Property<T, ?>[] properties;
}