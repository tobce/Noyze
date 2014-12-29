package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.apps.dashclock.configuration.ColorPreference;
import com.squareup.otto.Subscribe;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.VolumeAccessibilityService;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.media.compat.RemoteControlCompat;
import me.barrasso.android.volume.media.conditions.RingerNotificationLink;
import me.barrasso.android.volume.ui.transition.TransitionCompat;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Utils;
import me.barrasso.android.volume.utils.VolumeManager;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;
import static me.barrasso.android.volume.LogUtils.LOGW;

/**
 * Referred to as "Drop" in the app.<br />
 * An original theme, like {@link me.barrasso.android.volume.popup.HeadsUpVolumePanel}. It's an
 * awesome theme because it includes BOTH multiple-channel support (like {@link me.barrasso.android.volume.popup.ParanoidVolumePanel}
 * AND contextual music controls (only while music is playing). It's the most useful theme yet!
 */
public class UberVolumePanel extends VolumePanel implements AdapterView.OnItemSelectedListener {

    public static final String TAG = UberVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<UberVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<UberVolumePanel>(UberVolumePanel.class);

    public UberVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    private TransitionCompat transition;

    private boolean hasAlbumArt;

    View divider;
    ImageView album;
    TextView artist;
    TextView song;
    Spinner spinner;
    ProgressBar seekBar;
    ViewGroup root, musicPanel, sliderGroup, visiblePanel;
    ImageButton playPause, mBtnNext;

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();
        transition = TransitionCompat.get();

        boolean darkColor = ColorPreference.isColorDark(color);
        int theme = (darkColor) ? android.R.style.Theme_Holo_Light : android.R.style.Theme_Holo;
        Context themeContext = new ContextThemeWrapper(context, theme);
        context.getApplicationContext().setTheme(theme);
        LayoutInflater inflater = LayoutInflater.from(themeContext);
        FrameLayout parent = new FrameLayout(themeContext);
        root = (ViewGroup) inflater.inflate(R.layout.uber_volume_adjust, parent, false);
        context.getApplicationContext().setTheme(R.style.AppTheme);

        visiblePanel = (ViewGroup) root.findViewById(R.id.visible_panel);
        seekBar = (ProgressBar) root.findViewById(android.R.id.progress);
        spinner = (Spinner) root.findViewById(R.id.stream_icon);
        album = (ImageView) root.findViewById(R.id.album_art);
        artist = (TextView) root.findViewById(R.id.track_artist);
        song = (TextView) root.findViewById(R.id.track_song);
        musicPanel = (ViewGroup) root.findViewById(R.id.music_panel);
        divider = root.findViewById(R.id.divider);
        playPause = (ImageButton) root.findViewById(R.id.media_play_pause);
        mBtnNext = (ImageButton) root.findViewById(R.id.media_next);

        album.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMusic();
            }
        });

        LayerDrawable layer = (LayerDrawable) seekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate()
                .setColorFilter(HeadsUpVolumePanel._COLOR, PorterDuff.Mode.MULTIPLY);
        attachPlaybackListeners(root, new MediaButtonClickListener());

        toggleSeekBar(seek);
        setEnableMarquee(true);

        initSpinner();
        updateMediaIcons();

        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));
        mLayout = root;
    }

    @Override
    public void setBackgroundColor(final int newColor) {
        super.setBackgroundColor(newColor);
        visiblePanel.setBackgroundColor(newColor);
        spinner.setPopupBackgroundDrawable(new ColorDrawable(backgroundColor));
        spinner.invalidate();
    }

    @Override
    public void setColor(final int newColor) {
        super.setColor(newColor);
        toggleSeekBar(seek);
        updateMediaIcons();
        StreamAdapter adapter = ((StreamAdapter) spinner.getAdapter());
        adapter.setColor(newColor);
        adapter.notifyDataSetChanged();
        spinner.invalidate();
    }

    @Override
    public void setTertiaryColor(final int newColor) {
        super.setTertiaryColor(newColor);
        song.setTextColor(newColor);
        artist.setTextColor(newColor);
    }

    @Override
    protected void adjustVolume(int direction) {
        LOGI(TAG, "adjustVolume(" + VolumeManager.getStreamName(mCurrentStream) + ", " + direction + ')');
        if (mCurrentStream == AudioManager.USE_DEFAULT_STREAM_TYPE) {
            super.adjustVolume(direction);
        } else {
            lastDirection = direction; // Needed because we don't call super
            adjustStreamVolume(direction, mCurrentStream);
        }
    }

    private int mCurrentStream = AudioManager.USE_DEFAULT_STREAM_TYPE;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        StreamAdapter adapter = ((StreamAdapter) spinner.getAdapter());
        LOGI(TAG, "onItemSelected(" + pos + ", " + id + ')');
        StreamResources resources = StreamResources.resourceForStreamType(mStreamIndices.keyAt(pos));
        seekBar.setMax(getStreamMaxVolume(resources.getStreamType()));
        seekBar.setProgress(getStreamVolume(resources.getStreamType()));
        seekBar.setTag(resources);
        mCurrentStream = resources.getStreamType();
        adapter.notifyDataSetChanged();
        spinner.invalidate();
    }

    @Override public void onNothingSelected(AdapterView<?> parent) { }

    protected void loadSystemSettings() {
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

    private SparseIntArray mStreamIndices;

    protected void initSpinner() {
        if (null == mAudioManager) mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        loadSystemSettings();
        boolean mVoiceCapable = mAudioHelper.isVoiceCapable();
        List<StreamResources> streams = new ArrayList<StreamResources>();
        RingerNotificationLink linkChecker = new RingerNotificationLink();
        mNotificationRingerLink = linkChecker.apply(mAudioManager);
        for (StreamResources stream : StreamResources.STREAMS) {
            final int streamType = stream.getStreamType();
            if (!stream.show()) {
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
            streams.add(stream);
        }

        int i = 0;
        mStreamIndices = new SparseIntArray(streams.size());
        for (StreamResources stream : streams) {
            mStreamIndices.put(stream.getStreamType(), i);
            stream.setVolume(getStreamVolume(stream.getStreamType()));
            ++i;
        }

        StreamAdapter adapter = new StreamAdapter(getContext(), 0, streams);
        adapter.setDropDownViewResource(R.layout.spinner_icon);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        spinner.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                    onUserInteraction();
                return view.onTouchEvent(motionEvent);
            }
        });
        adapter.setWidth(Math.min(spinner.getWidth(), spinner.getHeight()));
    }

    protected String mCurrentPackage;

    @Override
    public void show() {
        // Don't show the music panel if we're in the music app.
        if (!TextUtils.isEmpty(musicPackageName) && !TextUtils.isEmpty(mCurrentPackage)) {
            if (mCurrentPackage.equals(musicPackageName)) {
                setMusicVisibility(View.GONE);
            } else if (mMusicActive) {
                setMusicVisibility(View.VISIBLE);
            }
        }

        super.show();
    }

    @Override
    public void onTopAppChanged(VolumeAccessibilityService.TopApp app) {
        super.onTopAppChanged(app);
        mCurrentPackage = app.mCurrentPackage;
    }

    @Override public void setSeek(final boolean shouldSeek) {
        super.setSeek(shouldSeek);
        toggleSeekBar(shouldSeek);
    }

    @Override
    public void screen(boolean on) {
        super.screen(on);
        setEnableMarquee(on);
    }

    @Override public void setOneVolume(boolean one) { /* No-op */ }

    private void setEnableMarquee(boolean enabled) {
        LOGD(TAG, "setEnableMarquee(" + enabled + ')');
        if (artist != null) artist.setSelected(enabled);
        if (song != null) song.setSelected(enabled);
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

    public void setMusicVisibility(int visibility) {
        LOGI(TAG, "setMusicVisibility(" + visibility + ')');
        divider.setVisibility(visibility);
        musicPanel.setVisibility(visibility);
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // TODO: set the spinner to the stream matching the change.
        // All changes to the SeekBar should be handled by this stream.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        spinner.setSelection(mStreamIndices.get(streamType));
        StreamAdapter adapter = ((StreamAdapter) spinner.getAdapter());
        adapter.setColor(color);
        adapter.notifyDataSetChanged();
        spinner.invalidate();

        LayerDrawable layer = (LayerDrawable) seekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        visiblePanel.setBackgroundColor(backgroundColor);
        seekBar.setMax(max);
        seekBar.setProgress(volume);
        seekBar.setTag(resources);

        show();
    }

    protected boolean hideMusicWithPanel;

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

        // Update button visibility based on the transport flags.
        if (null == mediaInfo.second || mediaInfo.second.getActions() <= 0) {
            mBtnNext.setVisibility(View.VISIBLE);
            playPause.setVisibility(View.VISIBLE);
        } else {
            final long flags = mediaInfo.second.getActions();
            setVisibilityBasedOnFlag(mBtnNext, flags, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
            setVisibilityBasedOnFlag(playPause, flags,
                              PlaybackStateCompat.ACTION_PLAY
                            | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        }

        song.setText(sTitle);
        artist.setText(sArtist);

        // If we have album art, use it!
        if (mMusicActive) {
            Bitmap albumArtBitmap = null;
            try {
                albumArtBitmap = RemoteControlCompat.getBitmap(getContext(), mediaInfo.first);
            } catch (FileNotFoundException fne) {
                LOGE(TAG, "Album art URI invalid.", fne);
            }
            if (null != albumArtBitmap) {
                LOGI(TAG, "Loading artwork bitmap.");
                album.setImageAlpha(0xFF);
                album.setColorFilter(null);
                album.setImageBitmap(albumArtBitmap);
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
                album.setColorFilter(new ColorMatrixColorFilter(cm));
                appIcon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                album.setImageAlpha(0xEF);
                album.setImageDrawable(appIcon);
                hasAlbumArt = true;
            } else {
                hasAlbumArt = false;
            }
        }

        album.setVisibility((hasAlbumArt) ? View.VISIBLE : View.GONE);
        if (!mMusicActive) {
            hideMusicWithPanel = true;
        }
        updatePlayState();
        if (mMusicActive) setMusicVisibility(View.VISIBLE);
    }

    protected void updateMediaIcons() {
        LOGI(TAG, "updateMediaIcons()");
        Resources res = getResources();
        Drawable next = res.getDrawable(R.drawable.ic_media_next_white);
        next.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        mBtnNext.setImageDrawable(next);
        updatePlayState();
    }

    /*package*/ void updatePlayState() {
        int icon = ((mMusicActive) ? R.drawable.ic_media_pause_white : R.drawable.ic_media_play_white);
        Drawable play = getResources().getDrawable(icon);
        play.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        playPause.setImageDrawable(play);
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

    @Override public void onVisibilityChanged(int visibility) {
        LOGI(TAG, "onVisibilityChanged(" + visibility + ')');
        super.onVisibilityChanged(visibility);
        switch (visibility) {
            case View.GONE:
                if (hideMusicWithPanel) {
                    hideMusicWithPanel = false;
                    setMusicVisibility(View.GONE);
                }
                setEnableMarquee(false);
                mCurrentStream = AudioManager.USE_DEFAULT_STREAM_TYPE;
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

    @Override public boolean isInteractive() { return true; }
    @Override public boolean supportsMediaPlayback() { return true; }

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

    @Override
    public WindowManager.LayoutParams getWindowLayoutParams() {
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

    // Adapter for dealing with stream icons.
    static class StreamAdapter extends ArrayAdapter<StreamResources> {

        private int color = HeadsUpVolumePanel._COLOR;
        private final LayoutInflater inflater;
        private int size = 0;

        public StreamAdapter(Context context, int layout, List<StreamResources> items) {
            super(context, layout, items);
            inflater = LayoutInflater.from(context);
        }

        public void setColor(final int colour) {
            color = colour;
        }
        public void setWidth(final int newSize) { size = newSize; }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            LOGI(TAG, "getDropDownView(" + position + ")");
            return getView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView icon;
            if (null == convertView) {
                View layout = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
                icon = (TextView) layout.findViewById(android.R.id.text1);
            } else {
                icon = (TextView) convertView.findViewById(android.R.id.text1);
            }

            // Update the icon accordingly. It's awkward, but if we use a custom layout then shit looks weird.
            StreamResources resources = getItem(position);
            LOGI(TAG, "getView(" + position + "), stream = " + VolumeManager.getStreamName(resources.getStreamType()));
            int iconRes = (resources.getVolume() <= 0 ? resources.getIconMuteRes() : resources.getIconRes());
            Drawable drawable = getContext().getResources().getDrawable(iconRes);
            drawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            final int defIconSize = Resources.getSystem().getDimensionPixelSize(android.R.dimen.app_icon_size);
            int iconSize = defIconSize;
            if (size > 0) {
                iconSize = size;
            } else {
                if (position > 0) iconSize = ((iconSize * 3) / 4);
                else              iconSize = ((iconSize * 4) / 3);
            }
            drawable.setBounds(0, 0, iconSize, iconSize);
            icon.setGravity(Gravity.CENTER);
            icon.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            icon.setCompoundDrawablePadding(defIconSize / 5);
            icon.setPadding((defIconSize / 6), (defIconSize / 5), (defIconSize / 6), (defIconSize / 5));

            return icon;
        }
    }
}