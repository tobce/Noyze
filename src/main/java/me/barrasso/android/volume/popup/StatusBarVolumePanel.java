package me.barrasso.android.volume.popup;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.graphics.PixelFormat;
import android.widget.SeekBar;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.MaxWidthLinearLayout;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.SettingsHelper;
import me.barrasso.android.volume.utils.Utils;

/**
 * Simplest {@link VolumePanel} that covers the system status bar with a
 * black strip that includes the active volume_3 stream icon and a {@link android.widget.ProgressBar},
 * but does not accept user interaction.
 */
public class StatusBarVolumePanel extends VolumePanel {
	
	public static final String TAG = StatusBarVolumePanel.class.getSimpleName();
	
	public static final VolumePanelInfo<StatusBarVolumePanel> VOLUME_PANEL_INFO =
										new VolumePanelInfo<StatusBarVolumePanel>(StatusBarVolumePanel.class);

	public StatusBarVolumePanel(PopupWindowManager pWindowManager) {
		super(pWindowManager);
	}

    ViewGroup sliderGroup;
    ViewGroup root;
    ImageView icon;
    ProgressBar seekBar;

	@Override
	public void onCreate() {
		super.onCreate();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        root = (ViewGroup) inflater.inflate(R.layout.sb_volume_adjust, null);
        sliderGroup = (ViewGroup) root.findViewById(R.id.slider_group);
		seekBar = (ProgressBar) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        toggleSeekBar(seek);

        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        setStretch(settingsHelper.getProperty(VolumePanel.class, STRETCH, stretch));

        if ((sliderGroup instanceof MaxWidthLinearLayout) && stretch) {
            ((MaxWidthLinearLayout) sliderGroup).setMaxWidth(0);
        }

        // When the stream icon is clicked, launch sound settings.
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
                Intent volumeSettings = new Intent(Settings.ACTION_SOUND_SETTINGS);
                startActivity(volumeSettings);
            }
        });

        // KitKat+ add support for default transitions.
        TransitionCompat transition = TransitionCompat.get();
        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));

		mLayout = root;
	}

    @Override public void setColor(final int newColor) {
        super.setColor(newColor);
        toggleSeekBar(seek);
    }

    @Override public void setSeek(final boolean shouldSeek) {
        super.setSeek(shouldSeek);
        toggleSeekBar(shouldSeek);
    }

    protected void toggleSeekBar(final boolean shouldSeek) {
        // If we've got a SeekBar, handle seeking!
        if (seekBar instanceof SeekBar) {
            SeekBar seeker = (SeekBar) seekBar;
            seeker.setOnSeekBarChangeListener((shouldSeek) ? this : null);
            seeker.setOnTouchListener((shouldSeek) ? null : noTouchListener);
            Drawable thumb = null;
            if (shouldSeek) {
                thumb = getResources().getDrawable(R.drawable.scrubber_control_selector_mini);
                thumb.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                thumb.setBounds(0, 0, thumb.getIntrinsicWidth(), thumb.getIntrinsicHeight());
            }
            seeker.setThumb(thumb);
            // NOTE: there's so weird issue with setting the thumb dynamically.
            // This seems to do the trick (fingers crossed).
            Utils.tap((View) seeker.getParent());
            seeker.invalidate();
        }
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // Update the icon & progress based on the volume_3 change.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");
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
        show();
	}

    protected void updateSize() {
        LOGI(TAG, "updateSize(stretch=" + stretch + ')');
        if (sliderGroup instanceof MaxWidthLinearLayout) {
            int panelWidth = 0;
            if (!stretch) {
                panelWidth = getNotificationPanelWidth();
                if (panelWidth <= 0)
                    panelWidth = getResources().getDimensionPixelSize(R.dimen.notification_panel_width);
            }

            ((MaxWidthLinearLayout) sliderGroup).setMaxWidth(panelWidth);
        }
        onWindowAttributesChanged();
    }

    @Override
    public void setStretch(boolean stretchIt) {
        LOGI(TAG, "setStretch(" + stretchIt + ')');
        super.setStretch(stretchIt);
        updateSize();
    }

    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
		int flags = (LayoutParams.FLAG_NOT_FOCUSABLE		|
                     LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH  |
                     LayoutParams.FLAG_NOT_TOUCH_MODAL      |
                     LayoutParams.FLAG_LAYOUT_INSET_DECOR   |
				     LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
				     LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
		LayoutParams WPARAMS = new WindowManager.LayoutParams(
			LayoutParams.MATCH_PARENT, mStatusBarHeight, 0, 0,
			LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
		WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
		WPARAMS.packageName = getContext().getPackageName();
		WPARAMS.setTitle(TAG);
        WPARAMS.rotationAnimation = LayoutParams.ROTATION_ANIMATION_JUMPCUT;
		WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
		WPARAMS.screenBrightness = WPARAMS.buttonBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		return WPARAMS;
	}
}