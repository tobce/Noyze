package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
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
import android.widget.SeekBar;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import java.io.FileNotFoundException;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.media.compat.RemoteControlCompat;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.Utils;
import me.barrasso.android.volume.utils.VolumeManager;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Android "L" style (API 21) Heads Up Notifications... but for volume_3!
 * TODO: support song seeking like the Android lockscreen.
 */
public final class HeadsUpVolumePanel extends VolumePanel {

    public static final String TAG = HeadsUpVolumePanel.class.getSimpleName();

    static final int _COLOR = Color.argb(204, 0, 0, 0);

    public static final VolumePanelInfo<HeadsUpVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<HeadsUpVolumePanel>(HeadsUpVolumePanel.class);

    public HeadsUpVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    private TransitionCompat transition;

    TextView artist;
    TextView song;
    TextView clock;
    ImageView icon;
    ProgressBar seekBar;
    ViewGroup root, musicPanel, visiblePanel;
    ImageButton playPause, mBtnNext, mBtnPrev;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();

        transition = TransitionCompat.get();

        LayoutInflater inflater = LayoutInflater.from(context);
        root = (ViewGroup) inflater.inflate(R.layout.hu_volume_adjust, null);
        seekBar = (ProgressBar) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMusic();
            }
        });
        clock = (TextView) root.findViewById(android.R.id.text2);
        artist = (TextView) root.findViewById(R.id.track_artist);
        song = (TextView) root.findViewById(R.id.track_song);
        musicPanel = (ViewGroup) root.findViewById(R.id.music_panel);
        attachPlaybackListeners(root, new MediaButtonClickListener(), new MusicLongClickListener());
        playPause = (ImageButton) root.findViewById(R.id.media_play_pause);
        mBtnNext = (ImageButton) root.findViewById(R.id.media_next);
        mBtnPrev = (ImageButton) root.findViewById(R.id.media_previous);
        visiblePanel = (ViewGroup) root.findViewById(R.id.visible_panel);
        toggleSeekBar(seek);
        setEnableMarquee(true);
        updateMediaIcons();
        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));
        mLayout = root;
    }

    protected void updateMediaIcons() {
        LOGI(TAG, "updateMediaIcons()");
        Resources res = getResources();
        Drawable next = res.getDrawable(R.drawable.ic_media_next_white);
        Drawable prev = res.getDrawable(R.drawable.ic_media_previous_white);
        next.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        prev.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        mBtnNext.setImageDrawable(next);
        mBtnPrev.setImageDrawable(prev);
        updatePlayState();
    }

    @Override public void setBackgroundColor(final int newColor) {
        super.setBackgroundColor(newColor);
        visiblePanel.setBackground(new ColorDrawable(newColor));
    }

    @Override public void setColor(final int newColor) {
        super.setColor(newColor);
        toggleSeekBar(seek);
        updateMediaIcons();
        updateIcon((StreamResources) seekBar.getTag());
    }

    @Override
    public void setTertiaryColor(final int newColor) {
        super.setTertiaryColor(newColor);
        song.setTextColor(newColor);
        artist.setTextColor(newColor);
        clock.setTextColor(newColor);
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
                thumb.setBounds(0,0, thumb.getIntrinsicWidth(), thumb.getIntrinsicHeight());
            }
            seeker.setThumb(thumb);
            // NOTE: there's so weird issue with setting the thumb dynamically.
            // This seems to do the trick (fingers crossed).
            Utils.tap((View) seeker.getParent());
            seeker.invalidate();
        }
    }

    /*package*/ class MusicLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View view) {
            LOGD("MusicTouchListener", "onLongClick(" + view.getId() + ")");
            switch (view.getId()) {
                case R.id.media_play_pause:
                case R.id.stream_icon:
                    openMusic();
                    return true;
            }

            return false;
        }
    }

    // Bit of a misnomer because it'll launch settings when music isn't playing.
    /*package*/ void openMusic() {
        hide();
        if (hasAlbumArt) {
            launchMusicApp();
        } else {
            Intent volumeSettings = new Intent(Settings.ACTION_SOUND_SETTINGS);
            startActivity(volumeSettings);
        }
    }

    /*package*/ void updatePlayState() {
        int icon = ((mMusicActive) ? R.drawable.ic_media_pause_white : R.drawable.ic_media_play_white);
        Drawable play = getResources().getDrawable(icon);
        play.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        playPause.setImageDrawable(play);
    }

    // Used to keep track if we're displaying album art.
    protected boolean hasAlbumArt = false;
    protected boolean hasDuration = false;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onPlayStateChanged(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        if (!created) return;
        super.onPlayStateChanged(mediaInfo);
        LOGI(TAG, "onPlayStateChanged()");
        LOGI(TAG, RemoteControlCompat.getMediaMetadataLog(mediaInfo.first));

        String sTitle = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
        String sArtist = mediaInfo.first.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        boolean hasArtist = !TextUtils.isEmpty(sArtist);
        boolean hasTitle = !TextUtils.isEmpty(sTitle);
        boolean missingData = (!hasArtist || !hasTitle);

        if (mMusicActive && !missingData) {
            transition.beginDelayedTransition(musicPanel, TransitionCompat.KEY_AUDIO_TRANSITION);
        }

        updatePlayState();
        song.setText(sTitle);
        artist.setText(sArtist);
        artist.setVisibility(View.VISIBLE);
        Object tag = seekBar.getTag();

        // Update button visibility based on the transport flags.
        if (null == mediaInfo.second || !mMusicActive || mediaInfo.second.getActions() <= 0) {
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

        // If we have album art, use it!
        if (mMusicActive) {
            Bitmap albumArt = null;
            try {
                albumArt = RemoteControlCompat.getBitmap(getContext(), mediaInfo.first);
            } catch (FileNotFoundException fne) {
                LOGE(TAG, "Album art URI invalid.", fne);
            }
            if (null != albumArt) {
                LOGI(TAG, "Loading album art from RemoteController.");
                albumArt = Bitmap.createScaledBitmap(albumArt, icon.getWidth(), icon.getHeight(), false);
                icon.setImageAlpha(0xFF);
                icon.setColorFilter(null);
                icon.setImageBitmap(albumArt);
                hasAlbumArt = true;
            } else {
                hasAlbumArt = false;
            }
        }

        // Next, we'll try to display the app's icon.
        if (mMusicActive && !hasAlbumArt && !TextUtils.isEmpty(musicPackageName)) {
            Drawable appIcon = null;
            try {
                Bitmap iconBmp = RemoteControlCompat.getBitmap(getContext(), mediaInfo.first);
                if (null != iconBmp) {
                    appIcon = new BitmapDrawable(getContext().getResources(), iconBmp);
                    LOGI(TAG, "App icon loaded from MediaMetadata instead.");
                }
            } catch (FileNotFoundException fne) {
                LOGE(TAG, "Album art URI invalid.", fne);
            }
            if (null == appIcon)
                appIcon = getAppIcon(musicPackageName);
            if (null != appIcon) {
                LOGI(TAG, "Loading app icon instead of album art.");
                final ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0);
                appIcon.setColorFilter(new ColorMatrixColorFilter(cm));
                icon.setColorFilter(_COLOR, PorterDuff.Mode.MULTIPLY);
                icon.setImageAlpha(0xEF);
                icon.setImageDrawable(appIcon);
                hasAlbumArt = true;
            } else {
                hasAlbumArt = false;
            }
        }

        if (!mMusicActive) hasAlbumArt = false;

        // If music isn't playing, display the stream mode text.
        if (tag instanceof StreamResources) {
            StreamResources sr = (StreamResources) tag;
            if ((!mMusicActive || missingData)) {
                song.setText(getStreamName(sr));
                artist.setText(null);
                artist.setVisibility(View.GONE);
            }

            if (!hasAlbumArt) updateIcon(sr);
        }
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

    @Override public void onStreamVolumeChange(int streamType, int volume, int max) {
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        LOGD(TAG, "onStreamVolumeChange(" + VolumeManager.getStreamName(streamType) +
                ", " + volume + ", " + max + ")");
        resources.setVolume(volume);
        if (!hasAlbumArt) updateIcon(resources);
        if (!hasDuration) {
            seekBar.setMax(max);
            seekBar.setProgress(volume);
        }
        LayerDrawable layer = (LayerDrawable) seekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        seekBar.setTag(resources);
        if (!mMusicActive) {
            int descRes = resources.getDescRes();
            // Display silent/ vibrate based on the ringer setting.
            if (resources.getVolume() <= 0 &&
                    (resources == StreamResources.RingerStream ||
                     resources == StreamResources.NotificationStream)) {
                if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    descRes = R.string.vibrate_c;
                } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                    descRes = R.string.silent_c;
                }
            }
            song.setText(descRes);
            artist.setText(null);
            artist.setVisibility(View.GONE);
        }
        show();
    }

    protected void updateIcon(StreamResources sr) {
        if (null == sr) return;
        LOGI(TAG, "updateIcon(" + sr.getStreamType() + ')');
        int iconRes = ((sr.getVolume() <= 0) ? sr.getIconMuteRes() : sr.getIconRes());
        Drawable drawable = getResources().getDrawable(iconRes);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        icon.setImageAlpha(0xFF);
        icon.setImageDrawable(drawable);
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

    @SuppressWarnings("unused")
    @Subscribe
    public void onPlaybackEvent(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        // NOTE: This MUST be added to the final descendant of VolumePanel!
        LOGI(TAG, "onPlaybackEvent()");
        this.onPlayStateChanged(mediaInfo);
    }

    @Override public boolean supportsMediaPlayback() { return true; }
    @Override public boolean isInteractive() { return true; }

    @Override public void onRotationChanged(final int rotation) {
        super.onRotationChanged(rotation);
        mWindowAttributes = getWindowLayoutParams();
        onWindowAttributesChanged();
    }

    @Override public void setStretch(boolean stretchy) {
        super.setStretch(stretchy);
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
        WPARAMS.windowAnimations = android.R.style.Animation_Translucent;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(TAG);
        WPARAMS.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        Resources res = getResources();
        final int panelWidth = getNotificationPanelWidth();
        final int maxWidth = ((panelWidth > 0) ? panelWidth : res.getDimensionPixelSize(R.dimen.notification_panel_width));
        final int menuWidth = res.getDimensionPixelSize(R.dimen.max_menu_width);
        final int screenWidth = getWindowWidth();
        if (stretch || (maxWidth <= 0 && (!res.getBoolean(R.bool.isTablet) && screenWidth < menuWidth))) {
            WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
        } else {
            WPARAMS.gravity = (Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            WPARAMS.width = (maxWidth <= 0) ? menuWidth : maxWidth;
        }
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }
}