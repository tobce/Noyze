package me.barrasso.android.volume.popup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.Expandable;
import me.barrasso.android.volume.ui.MaxWidthLinearLayout;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Constants;

import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Theme based off Paranoid Android's (4.4) volume panel, with the current
 * slider at the top and an option to expand and control other streams.
 */
public class ParanoidVolumePanel extends VolumePanel implements Expandable {

    public static final String TAG = ParanoidVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<ParanoidVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<ParanoidVolumePanel>(ParanoidVolumePanel.class);

    public ParanoidVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    /** The visible portion of the volume overlay */
    protected ViewGroup mPanel;
    /** Contains the sliders and their touchable icons */
    protected ViewGroup mSliderGroup;
    /** The button that expands the dialog to show all sliders */
    protected View mMoreButton;

    protected ViewGroup root;

    /** Currently active stream that shows up at the top of the list of sliders */
    protected int mActiveStreamType = -1;

    protected boolean mVolumeLinkNotification = false;

    protected void parentOnCreate() { super.onCreate(); }

    @SuppressWarnings("deprecation")
    @Override public void onCreate() {
        super.onCreate();
        oneVolume = false;
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Load default PA color if the user doesn't have a preference.
        if (!has(COLOR)) color = context.getResources().getColor(R.color.pa_volume_scrubber);
        if (!has(BACKGROUND)) backgroundColor = context.getResources().getColor(R.color.volume_panel_bg);
        root = (ViewGroup) inflater.inflate(R.layout.pa_volume_adjust, null);

        mPanel = (ViewGroup) root.findViewById(R.id.visible_panel);

        mSliderGroup = (ViewGroup) root.findViewById(R.id.slider_group);
        mMoreButton = root.findViewById(R.id.expand_button);
        mPanel.setBackgroundColor(backgroundColor);

        loadSystemSettings();
        mMoreButton.setOnClickListener(expandListener);

        if (null == mStreamControls)
            mStreamControls = new SparseArray<StreamControl>(StreamResources.STREAMS.length);

        mLayout = root;
    }

    protected void updateSize() {
        LOGI(TAG, "updateSize(stretch=" + stretch + ')');
        if (mPanel instanceof MaxWidthLinearLayout) {
            int panelWidth = 0;
            if (!stretch) {
                panelWidth = getNotificationPanelWidth();
                if (panelWidth <= 0)
                    panelWidth = getResources().getDimensionPixelSize(R.dimen.volume_panel_screen_width);
            }

            ((MaxWidthLinearLayout) mPanel).setMaxWidth(panelWidth);
        }
        onWindowAttributesChanged();
    }

    protected void loadSystemSettings() {
        // Not supported on all devices, but worth a check.
        ContentResolver cr = getContext().getContentResolver();
        final int notifLink = Settings.System.getInt(cr, Constants.VOLUME_LINK_NOTIFICATION, Integer.MIN_VALUE);
        final int notifUseRinger = Settings.System.getInt(cr, Constants.NOTIFICATIONS_USE_RING_VOLUME, Integer.MIN_VALUE);
        if (notifLink != Integer.MIN_VALUE) {
            mVolumeLinkNotification = (notifLink == 1);
        } else if (notifUseRinger != Integer.MIN_VALUE) {
            mVolumeLinkNotification = (notifUseRinger == 1);
        } else {
            mVolumeLinkNotification = mNotificationRingerLink;
        }

        // For now, only show master volume if master volume is supported.
        if (null == mAudioHelper) mAudioHelper = AudioHelper.getHelper(getContext(), null);
        boolean useMasterVolume = mAudioHelper.useMasterVolume();
        if (useMasterVolume) {
            for (int i = 0; i < StreamResources.STREAMS.length; i++) {
                StreamResources streamRes = StreamResources.STREAMS[i];
                streamRes.show(streamRes.getStreamType() == STREAM_MASTER);
            }
        }
    }

    protected View.OnClickListener expandListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            LOGI(TAG, "onClick(" + view.getId() + ") expand = " + R.id.expand_button);
            switch (view.getId()) {
                case R.id.expand_button:
                    toggle();
                    return;
            }
            // Another way is to check if the two are equal.
            if (view.equals(mMoreButton)) toggle();
        }
    };

    protected void toggle() {
        if (isExpanded())
            collapse();
        else
            expand();
        onUserInteraction();
    }

    @Override public void setBackgroundColor(int bcolor) {
        super.setBackgroundColor(bcolor);
        mPanel.setBackgroundColor(backgroundColor);
    }

    @Override public void setColor(int fcolor) {
        super.setColor(fcolor);
        updateStates();
    }

    @Override public void setTertiaryColor(int fcolor) {
        super.setTertiaryColor(fcolor);
        updateStates();
    }

    @Override public void onVisibilityChanged(int visibility) {
        super.onVisibilityChanged(visibility);
        switch (visibility) {
            case View.GONE:
                mActiveStreamType = -1;
                break;
        }
    }

    @Override public void setOneVolume(boolean one) { /* No-op */ }
    protected int getItemLayout() { return R.layout.pa_volume_adjust_item; }

    protected void createSliders() {
        LOGI(TAG, "createSliders()");
        StreamResources[] STREAMS = StreamResources.STREAMS;
        Context mContext = getContext();
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Resources res = mContext.getResources();
        for (int i = 0; i < STREAMS.length; i++) {
            StreamResources streamRes = STREAMS[i];
            int streamType = streamRes.getStreamType();
            StreamControl sc = new StreamControl();
            sc.streamType = streamType;
            sc.group = (ViewGroup) inflater.inflate(getItemLayout(), null);
            sc.group.setTag(sc);
            sc.icon = (ImageView) sc.group.findViewById(R.id.stream_icon);
            sc.icon.setTag(sc);
            sc.icon.setContentDescription(res.getString(streamRes.getDescRes()));
            sc.iconRes = streamRes.getIconRes();
            sc.iconMuteRes = streamRes.getIconMuteRes();
            int[] icons = _getStreamIcons(sc);
            sc.icon.setImageResource(icons[0]);
            sc.icon.setOnClickListener(getStreamClickListener());
            sc.seekbarView = (SeekBar) sc.group.findViewById(android.R.id.progress);
            int plusOne = (streamType == STREAM_BLUETOOTH_SCO ||
                    streamType == AudioManager.STREAM_VOICE_CALL) ? 1 : 0;
            setProgressColor(sc.seekbarView, color);
            sc.seekbarView.setMax(getStreamMaxVolume(streamType) + plusOne);
            sc.seekbarView.setOnSeekBarChangeListener(volumeSeekListener);
            sc.seekbarView.setTag(sc);
            onCreateStream(sc);
            mStreamControls.put(streamType, sc);
        }
    }

    protected void onCreateStream(StreamControl sc) { }

    protected View.OnClickListener getStreamClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view instanceof ImageView) {
                    launchSoundSettings();
                }
            }
        };
    }

    protected void launchSoundSettings() {
        hide();
        Intent volumeSettings = new Intent(Settings.ACTION_SOUND_SETTINGS);
        startActivity(volumeSettings);
    }

    protected void reorderSliders(final int activeStreamType) {
        LOGI(TAG, "reorderSliders()");
        mSliderGroup.removeAllViews();

        StreamControl active = mStreamControls.get(activeStreamType);
        if (active == null) {
            mActiveStreamType = -1;
        } else {
            mSliderGroup.addView(active.group);
            mActiveStreamType = activeStreamType;
            active.group.setVisibility(View.VISIBLE);
            updateSlider(active);
        }

        addOtherVolumes();
    }

    private void addOtherVolumes() {
        LOGI(TAG, "addOtherVolumes()");
        boolean mVoiceCapable = mAudioHelper.isVoiceCapable();
        for (StreamResources stream : StreamResources.STREAMS) {
            // Skip the phone specific ones and the active one
            final int streamType = stream.getStreamType();
            StreamControl sc = mStreamControls.get(streamType);
            if (!stream.show() || streamType == mActiveStreamType) {
                continue;
            }
            // Skip ring volume for non-phone devices
            if (!mVoiceCapable && streamType == AudioManager.STREAM_RING) {
                continue;
            }
            // Skip notification volume if linked with ring volume
            if (streamType == AudioManager.STREAM_NOTIFICATION) {
                if (mVoiceCapable && mNotificationRingerLink) {
                    continue;
                } else if (linkNotifRinger) {
                    // User has asked to link notification & ringer volume.
                    continue;
                }
            }

            mSliderGroup.addView(sc.group);
            updateSlider(sc);
        }
    }

    public void updateStates() {
        LOGI(TAG, "updateStates()");
        final int count = mSliderGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            StreamControl sc = (StreamControl) mSliderGroup.getChildAt(i).getTag();
            updateSlider(sc);
        }
    }

    protected int[] _getStreamIcons(StreamControl sc) {
        int[] icons = getStreamIcons(sc);
        if (sc.streamType == AudioManager.STREAM_NOTIFICATION ||
                sc.streamType == AudioManager.STREAM_RING) {
            switch (mRingerMode) {
                case AudioManager.RINGER_MODE_VIBRATE:
                    icons[1] = getVibrateIcon();
                    break;
                case AudioManager.RINGER_MODE_SILENT:
                    icons[1] = getSilentIcon();
                    break;
            }
        }
        return icons;
    }

    protected int[] getStreamIcons(StreamControl sc) {
        if (sc.streamType == AudioManager.STREAM_NOTIFICATION ||
            sc.streamType == AudioManager.STREAM_RING) {
            switch (mRingerMode) {
                case AudioManager.RINGER_MODE_VIBRATE:
                    return new int[] { sc.iconRes, getVibrateIcon() };
                case AudioManager.RINGER_MODE_SILENT:
                    return new int[] { sc.iconRes, getSilentIcon() };
            }
        }
        return new int[] { sc.iconRes, sc.iconMuteRes }; // Default icons
    }

    /** Update the mute and progress state of a slider */
    private void updateSlider(StreamControl sc) {
        final int volume = getStreamVolume(sc.streamType);
        LOGI(TAG, "updateSlider(" + sc.streamType + ", " + volume + ")");
        sc.seekbarView.setProgress(volume);
        final boolean muted = (isMuted(sc.streamType) || volume <= 0);
        // Force reloading the image resource
        sc.icon.setImageDrawable(null);
        int[] icons = _getStreamIcons(sc);
        int icon = muted ? icons[1] : icons[0];
        if (isParanoid()) {
            Drawable drawable = getResources().getDrawable(icon);
            drawable.mutate().setColorFilter(tertiaryColor, PorterDuff.Mode.MULTIPLY);
            sc.icon.setImageDrawable(drawable);
        } else {
            sc.icon.setImageResource(icon);
        }
        setProgressColor(sc.seekbarView, color);
        onUpdateSlider(sc.group);
    }

    protected void onUpdateSlider(ViewGroup sliderGroup) { }

    protected void setProgressColor(SeekBar seekbar, final int tcolor) {
        Drawable thumb = null;
        LOGI(TAG, "setProgressColor(" + color + ")");
        LayerDrawable layer = (LayerDrawable) seekbar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        thumb = getResources().getDrawable(R.drawable.scrubber_control_selector_white);
        thumb.mutate().setColorFilter(tcolor, PorterDuff.Mode.MULTIPLY);
        thumb.setBounds(0, 0, thumb.getIntrinsicWidth(), thumb.getIntrinsicHeight());
        seekbar.setThumb(thumb);
        // NOTE: The call to Utils.tap was removed because it causes an update
        // to the volume progress and creates strange behaviour.
        seekbar.invalidate();
    }

    @Override public boolean isExpanded() {
        View child = mSliderGroup.getChildAt(1);
        return (null != child && child.getVisibility() == View.VISIBLE);
    }

    protected int getExpandedIcon() { return R.drawable.ic_find_previous_holo_dark; }
    protected int getCollapsedIcon() { return R.drawable.ic_find_next_holo_dark; }

    @Override public void expand() {
        LOGI(TAG, "expand()");
        final int count = mSliderGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mSliderGroup.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                child.setVisibility(View.VISIBLE);
                onUpdateSlider((ViewGroup) child);
            }
        }
        if (mMoreButton instanceof ImageView)
            ((ImageView) mMoreButton).setImageResource(getExpandedIcon());
    }

    private void hideSlider(final int mActiveStreamType) {
        LOGI(TAG, "hideSlider(" + mActiveStreamType + ')');
        final int count = mSliderGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            StreamControl sc = (StreamControl) mSliderGroup.getChildAt(i).getTag();
            if (mActiveStreamType == sc.streamType) {
                mSliderGroup.getChildAt(i).setVisibility(View.GONE);
            }
        }
    }

    @Override public void collapse() {
        LOGI(TAG, "collapse()");
        final int count = mSliderGroup.getChildCount();
        for (int i = 1; i < count; i++) {
            mSliderGroup.getChildAt(i).setVisibility(View.GONE);
        }
        if (mMoreButton instanceof ImageView)
            ((ImageView) mMoreButton).setImageResource(getCollapsedIcon());
    }

    /*package*/ SeekBar.OnSeekBarChangeListener volumeSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            final Object tag = seekBar.getTag();
            if (fromUser && tag instanceof StreamControl) {
                StreamControl sc = (StreamControl) tag;
                if (getStreamVolume(sc.streamType) != progress) {
                    LOGI(TAG, "setStreamVolume(" + sc.streamType + ", " + progress + ')');
                    setStreamVolume(sc.streamType, progress);
                }
            }
            onUserInteraction();
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override public void onStopTrackingTouch(SeekBar seekBar) {
            final Object tag = seekBar.getTag();
            if (tag instanceof StreamControl) {
                StreamControl sc = (StreamControl) tag;
                // because remote volume updates are asynchronous, AudioService might have received
                // a new remote volume value since the finger adjusted the slider. So when the
                // progress of the slider isn't being tracked anymore, adjust the slider to the last
                // "published" remote volume value, so the UI reflects the actual volume.
                if (sc.streamType == STREAM_REMOTE_MUSIC) {
                    seekBar.setProgress(getStreamVolume(STREAM_REMOTE_MUSIC));
                }
            }
        }
    };

    @Override
    protected void onVolumeChanged(int streamType, int index, int prevIndex, boolean ringer) {

        // Under NO circumstances can this theme handle master volume! It wouldn't
        // make sense and all sliders would compete with one another.
        oneVolume = false;

        // Before displaying, let's create and reorder the sliders.
        synchronized (this) {
            if (null != mMoreButton) mMoreButton.setOnClickListener(expandListener);
            if (mStreamControls == null || mStreamControls.size() <= 0) {
                createSliders();
            }

            if (streamType != mActiveStreamType) {
                hideSlider(mActiveStreamType);
                reorderSliders(streamType);
            }
        }

        super.onVolumeChanged(streamType, index, prevIndex, ringer);
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");

        StreamControl sc = mStreamControls.get(streamType);
        if (sc != null) {
            if (sc.seekbarView.getMax() != max) {
                sc.seekbarView.setMax(max);
            }

            sc.seekbarView.setProgress(volume);
        }

        if (alwaysExpanded) expand();
        show();
    }

    @Override public void hide() {
        super.hide();
        if (isExpanded() && !alwaysExpanded) collapse();
    }

    protected int getVibrateIcon() { return R.drawable.ic_audio_ring_notif_vibrate; }
    protected int getSilentIcon() { return R.drawable.ic_audio_phone_mute; }

    @Override public void onRingerModeChange(int ringerMode) {

        // Pulled from VolumePanel to also have the small vibration/ feedback.
        switch (ringerMode) {
            case RINGER_MODE_VIBRATE:
                if (isEnabled()) mAudioHelper.vibrate(VIBRATE_DURATION);
                break;
        }

        LOGI(TAG, "onRingerModeChange(" + ringerMode + ')');
        if (null != mLastVolumeChange &&
                (mLastVolumeChange.mStreamType == AudioManager.STREAM_NOTIFICATION ||
                 mLastVolumeChange.mStreamType == AudioManager.STREAM_RING)) {
            StreamControl sc = mStreamControls.get(mLastVolumeChange.mStreamType);
            if (null != sc) {
                switch (ringerMode) {
                    case RINGER_MODE_VIBRATE:
                        sc.iconMuteRes = getVibrateIcon();
                        break;
                    case RINGER_MODE_SILENT:
                    default:
                        sc.iconMuteRes = getSilentIcon();
                        break;
                }
            }
        }

        if (isShowing()) updateStates();
    }

    @Override
    public void setStretch(boolean stretchIt) {
        LOGI(TAG, "setStretch(" + stretchIt + ')');
        super.setStretch(stretchIt);
        if (isParanoid()) updateSize();
    }

    @Override
    public void onRotationChanged(int rotation) {
        super.onRotationChanged(rotation);
        if (isParanoid()) onWindowAttributesChanged();
    }

    @Override public boolean isInteractive() { return true; }

    protected boolean isParanoid() {
        return getClass().equals(ParanoidVolumePanel.class);
    }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH  |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL      |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR   |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        int anim = getInternalStyle("Animation_DropDownDown");
        if (anim > 0) WPARAMS.windowAnimations = anim;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(getName());
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}