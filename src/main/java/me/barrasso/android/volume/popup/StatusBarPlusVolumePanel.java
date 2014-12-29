package me.barrasso.android.volume.popup;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.MaxWidthLinearLayout;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.utils.SettingsHelper;
import me.barrasso.android.volume.utils.Utils;

import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Like {@link me.barrasso.android.volume.popup.StatusBarVolumePanel}, but with the ability
 * to control multiple volume channels (and it's paid).
 */
public class StatusBarPlusVolumePanel extends ParanoidVolumePanel {

	public static final VolumePanelInfo<StatusBarPlusVolumePanel> VOLUME_PANEL_INFO =
										new VolumePanelInfo<StatusBarPlusVolumePanel>(StatusBarPlusVolumePanel.class);

	public StatusBarPlusVolumePanel(PopupWindowManager pWindowManager) {
		super(pWindowManager);
	}

    ViewGroup sliderGroup;
    ViewGroup root;
    ImageView icon;
    ProgressBar seekBar;

	@Override
	public void onCreate() {
        parentOnCreate();
        oneVolume = false;
		super.onCreate();

        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        loadSystemSettings();

        if (null == mStreamControls)
            mStreamControls = new SparseArray<StreamControl>(StreamResources.STREAMS.length);

        root = (ViewGroup) inflater.inflate(R.layout.sbp_volume_adjust, null);
        sliderGroup = (ViewGroup) root.findViewById(R.id.contentPanel);
        mMoreButton = sliderGroup;
        mSliderGroup = (ViewGroup) root.findViewById(R.id.slider_group);
		seekBar = (ProgressBar) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        seekBar.setOnTouchListener(noTouchListener);
        mMoreButton.setOnClickListener(expandListener);
        ((SeekBar) seekBar).setThumb(null);

        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        setStretch(settingsHelper.getProperty(VolumePanel.class, STRETCH, stretch));

        if ((sliderGroup instanceof MaxWidthLinearLayout) && stretch) {
            ((MaxWidthLinearLayout) sliderGroup).setMaxWidth(0);
        }

        // KitKat+ add support for default transitions.
        TransitionCompat transition = TransitionCompat.get();
        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));

		mLayout = root;
	}

    @Override
    public void onRingerModeChange(int ringerMode) {
        super.onRingerModeChange(ringerMode);
        if (null != mLastVolumeChange) {
            switch (mLastVolumeChange.mStreamType) {
                case AudioManager.STREAM_NOTIFICATION:
                case AudioManager.STREAM_RING:
                    switch (ringerMode) {
                        case RINGER_MODE_VIBRATE:
                            icon.setImageResource(getVibrateIcon());
                            break;
                        case RINGER_MODE_SILENT:
                        default:
                            icon.setImageResource(getSilentIcon());
                            break;
                    }
                    break;
            }
        }
    }

    @Override public void expand() {
        mSliderGroup.setVisibility(View.VISIBLE);
        super.expand();
    }

    @Override public void collapse() {
        mSliderGroup.setVisibility(View.GONE);
        super.collapse();
    }

    @Override public boolean isExpanded() {
        return (mSliderGroup.getVisibility() == View.VISIBLE);
    }

    @Override
    protected boolean isParanoid() {
        return true;
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // Update the icon & progress based on the volume change.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        // Figure out what resource and icon to use.
        int iconRes = ((volume <= 0) ? resources.getIconMuteRes() : resources.getIconRes());
        Drawable drawable = getResources().getDrawable(iconRes);
        drawable.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        LayerDrawable layer = (LayerDrawable) seekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        // Actually update the images/ drawables.
        root.setBackgroundColor(backgroundColor);
        icon.setImageDrawable(drawable);
        seekBar.setMax(max);
        seekBar.setProgress(volume);
        seekBar.setTag(resources);
        super.onStreamVolumeChange(streamType, volume, max);
    }
}