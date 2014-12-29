package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.VolumeAccessibilityService;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.VolumeManager;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Blackberry style music/ volume_3 panel.
 */
public final class BlackberryVolumePanel extends VolumePanel {

    public static final String TAG = BlackberryVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<BlackberryVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<BlackberryVolumePanel>(BlackberryVolumePanel.class);

    public static int[] iconForStream(StreamResources res) {
        switch (res) {
            case NotificationStream:
                return new int[] { R.drawable.ic_bb_notification, R.drawable.ic_bb_notification_mute };
            case RingerStream:
            case VoiceStream:
                return new int[] { R.drawable.ic_bb_phone, R.drawable.ic_bb_phone_mute };
            default:
                return new int[] { R.drawable.ic_bb_speaker, R.drawable.ic_bb_speaker_mute };
        }
    }

    public BlackberryVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    private TransitionCompat transition;

    ImageView albumArt;
    ViewGroup albumArtContainer, mediaContainer;
    TextView artist;
    TextView song;
    ImageView icon;
    TextView streamName;
    ProgressBar seekBar;
    ViewGroup root, musicPanel;
    ImageButton playPause, mBtnNext, mBtnPrev;
    View divider;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();

        transition = TransitionCompat.get();

        LayoutInflater inflater = LayoutInflater.from(context);
        root = (ViewGroup) inflater.inflate(R.layout.bb_volume_adjust, null);
        seekBar = (ProgressBar) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        divider = root.findViewById(R.id.divider);
        streamName = (TextView) root.findViewById(R.id.streamName);
        mediaContainer = (ViewGroup) root.findViewById(R.id.media_container);
        albumArtContainer = (ViewGroup) root.findViewById(R.id.album_art_container);
        albumArt = (ImageView) root.findViewById(R.id.album_art);
        albumArt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMusic();
            }
        });
        artist = (TextView) root.findViewById(R.id.track_artist);
        song = (TextView) root.findViewById(R.id.track_song);
        musicPanel = (ViewGroup) root.findViewById(R.id.music_panel);
        attachPlaybackListeners(root, new MediaButtonClickListener());
        playPause = (ImageButton) root.findViewById(R.id.media_play_pause);
        mBtnNext = (ImageButton) root.findViewById(R.id.media_next);
        mBtnPrev = (ImageButton) root.findViewById(R.id.media_previous);
        setEnableMarquee(true);

        // Make sure we don't seek!
        seekBar.setOnTouchListener(noTouchListener);

        // Launch settings if the stream icon is clicked.
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getId() == R.id.stream_icon) {
                    hide();
                    Intent volumeSettings = new Intent(Settings.ACTION_SOUND_SETTINGS);
                    startActivity(volumeSettings);
                }
            }
        });

        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));
        mLayout = root;
    }

    /*package*/ void openMusic() {
        if (mMusicActive) {
            hide();
            launchMusicApp();
        }
    }

    /*package*/ void updatePlayState() {
        playPause.setImageResource(((mMusicActive) ? R.drawable.ic_bb_pause : R.drawable.ic_bb_play));
    }

    @Override
    public void setTertiaryColor(final int newColor) {
        super.setTertiaryColor(newColor);
        streamName.setTextColor(newColor);
        song.setTextColor(newColor);
        artist.setTextColor(newColor);
    }

    // Used to keep track if we're displaying album art.
    protected boolean hasAlbumArt = false;
    protected boolean hideMusicWithPanel = false;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onPlayStateChanged(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        if (!created) return;
        super.onPlayStateChanged(mediaInfo);
        LOGI(TAG, "onPlayStateChanged()");

        // if (mMusicActive) transition.beginDelayedTransition(mediaContainer, TransitionCompat.KEY_AUDIO_TRANSITION);

        // Update button visibility based on the transport flags.
        /*if (null == info || info.mTransportControlFlags <= 0) {
            mBtnNext.setVisibility(View.VISIBLE);
            mBtnPrev.setVisibility(View.VISIBLE);
            playPause.setVisibility(View.VISIBLE);
        } else {
            final int flags = info.mTransportControlFlags;
            setVisibilityBasedOnFlag(mBtnPrev, flags, RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);
            setVisibilityBasedOnFlag(mBtnNext, flags, RemoteControlClient.FLAG_KEY_MEDIA_NEXT);
            setVisibilityBasedOnFlag(playPause, flags,
                              RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                            | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                            | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                            | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
        }*/

        // If we have album art, use it!
        if (mMusicActive) {
            Bitmap albumArtBitmap = mediaInfo.first.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            if (null != albumArtBitmap) {
                LOGI(TAG, "Loading artwork bitmap.");
                albumArt.setImageAlpha(0xFF);
                albumArt.setColorFilter(null);
                albumArt.setImageBitmap(albumArtBitmap);
                hasAlbumArt = true;
            } else {
                hasAlbumArt = false;
            }
        }

        // Next, we'll try to display the app's icon.
        if (mMusicActive && !hasAlbumArt && !TextUtils.isEmpty(musicPackageName)) {
            Drawable appIcon = getAppIcon(musicPackageName);
            if (null != appIcon) {
                LOGI(TAG, "Loading app icon instead of album art.");
                final int bbColor = getResources().getColor(R.color.bb_icons);
                final ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0);
                albumArt.setColorFilter(new ColorMatrixColorFilter(cm));
                appIcon.setColorFilter(bbColor, PorterDuff.Mode.MULTIPLY);
                albumArt.setImageAlpha(0xEF);
                albumArt.setImageDrawable(appIcon);
                hasAlbumArt = true;
            } else {
                hasAlbumArt = false;
            }
        }

        if (!mMusicActive) hasAlbumArt = false;

        albumArtContainer.setVisibility((hasAlbumArt) ? View.VISIBLE : View.GONE);
        if (mMusicActive) setMusicPanelVisibility(View.VISIBLE);
        if (!mMusicActive) {
            hideMusicWithPanel = true;
        }

        updatePlayState();

        String sTitle = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        String sArtist = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        song.setText(sTitle);
        artist.setText(sArtist);
    }

    protected Drawable getAppIcon(String packageName) {
        PackageManager mPM = getContext().getPackageManager();
        try {
            return mPM.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException nfe) {
            LOGE(TAG, "Couldn't get app icon for `" + packageName + "`", nfe);
            return null;
        }
    }

    @Override
    public void screen(boolean on) {
        super.screen(on);
        setEnableMarquee(on);
    }

    private void setEnableMarquee(boolean enabled) {
        LOGD(TAG, "setEnableMarquee(" + enabled + ')');
        if (artist != null) artist.setSelected(enabled);
        if (song != null) song.setSelected(enabled);
    }

    @Override public void onRingerModeChange(int ringerMode) { }

    @Override public void onStreamVolumeChange(int streamType, int volume, int max) {
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        LOGI(TAG, "onStreamVolumeChange(" + VolumeManager.getStreamName(streamType) +
                ", " + volume + ", " + max + ", musicActive=" + mMusicActive + ")");
        resources.setVolume(volume);
        LayerDrawable layer = (LayerDrawable) seekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        updateIcon(resources);
        seekBar.setMax(max);
        seekBar.setProgress(volume);
        streamName.setText(resources.getDescRes());
        seekBar.setTag(resources);
        setMusicPanelVisibility((mMusicActive) ? View.VISIBLE : View.GONE);
        show();
    }

    protected void updateIcon(StreamResources sr) {
        LOGI(TAG, "updateIcon(" + VolumeManager.getStreamName(sr.getStreamType()) + ")");
        int[] icons = iconForStream(sr);
        int iconRes = ((sr.getVolume() <= 0) ? icons[1] : icons[0]);
        icon.setImageResource(iconRes);
    }

    @Override
    public void show() {
        // Don't show the music panel if we're in the music app.
        if (!TextUtils.isEmpty(musicPackageName) && !TextUtils.isEmpty(mCurrentPackage)) {
            if (mCurrentPackage.equals(musicPackageName)) {
                setMusicPanelVisibility(View.GONE);
            } else if (mMusicActive) {
                setMusicPanelVisibility(View.VISIBLE);
            }
        }

        super.show();
    }

    @Override public void onVisibilityChanged(int visibility) {
        LOGI(TAG, "onVisibilityChanged(" + visibility + ')');
        super.onVisibilityChanged(visibility);
        switch (visibility) {
            case View.GONE:
                if (hideMusicWithPanel) {
                    hideMusicWithPanel = false;
                    setMusicPanelVisibility(View.GONE);
                }
                setEnableMarquee(false);
                break;
            case View.VISIBLE:
                setEnableMarquee(true);
                break;
        }
    }

    protected void setMusicPanelVisibility(int visibility) {
        LOGI(TAG, "setMusicPanelVisibility(" + visibility + ')');
        mediaContainer.setVisibility(visibility);
        divider.setVisibility(visibility);
        mediaContainer.requestLayout();
        mediaContainer.invalidate();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onPlaybackEvent(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        // NOTE: This MUST be added to the final descendant of VolumePanel!
        LOGI(TAG, "onPlaybackEvent()");
        this.onPlayStateChanged(mediaInfo);
    }

    protected String mCurrentPackage;

    @Override
    public void onTopAppChanged(VolumeAccessibilityService.TopApp app) {
        super.onTopAppChanged(app);
        mCurrentPackage = app.mCurrentPackage;
    }

    @Override public boolean supportsMediaPlayback() { return true; }
    @Override public boolean isInteractive() { return true; }

    @Override public void onRotationChanged(final int rotation) {
        super.onRotationChanged(rotation);
        mWindowAttributes = getWindowLayoutParams();
        onWindowAttributesChanged();
    }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH     |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL         |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR      |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                0, 0, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(TAG);
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        Resources res = getResources();
        final int maxWidth = res.getDimensionPixelSize(R.dimen.notification_panel_width);
        final int menuWidth = res.getDimensionPixelSize(R.dimen.max_menu_width);
        final int screenWidth = getWindowWidth();
        if (maxWidth <= 0 && (!res.getBoolean(R.bool.isTablet) && screenWidth < menuWidth)) {
            WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL);
        } else {
            WPARAMS.gravity = (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            WPARAMS.width = (maxWidth <= 0) ? menuWidth : maxWidth;
        }
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}