package me.barrasso.android.volume.popup;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.media.StreamResources;
import miui.v5.widget.CircleProgressView;

import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Simple theme "borrowed" from MIUI V5!
*/
public class CircleVolumePanel extends VolumePanel {

    public static final String TAG = CircleVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<CircleVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<CircleVolumePanel>(CircleVolumePanel.class);

    public CircleVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    ImageView icon, headset;
    CircleProgressView seekBar;
    ViewGroup root;
    TextView streamName;
    View divider;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        root = (ViewGroup) inflater.inflate(R.layout.miui_volume_adjust, null);
        seekBar = (CircleProgressView) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        streamName = (TextView) root.findViewById(R.id.streamName);
        headset = (ImageView) root.findViewById(R.id.v5_volume_headset);
        divider = root.findViewById(R.id.divider);

        // Launch the system sound settings when the icon is clicked.
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
                Intent volumeSettings = new Intent(Settings.ACTION_SOUND_SETTINGS);
                startActivity(volumeSettings);
            }
        });

        // Set our font to MIUI
        Typeface miui = Typeface.createFromAsset(context.getAssets(), "fonts/Miui-Regular.ttf");
        streamName.setTypeface(miui);

        // Change the icon to use for Bluetooth Music.
        MusicMode.BLUETOOTH.iconResId = R.drawable.v5_ic_audio_bt;
        MusicMode.BLUETOOTH.iconMuteResId = R.drawable.v5_ic_audio_bt_mute;

        mLayout = root;
    }

    public static int[] iconForStream(StreamResources res) {
        switch (res) {
            case NotificationStream:
                return new int[] { R.drawable.v5_ic_audio_notification, R.drawable.v5_ic_audio_notification_mute };
            case MediaStream:
                return new int[] { R.drawable.v5_ic_audio_media, R.drawable.v5_ic_audio_media };
            case BluetoothSCOStream:
                return new int[] { R.drawable.v5_ic_audio_bt, R.drawable.v5_ic_audio_bt_mute };
            case RingerStream:
                return new int[] { R.drawable.v5_ic_audio_ring_notif, R.drawable.v5_ic_audio_ring_notif_mute };
            case VoiceStream:
                return new int[] { R.drawable.v5_ic_audio_phone, R.drawable.v5_ic_audio_phone_mute };
            case AlarmStream:
                return new int[] { R.drawable.v5_ic_audio_alarm, R.drawable.v5_ic_audio_alarm_mute };
            default:
                return new int[] { R.drawable.v5_ic_audio_ring_notif, R.drawable.v5_ic_audio_ring_notif_mute };
        }
    }

    @Override
    public void setTertiaryColor(final int newColor) {
        super.setTertiaryColor(newColor);
        streamName.setTextColor(newColor);
    }

    @Override public void setColor(int color) {
        LOGI(TAG, "setColor(" + color + ')');
        super.setColor(color);
        try {
            seekBar.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        } catch (Throwable t) {
            LOGE(TAG, "Error applying color filter to the MIUI VP.", t);
        }
    }

    protected MusicMode musicMode = MusicMode.DEFAULT;

    @Override
    public void setMusicIcon(MusicMode mode) {
        musicMode = mode;
        switch (mode) {
            case BLUETOOTH:
                super.setMusicIcon(mode);
                break;
            case HEADSET:
                setHeadset(R.drawable.v5_ic_audio_headset);
                setHeadsetVisibility(View.VISIBLE);
                break;
            case DEFAULT:
                setHeadset(R.drawable.v5_ic_audio_speaker);
                setHeadsetVisibility(View.VISIBLE);
                break;
        }
    }

    protected void updateIcon(StreamResources sr) {
        int[] icons = iconForStream(sr);
        // Special case for when the device is in vibrate mode.
        if ((sr == StreamResources.RingerStream ||
             sr == StreamResources.NotificationStream) &&
             mRingerMode == AudioManager.RINGER_MODE_VIBRATE)
            icons = new int[] { R.drawable.v5_ic_audio_ring_notif, R.drawable.v5_ic_audio_ring_notif_vibrate };
        int iconRes = ((sr.getVolume() <= 0) ? icons[1] : icons[0]);
        icon.setImageResource(iconRes);
    }

    @Override
    public void onVisibilityChanged(int visibility) {
        super.onVisibilityChanged(visibility);
        // If an animation is running, cancel it.
        switch (visibility) {
            case View.GONE:
                cancelAnimation();
                hasPulsed = false;
            break;
        }
    }

    protected void cancelAnimation() {
        if (null != animator)
            animator.cancel();
        animator = null;
    }

    protected Animator animator;
    protected boolean hasPulsed = false;

    protected Animator shuffle(View target) {
        LOGI(TAG, "shuffle()");
        final int duration = Resources.getSystem().getInteger(android.R.integer.config_mediumAnimTime);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 5, 0, -5, 0, 5, 0, -5, 0, 5, 0, -5, 0, 5, 0);
        rotation.setDuration(duration);
        rotation.setStartDelay(duration / 4);
        target.setHasTransientState(true);
        return rotation;
    }

    protected Animator wiggle(View target) {
        LOGI(TAG, "wiggle()");
        final int duration = Resources.getSystem().getInteger(android.R.integer.config_mediumAnimTime);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(target, View.ROTATION, 5, 0, -5, 0, 5, 0, -5, 0, 5, 0, -5, 0, 5, 0);
        rotation.setDuration(duration);
        rotation.setStartDelay(duration / 4);
        target.setHasTransientState(true);
        return rotation;
    }

    protected Animator pulse() {
        LOGI(TAG, "pulse()");
        final float scaleFactor = 1.075f;
        final int duration = Resources.getSystem().getInteger(android.R.integer.config_shortAnimTime);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, scaleFactor, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, scaleFactor, 1.0f);
        final AnimatorSet scaleAnim = new AnimatorSet();
        scaleAnim.play(scaleX).with(scaleY);
        scaleAnim.setDuration(duration);
        scaleAnim.setStartDelay(duration / 4);
        scaleAnim.setInterpolator(new AccelerateInterpolator());
        root.setHasTransientState(true);
        hasPulsed = true;
        return scaleAnim;
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // Cancel any animations already running.
        if (isShowing()) cancelAnimation();

        // Update the icon & progress based on the volume_3 change.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");
        // Hide the headset when it's not its turn!
        if (mMusicActive || streamType == AudioManager.STREAM_MUSIC) {
            setHeadsetVisibility(View.VISIBLE);
            setHeadset((musicMode == MusicMode.HEADSET) ?
                    R.drawable.v5_ic_audio_headset : R.drawable.v5_ic_audio_speaker);
        } else if (streamType == STREAM_BLUETOOTH_SCO) {
            setHeadsetVisibility(View.VISIBLE);
            setHeadset(R.drawable.v5_ic_audio_headset);
        } else {
            setHeadsetVisibility(View.GONE);
        }
        try {
            seekBar.setMax(max);
        } catch (Throwable t) {
            LOGE(TAG, "Error CircleProgressView#setMax(int)", t);
        }
        seekBar.setProgress(volume);
        seekBar.setTag(resources);
        int descRes = resources.getDescRes();
        if (resources.getVolume() <= 0 &&
            (resources == StreamResources.RingerStream ||
             resources == StreamResources.NotificationStream)) {
            if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                descRes = R.string.vibrate_c;
                AnimatorSet set = new AnimatorSet();
                set.play(wiggle(icon)).with(shuffle(root));
                set.start();
                animator = set;
            } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                descRes = R.string.silent_c;
            }
        }
        streamName.setText(descRes);
        updateIcon(resources);

        // If we've reached the max volume_3, and we weren't there already, pulse!
        if (!hasPulsed && max == volume) {
            animator = pulse();
            animator.start();
        }
        show();
    }

    public void setHeadset(int iconRes) {
        headset.setImageResource(iconRes);
    }

    public void setHeadsetVisibility(int visibility) {
        headset.setVisibility(visibility);
        divider.setVisibility(visibility);
    }

    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH     |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL         |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR      |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(getName());
        WPARAMS.gravity = Gravity.CENTER;
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}