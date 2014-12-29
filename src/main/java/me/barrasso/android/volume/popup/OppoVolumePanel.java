package me.barrasso.android.volume.popup;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.utils.Utils;

public class OppoVolumePanel extends ParanoidVolumePanel {

    public static final String TAG = OppoVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<OppoVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<OppoVolumePanel>(OppoVolumePanel.class);

    public OppoVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    /** Dummy divider icon that needs to vanish with the more button */
    protected View mDivider;

    @SuppressWarnings("deprecation")
    @Override public void onCreate() {
        parentOnCreate();
        oneVolume = false;
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Load default PA color if the user doesn't have a preference.
        root = (ViewGroup) inflater.inflate(R.layout.oppo_volume_adjust, null);

        mPanel = (ViewGroup) root.findViewById(R.id.visible_panel);
        mSliderGroup = (ViewGroup) root.findViewById(R.id.slider_group);

        loadSystemSettings();

        if (null == mStreamControls)
            mStreamControls = new SparseArray<StreamControl>(StreamResources.STREAMS.length);

        // Change the icon to use for Bluetooth Music.
        MusicMode.BLUETOOTH.iconResId = R.drawable.oppo_ic_audio_bt;
        MusicMode.BLUETOOTH.iconMuteResId = R.drawable.oppo_ic_audio_bt_mute;

        mLayout = root;
    }

    // Special Oppo feature: ringer icon toggles the ringer mode.
    protected void ringImageClick() {
        LogUtils.LOGI(TAG, "ringImageClick()");
        int newMode = Utils.nextRingerMode(AudioManager.ADJUST_RAISE, mRingerMode, mAudioHelper.hasVibrator());
        if (newMode != mRingerMode) {
            mAudioManager.setRingerMode(newMode);
        }
    }

    @Override
    protected View.OnClickListener getStreamClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Object tag = view.getTag();
                if (tag instanceof StreamControl) {
                    StreamControl sc = (StreamControl) tag;
                    if (sc.streamType == AudioManager.STREAM_NOTIFICATION ||
                        sc.streamType == AudioManager.STREAM_RING) {
                        ringImageClick();
                        return;
                    }
                }

                if (view instanceof ImageView) {
                    launchSoundSettings();
                }
            }
        };
    }

    @Override
    protected void reorderSliders(final int activeStreamType) {
        LogUtils.LOGI(TAG, "reorderSliders(" + activeStreamType + ')');
        super.reorderSliders(activeStreamType);
        StreamControl active = mStreamControls.get(activeStreamType);
        if (null != active && null != active.group) {
            mMoreButton = (ImageView) active.group.findViewById(R.id.expand_button);
            mDivider = active.group.findViewById(R.id.divider);
        }
    }

    @Override
    protected void onUpdateSlider(ViewGroup group) {
        // If we're expanded, always hide the settings & divider.
        LogUtils.LOGI(TAG, "onUpdateSlider()");
        if (isExpanded()) {
            View more = group.findViewById(R.id.expand_button);
            View divider = group.findViewById(R.id.divider);
            more.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
    }

    @Override public boolean isExpanded() {
        return (super.isExpanded() || null != mMoreButton && mMoreButton.getVisibility() != View.VISIBLE);
    }

    @Override public void expand() {
        LogUtils.LOGI(TAG, "expand()");
        super.expand();
        if (null != mMoreButton) mMoreButton.setVisibility(View.GONE);
        if (null != mDivider) mDivider.setVisibility(View.GONE);
    }

    @Override public void collapse() {
        LogUtils.LOGI(TAG, "collapse()");
        super.collapse();
        if (null != mMoreButton) mMoreButton.setVisibility(View.VISIBLE);
        if (null != mDivider) mDivider.setVisibility(View.VISIBLE);
    }

    @Override public void setBackgroundColor(int bcolor) { /* No-op */ }
    @Override public void setColor(int fcolor) { /* No-op */ }
    @Override protected void setProgressColor(SeekBar seekbar, final int tcolor) { /* No-op */ }

    @Override protected int[] getStreamIcons(StreamControl sc) {
        if (sc.streamType == STREAM_BLUETOOTH_SCO)
            return new int[] { R.drawable.oppo_ic_audio_bt, R.drawable.oppo_ic_audio_bt_mute };
        switch (sc.streamType) {
            case AudioManager.STREAM_ALARM:
                return new int[] { R.drawable.oppo_ic_audio_alarm, R.drawable.oppo_ic_audio_alarm_mute };
            case AudioManager.STREAM_RING:
                return new int[] { R.drawable.oppo_ic_audio_ring_notif, R.drawable.oppo_ic_audio_ring_notif_mute };
            case AudioManager.STREAM_NOTIFICATION:
                return new int[] { R.drawable.oppo_ic_audio_notification, R.drawable.oppo_ic_audio_notification_mute };
            case AudioManager.STREAM_MUSIC:
                return new int[] { R.drawable.oppo_ic_audio_media, R.drawable.oppo_ic_audio_media_mute };
            case AudioManager.STREAM_VOICE_CALL:
                return new int[] { R.drawable.oppo_ic_audio_phone, R.drawable.oppo_ic_audio_phone };
            default:
                return new int[] { R.drawable.oppo_ic_audio_vol, R.drawable.oppo_ic_audio_vol_mute };
        }
    }

    @Override protected int getItemLayout() { return R.layout.oppo_volume_adjust_item; }
    @Override protected int getVibrateIcon() { return R.drawable.oppo_ic_audio_ring_notif_vibrate; }
    @Override protected int getSilentIcon() { return R.drawable.oppo_ic_audio_ring_notif_mute; }
    @Override protected int getExpandedIcon() { return R.drawable.oppo_volume_panel_expand_settings; }
    @Override protected int getCollapsedIcon() { return getExpandedIcon(); }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        WindowManager.LayoutParams WPARAMS = super.getWindowLayoutParams();
        WPARAMS.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WPARAMS.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        WPARAMS.y = getResources().getDimensionPixelSize(R.dimen.volume_panel_top);
        return WPARAMS;
    }

}