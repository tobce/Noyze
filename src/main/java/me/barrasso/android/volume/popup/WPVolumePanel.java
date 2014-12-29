package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.BackgroundLinearLayout;
import me.barrasso.android.volume.ui.OnTouchClickListener;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.SettingsHelper;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Windows Phone/ Metro-UI style {@link me.barrasso.android.volume.popup.VolumePanel}
 * designed to display the volume_3 as text with an option to toggle the three ringer
 * modes and media playback controls while music is playing.
 */
public final class WPVolumePanel extends VolumePanel {

    public static final String TAG = WPVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<WPVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<WPVolumePanel>(WPVolumePanel.class);

    public WPVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    private TransitionCompat transition;

    ViewGroup trackInfo;
    ViewGroup musicPanel;
    ViewGroup root;
    TextView ringerText;
    TextView volText, streamText;
    TextView artist, title;
    ImageView icon;
    ImageButton playPause, mBtnPrev, mBtnNext;

    /**
     * @param color The color to lighten.
     * @param factor Lightening factor, 0 - 1.0
     */
    public static int lighten(final int color, final float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = 1.0f - factor * (1.0f - hsv[2]);
        return Color.HSVToColor(hsv);
    }

    public static int stripAlpha(final int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override public void onCreate() {
        super.onCreate();
        Context context = getContext();

        transition = TransitionCompat.get();

        // Get all relevant layouts/ views.
        LayoutInflater inflater = LayoutInflater.from(context);
        root = (ViewGroup) inflater.inflate(R.layout.wp_volume_adjust, null);
        musicPanel = (ViewGroup) root.findViewById(R.id.music_panel);
        trackInfo = (ViewGroup) musicPanel.findViewById(R.id.track_info);
        musicPanel.setVisibility(View.VISIBLE);
        playPause = (ImageButton) musicPanel.findViewById(R.id.media_play_pause);
        mBtnNext = (ImageButton) musicPanel.findViewById(R.id.media_next);
        mBtnPrev = (ImageButton) musicPanel.findViewById(R.id.media_previous);
        volText = (TextView) root.findViewById(R.id.volume_text);
        streamText = (TextView) root.findViewById(R.id.streamName);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        ringerText = (TextView) root.findViewById(R.id.ringer_mode);
        artist = ((TextView) trackInfo.findViewById(R.id.track_artist));
        title  = ((TextView) trackInfo.findViewById(R.id.track_song));

        // Launch the music app when the track information is clicked.
        trackInfo.setOnTouchListener(new OnTouchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                launchMusicApp();
            }
        }));

        // Handle toggling the vibrate/ ringer mode when clicked.
        icon.setOnTouchListener(new OnTouchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRinger();
                onUserInteraction();
            }
        }));

        // Set Segoe UI as the default font for all text.
        Typeface segoe = Typeface.createFromAsset(context.getAssets(), "fonts/Segoe-Regular.ttf");
        Typeface segoeBold = Typeface.createFromAsset(context.getAssets(), "fonts/Segoe-Bold.ttf");
        volText.setTypeface(segoe);
        ringerText.setTypeface(segoe);
        streamText.setTypeface(segoe);
        artist.setTypeface(segoe);
        title.setTypeface(segoeBold);
        volText.setPaintFlags(volText.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        artist.setPaintFlags(artist.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        title.setPaintFlags(title.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        streamText.setPaintFlags(streamText.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        ringerText.setPaintFlags(ringerText.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);

        attachPlaybackListeners(musicPanel, new MediaButtonClickListener());
        onRingerModeChange(mRingerMode);

        mLayout = root;
    }

    /*package*/ void toggleRinger() {
        LogUtils.LOGI(TAG, "toggleRinger()");
        int newMode = Utils.nextRingerMode(AudioManager.ADJUST_RAISE, mRingerMode, mAudioHelper.hasVibrator());
        if (newMode != mRingerMode) {
            mAudioManager.setRingerMode(newMode);
        }
    }

    @Override public void onRingerModeChange(int ringerMode) {
        LOGD(TAG, "onRingerModeChange(" + ringerMode + ")");
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_VIBRATE:
                icon.setImageResource(R.drawable.uvc_ringer_vibrate_dark);
                ringerText.setText(R.string.vibrate);
                break;
            case AudioManager.RINGER_MODE_SILENT:
                icon.setImageResource(R.drawable.uvc_ringer_off_dark);
                ringerText.setText(R.string.silent);
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                icon.setImageResource(R.drawable.uvc_ringer_on_dark);
                if (vibrateWhenRinging())
                    ringerText.setText(R.string.vibrate_ring);
                else
                    ringerText.setText(R.string.ring);
                break;
        }
    }

    @Override
    public int getColor() {
        return stripAlpha(color);
    }

    @Override
    public int getBackgroundColor() {
        return stripAlpha(backgroundColor);
    }

    @Override
    public void setTertiaryColor(final int newColor) {
        super.setTertiaryColor(newColor);
        title.setTextColor(newColor);
        artist.setTextColor(newColor);
        streamText.setTextColor(newColor);
        ringerText.setTextColor(newColor);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onPlaybackEvent(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        // NOTE: This MUST be added to the final descendant of VolumePanel!
        LOGI(TAG, "onPlaybackEvent()");
        this.onPlayStateChanged(mediaInfo);
    }

    @Override public void screen(boolean on) {
        super.screen(on);
        setEnableMarquee(on);
    }

    @Override public void onVisibilityChanged(int visibility) {
        super.onVisibilityChanged(visibility);
        switch (visibility) {
            case View.GONE:
                setEnableMarquee(false);
                break;
            case View.VISIBLE:
                setEnableMarquee(true);
                break;
        }
    }

    private void setEnableMarquee(boolean enabled) {
        LOGD(TAG, "setEnableMarquee(" + enabled + ')');
        if (artist != null) artist.setSelected(enabled);
        if (title != null) title.setSelected(enabled);
    }

    protected boolean hasAlbumArt;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onPlayStateChanged(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        if (!created) return;
        super.onPlayStateChanged(mediaInfo);
        LOGI(TAG, "onPlayStateChanged()");

        // Update button visibility based on the transport flags.
        if (null == mediaInfo.second || mediaInfo.second.getActions() <= 0) {
            mBtnNext.setVisibility(View.VISIBLE);
            mBtnPrev.setVisibility(View.VISIBLE);
            playPause.setVisibility(View.VISIBLE);
        } else {
            final long flags = mediaInfo.second.getActions();
            setVisibilityBasedOnFlag(mBtnPrev, flags, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
            setVisibilityBasedOnFlag(mBtnNext, flags, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
            setVisibilityBasedOnFlag(playPause, flags,
                              PlaybackStateCompat.ACTION_PLAY
                            | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        }

        // Update the text and visibility based on the information we have.
        // Protocol: if either artist or title is visible, make the panel visible.
        String sTitle = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        String sArtist = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        boolean hasArtist = !TextUtils.isEmpty(sArtist);
        boolean hasTitle = !TextUtils.isEmpty(sTitle);

        if (mMusicActive && (hasArtist || hasTitle)) {
            transition.beginDelayedTransition(musicPanel, TransitionCompat.KEY_AUDIO_TRANSITION);
        }

        playPause.setImageResource(((mMusicActive ? R.drawable.pause : R.drawable.play)));
        artist.setVisibility((mMusicActive && hasArtist) ? View.VISIBLE : View.GONE);
        title.setVisibility((mMusicActive && hasTitle) ? View.VISIBLE : View.GONE);
        artist.setText((mMusicActive && hasArtist) ? sArtist : "");
        title.setText((mMusicActive && hasTitle) ? sTitle : "");

        trackInfo.setVisibility(((mMusicActive && (hasArtist || hasTitle)) ? View.VISIBLE : View.GONE));

        // If we have album art, use it! We'll do this last in case we run into strange
        // issues with sizing/ layouts.
        if (root instanceof BackgroundLinearLayout) {
            BackgroundLinearLayout back = (BackgroundLinearLayout) root;
            if (mMusicActive) {
                Bitmap albumArt = mediaInfo.first.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                if (null != albumArt) {
                    final BitmapDrawable d = new BitmapDrawable(getResources(), albumArt);
                    back.setCustomBackgroundColor(getWPBackgroundColor());
                    back.setCustomBackground(d);
                    hasAlbumArt = true;
                } else {
                    back.setCustomBackground(null);
                    hasAlbumArt = false;
                }
            } else {
                back.setCustomBackground(null);
                hasAlbumArt = false;
            }
        }
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // Update the icon & progress based on the volume_3 change.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        trackInfo.setVisibility((mMusicActive ? View.VISIBLE : View.GONE));
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");
        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        if (settingsHelper.hasProperty(VolumePanel.class, VolumePanel.COLOR)) {
            volText.setText(buildVolumeText(getResources(), volume, max, getColor(), lighten(getColor(), 0.75f)));
        } else {
            volText.setText(buildVolumeText(getResources(), volume, max));
        }
        volText.setTag(resources);
        if (null != streamText) streamText.setText(resources.getDescRes());
        if (!mMusicActive || !hasAlbumArt) {
            root.setBackground(new ColorDrawable(getWPBackgroundColor()));
        }
        show();
    }

    protected int getWPBackgroundColor() {
        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        int mBackColor = getResources().getColor(R.color.windows_phone_theme_dark);
        if (settingsHelper.hasProperty(VolumePanel.class, VolumePanel.BACKGROUND)) {
            mBackColor = getBackgroundColor();
        }
        return mBackColor;
    }

    public static SpannableStringBuilder buildVolumeText(Resources res, final int volume, final int max) {
        return buildVolumeText(res, volume, max, res.getColor(R.color.wp_volume_index), res.getColor(R.color.wp_volume_max));
    }

    /** Build WP-style volume_3 text with a big volume_3 and a small maximum. */
    public static SpannableStringBuilder buildVolumeText(Resources res, final int volume,
                                                         final int max, final int volColor, final int maxColor) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(String.valueOf(volume));
        builder.append("/");
        builder.append(String.valueOf(max));
        int volEnd = String.valueOf(volume).length(),
                len = builder.length();
        builder.setSpan(new ForegroundColorSpan(maxColor), 0, volEnd, 0);
        builder.setSpan(new ForegroundColorSpan(volColor), volEnd, len, 0);
        builder.setSpan(new RelativeSizeSpan(1.40f), 0, volEnd, 0);
        builder.setSpan(new RelativeSizeSpan(0.80f), volEnd, len, 0);
        return builder;
    }

    @Override
    public void setStretch(boolean stretchIt) {
        super.setStretch(stretchIt);
        onWindowAttributesChanged();
    }

    @Override
    public void onRotationChanged(int rotation) {
        super.onRotationChanged(rotation);
        onWindowAttributesChanged();
    }

    @Override public boolean supportsMediaPlayback() { return true; }
    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH     |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL         |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR      |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN        |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED        );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.RGB_565);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        WPARAMS.setTitle(TAG);
        Resources res = getResources();
        final int panelWidth = getNotificationPanelWidth();
        final int maxWidth = ((panelWidth > 0) ? panelWidth : res.getDimensionPixelSize(R.dimen.notification_panel_width));
        final int menuWidth = res.getDimensionPixelSize(R.dimen.max_menu_width);
        final int screenWidth = getWindowWidth();
        if (stretch || (maxWidth <= 0) || (!res.getBoolean(R.bool.isTablet) && screenWidth < menuWidth)) {
            WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
        } else {
            WPARAMS.gravity = (Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            WPARAMS.width = (maxWidth <= 0) ? menuWidth : maxWidth;
        }
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}