package me.barrasso.android.volume.media.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaMetadataEditor;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.Build;
import android.media.MediaMetadataRetriever;
import android.util.DisplayMetrics;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link me.barrasso.android.volume.media.compat.RemoteControlCompat} that utilizes the
 * {@link android.media.RemoteController} API (deprecated in Android 5.0 Lollipop).
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@SuppressWarnings("deprecation")
public class RemoteControlKitKat extends RemoteControlCompat
    implements RemoteController.OnClientUpdateListener {

    private static MediaMetadataCompat.Builder mediaMetadata(MediaMetadataEditor editor) {
        final Map<String, Integer> STRING_KEYS = new HashMap<>(10);
        final Map<String, Integer> LONG_KEYS = new HashMap<>(4);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_ALBUM, MediaMetadataRetriever.METADATA_KEY_ALBUM);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_TITLE, MediaMetadataRetriever.METADATA_KEY_TITLE);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataRetriever.METADATA_KEY_ARTIST);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_AUTHOR, MediaMetadataRetriever.METADATA_KEY_AUTHOR);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_COMPILATION, MediaMetadataRetriever.METADATA_KEY_COMPILATION);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_COMPOSER, MediaMetadataRetriever.METADATA_KEY_COMPOSER);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_DATE, MediaMetadataRetriever.METADATA_KEY_DATE);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_GENRE, MediaMetadataRetriever.METADATA_KEY_GENRE);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_WRITER, MediaMetadataRetriever.METADATA_KEY_WRITER);
        STRING_KEYS.put(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        LONG_KEYS.put(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
        LONG_KEYS.put(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
        LONG_KEYS.put(MediaMetadataCompat.METADATA_KEY_DURATION, MediaMetadataRetriever.METADATA_KEY_DURATION);
        LONG_KEYS.put(MediaMetadataCompat.METADATA_KEY_YEAR, MediaMetadataRetriever.METADATA_KEY_YEAR);
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        for (Map.Entry<String, Integer> entry : STRING_KEYS.entrySet())
            builder.putString(entry.getKey(), editor.getString(entry.getValue(), null));
        for (Map.Entry<String, Integer> entry : LONG_KEYS.entrySet())
            builder.putLong(entry.getKey(), editor.getLong(entry.getValue(), 0));
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                editor.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK, null));
        return builder;
    }

    /**
     * A map between {@link android.media.RemoteControlClient} flags and {@link android.media.session.PlaybackState} actions.
     * @return The value to provide for {@link android.media.session.PlaybackState} for actions.
     */
    private static long getPlaybackStateActions(final int transportControlFlags) {
        final Map<Integer, Long> FLAG_MAP = new HashMap<>();
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_STOP, PlaybackStateCompat.ACTION_STOP);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_NEXT, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_PAUSE, PlaybackStateCompat.ACTION_PAUSE);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_FAST_FORWARD, PlaybackStateCompat.ACTION_FAST_FORWARD);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_REWIND, PlaybackStateCompat.ACTION_REWIND);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_PLAY, PlaybackStateCompat.ACTION_PLAY);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE, PlaybackStateCompat.ACTION_PLAY_PAUSE);
        FLAG_MAP.put(RemoteControlClient.FLAG_KEY_MEDIA_RATING, PlaybackStateCompat.ACTION_SET_RATING);
        long actions = 0;
        for (Map.Entry<Integer, Long> flags : FLAG_MAP.entrySet()) {
            if ((transportControlFlags & flags.getKey()) == flags.getKey()) {
                if (actions == 0)
                    actions = flags.getValue();
                else
                    actions |= flags.getValue();
            }
        }
        return actions;
    }

    /**
     * @return A {@link android.support.v4.media.MediaMetadataCompat} built from
     * a {@link android.media.MediaMetadataEditor} meant to bridge the two APIs.
     */
    public static MediaMetadataCompat buildMediaMetadata(MediaMetadataEditor editor) {
        return mediaMetadata(editor).build();
    }

    protected int mTransportControlFlags;
    protected RemoteController rController;

    public RemoteControlKitKat(Context context) {
        super(context);
        registerController();
    }

    // ===== API =====

    @Override
    public boolean isRegistered() {
        return (null != rController);
    }

    @Override
    public void release() {
        if (null == rController) return;
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.unregisterRemoteController(rController);
        rController = null;
    }

    // == Registration ==

    protected void registerController() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        rController = new RemoteController(mContext, (RemoteController.OnClientUpdateListener) mContext);

        try {
            // This is a weird issue that needs more clarification.
            audioManager.registerRemoteController(rController);
        } catch (SecurityException se) {
            rController = null;
        }

        // By default an RemoteController.OnClientUpdateListener implementation will not receive bitmaps
        // for album art. Use setArtworkConfiguration(int, int) to receive images as well.
        if (null != rController) {
            Resources res = mContext.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();
            final int dim = Math.max(dm.widthPixels, dm.heightPixels);
            rController.setArtworkConfiguration(dim, dim);
        }
    }

    // == Callbacks ==

    @Override
    public void onClientChange(boolean clearing) {
        if (null == mMetadata) return;
        metadataChanged(new MediaMetadataCompat.Builder(mMetadata));
    }

    @Override
    public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
        if (null == metadataEditor) return;
        metadataChanged(mediaMetadata(metadataEditor));
    }

    @Override
    public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs, long currentPosMs, float speed) {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(state, currentPosMs, speed);
        playbackStateChanged(builder.build());
    }

    @Override
    public void onClientPlaybackStateUpdate(int state) {
        if (null == mPlaybackState) return;
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(state, rController.getEstimatedMediaPosition(), mPlaybackState.getPlaybackSpeed());
        builder.setActions(getPlaybackStateActions(mTransportControlFlags));
        playbackStateChanged(builder.build());
    }

    @Override
    public void onClientTransportControlUpdate(int transportControlFlags) {
        mTransportControlFlags = transportControlFlags;
        PlaybackStateCompat state = getPlaybackState();
        if (null == state) return;
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(state.getState(), rController.getEstimatedMediaPosition(), state.getPlaybackSpeed());
        builder.setActions(getPlaybackStateActions(transportControlFlags));
        playbackStateChanged(builder.build());
    }

    // == Remote Package Hack ==

    protected void metadataChanged(MediaMetadataCompat.Builder builder) {
        builder.putString(METADATA_KEY_PACKAGE, getPackageName());
        metadataChanged(builder.build());
    }

    private static Method mGetRemoteControlClientPackageName;

    static {
        try {
            mGetRemoteControlClientPackageName = RemoteController.class.getDeclaredMethod("getRemoteControlClientPackageName");
            if (null != mGetRemoteControlClientPackageName)
                mGetRemoteControlClientPackageName.setAccessible(true);
        } catch (NoSuchMethodException e) { }
    }

    protected String getPackageName() {
        if (null != mGetRemoteControlClientPackageName) {
            try {
                return (String) mGetRemoteControlClientPackageName.invoke(rController);
            } catch (Throwable t) { }
        }
        return "";
    }
}