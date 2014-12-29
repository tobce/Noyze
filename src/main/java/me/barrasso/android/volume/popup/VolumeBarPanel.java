package me.barrasso.android.volume.popup;

import android.graphics.PixelFormat;
import android.util.Property;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.CmBatteryBar;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.utils.SettingsHelper;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Very simple theme, based on the MIUI Battery Bar concept but for volume_3!
 */
public class VolumeBarPanel extends VolumePanel {

    public static final String TAG = VolumeBarPanel.class.getSimpleName();

    public static final Property<VolumeBarPanel, Integer> BAR_HEIGHT =
            Property.of(VolumeBarPanel.class, Integer.TYPE, "barHeight");

    public static final VolumePanelInfo<VolumeBarPanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<VolumeBarPanel>(VolumeBarPanel.class);

    static {
        VOLUME_PANEL_INFO.properties = new Property[] { BAR_HEIGHT };
    }

    public VolumeBarPanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    protected int barHeight;
    CmBatteryBar volumeBar;

    @Override
    public void onCreate() {
        super.onCreate();
        volumeBar = new CmBatteryBar(getContext());
        volumeBar.setId(android.R.id.progress);
        volumeBar.setColor(color);
        mLayout = volumeBar;
    }

    @Override
    public SettingsHelper loadSettings() {
        SettingsHelper settingsHelper = super.loadSettings();
        setBarHeight(settingsHelper.getIntProperty(VolumeBarPanel.class, BAR_HEIGHT, getBarHeight()));
        return settingsHelper;
    }

    protected void updateHeight() {
        volumeBar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getBarHeight()));
    }

    public void setBarHeight(int newHeight) {
        LOGI(TAG, "setBarHeight(" + newHeight + ")");
        barHeight = newHeight;
        updateHeight();
    }

    public int getBarHeight() {
        if (barHeight <= 0)
            return getResources().getDimensionPixelSize(R.dimen.volume_bar_height);
        return barHeight;
    }

    @Override
    public void setColor(int color) {
        super.setColor(color);
        if (null != volumeBar)
            volumeBar.setColor(color);
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        boolean isMute = isMuted(streamType);
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + "), mute=" + isMute);
        volumeBar.setMax(max);
        volumeBar.setProgress(volume);
        volumeBar.setTag(resources);
        updateHeight();
        show();
    }

    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH  |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL      |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR   |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN   |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(TAG);
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}