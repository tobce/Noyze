package me.barrasso.android.volume.media.compat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import java.io.FileNotFoundException;

/**
 * Compatibility class for dealing with {@link android.media.RemoteController}
 * and {@link android.media.session.MediaController} APIs.
 */
public abstract class RemoteControlCompat {

    /** {@link java.lang.String} extra with the package name of the originating app. */
    public static final String METADATA_KEY_PACKAGE = "package";

    /** {@link java.lang.Long} extra with the time, in milliseconds, when the event occurred. */
    public static final String METADATA_KEY_TIMESTAMP = "timestamp";

    /**
     * @return The {@link android.graphics.Bitmap} references by {@link android.support.v4.media.MediaMetadataCompat}
     * @throws FileNotFoundException If an error parsing the {@link android.net.Uri} occurred.
     */
    public static Bitmap getBitmap(Context context, MediaMetadataCompat metadata) throws FileNotFoundException{
        if (null == metadata) return null;
        Bitmap albumArtBitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        // No album art... check the URI.
        if (null == albumArtBitmap) {
            String albumArtUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
            // Still no URI... check the art URI.
            if (TextUtils.isEmpty(albumArtUri))
                albumArtUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
            // If we've got a URI, try to load it.
            if (!TextUtils.isEmpty(albumArtUri)) {
                ContentResolver cr = context.getContentResolver();
                albumArtBitmap = BitmapFactory.decodeStream(cr.openInputStream(Uri.parse(albumArtUri)));
            }
        }
        return albumArtBitmap;
    }

    /** @see {@link #getBitmap(android.content.Context, android.support.v4.media.MediaMetadataCompat)} */
    public static Bitmap getIcon(Context context, MediaMetadataCompat metadata) throws FileNotFoundException{
        if (null == metadata) return null;
        Bitmap albumArtBitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
        // No icon... check the URI.
        if (null == albumArtBitmap) {
            String albumArtUri = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);
            // If we've got a URI, try to load it.
            if (!TextUtils.isEmpty(albumArtUri)) {
                ContentResolver cr = context.getContentResolver();
                albumArtBitmap = BitmapFactory.decodeStream(cr.openInputStream(Uri.parse(albumArtUri)));
            }
        }
        return albumArtBitmap;
    }

    /** Listener for media-related events. */
    public static interface MediaControlListener {
        void onMetadataChanged(MediaMetadataCompat metadata);
        void onPlaybackStateChanged(PlaybackStateCompat state);
    }

    public static RemoteControlCompat get(Context context, Class<? extends NotificationListenerService> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return new RemoteControlLollipop(context, clazz);
        return new RemoteControlKitKat(context);
    }

    public static boolean isPlaying(PlaybackStateCompat state) {
        if (null == state) return false;
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_REWINDING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                return true;
        }
        return false;
    }

    protected MediaControlListener mListener;
    protected final Context mContext;

    // The last state and metadata change.
    protected PlaybackStateCompat mPlaybackState;
    protected MediaMetadataCompat mMetadata;

    public MediaMetadataCompat getMetadata() { return mMetadata; }
    public PlaybackStateCompat getPlaybackState() { return mPlaybackState; }

    public RemoteControlCompat(Context context) {
        mContext = context;
    }

    public void setMediaControlListener(MediaControlListener listener) {
        mListener = listener;
    }

    public void release() { /* No-op */ }
    abstract boolean isRegistered();

    protected void metadataChanged(MediaMetadataCompat metadata) {
        // Append a timestamp to know when this event occurred.
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(metadata);
        builder.putLong(METADATA_KEY_TIMESTAMP, System.currentTimeMillis());
        mMetadata = builder.build();
        if (null != mListener && null != mMetadata && null != mPlaybackState)
            mListener.onMetadataChanged(metadata);
    }

    protected void playbackStateChanged(PlaybackStateCompat state) {
        mPlaybackState = state;
        if (null != mListener && null != mMetadata && null != mPlaybackState)
            mListener.onPlaybackStateChanged(state);
    }

    /**
     * @return {@link android.Manifest.permission#MEDIA_CONTENT_CONTROL}
     */
    protected final String getPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            permission = Manifest.permission.MEDIA_CONTENT_CONTROL;
        } else {
            permission = "android.permission.MEDIA_CONTENT_CONTROL";
        }
        return permission;
    }

    /**
     * @see android.Manifest.permission#MEDIA_CONTENT_CONTROL
     * @return True if this app holds the media control permission.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public final boolean hasPermission() {
        PackageManager pm = mContext.getPackageManager();
        int result = pm.checkPermission(getPermission(), mContext.getPackageName());
        return (result == PackageManager.PERMISSION_GRANTED);
    }

    /** @return {@link android.support.v4.media.MediaMetadataCompat} log info (what keys it contains). */
    public static String getMediaMetadataLog(MediaMetadataCompat metadata) {
        if (null == metadata) return "";
        final String[] METADATA_KEYS = new String[] {
            MediaMetadataCompat.METADATA_KEY_ALBUM,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadataCompat.METADATA_KEY_ART,
            MediaMetadataCompat.METADATA_KEY_ARTIST,
            MediaMetadataCompat.METADATA_KEY_ART_URI,
            MediaMetadataCompat.METADATA_KEY_AUTHOR,
            MediaMetadataCompat.METADATA_KEY_COMPILATION,
            MediaMetadataCompat.METADATA_KEY_COMPOSER,
            MediaMetadataCompat.METADATA_KEY_DATE,
            MediaMetadataCompat.METADATA_KEY_DISC_NUMBER,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
            MediaMetadataCompat.METADATA_KEY_DURATION,
            MediaMetadataCompat.METADATA_KEY_GENRE,
            MediaMetadataCompat.METADATA_KEY_NUM_TRACKS,
            MediaMetadataCompat.METADATA_KEY_RATING,
            MediaMetadataCompat.METADATA_KEY_TITLE,
            MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
            MediaMetadataCompat.METADATA_KEY_USER_RATING,
            MediaMetadataCompat.METADATA_KEY_WRITER,
            MediaMetadataCompat.METADATA_KEY_YEAR };
        StringBuffer builder = new StringBuffer("{");
        for (String key : METADATA_KEYS)
            builder.append(key).append('=').append(
                    (metadata.containsKey(key)) ? metadata.getText(key) : false).append(',');
        builder.append('}');
        return builder.toString();
    }
}