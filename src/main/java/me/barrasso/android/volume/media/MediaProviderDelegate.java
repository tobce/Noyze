package me.barrasso.android.volume.media;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.RemoteControlClient;
import android.os.Build;

import com.squareup.otto.Produce;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.RemoteMetadataProvider;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.enums.PlayState;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.enums.RemoteControlFeature;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.listeners.OnArtworkChangeListener;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.listeners.OnMetadataChangeListener;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.listeners.OnPlaybackStateChangeListener;
import com.woodblockwithoutco.remotemetadataprovider.v18.media.listeners.OnRemoteControlFeaturesChangeListener;

import java.util.List;

import me.barrasso.android.volume.LogUtils;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

import com.squareup.otto.MainThreadBus;

/**
 * Delegate class to deal with {@link com.woodblockwithoutco.remotemetadataprovider.v18.media.RemoteMetadataProvider}.
 * All methods are no-ops for Android versions other than {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR2}.
 * All metadata updates are posted using Otto, {@link com.squareup.otto.Bus}. {@link java.lang.Throwable}s thrown
 * for any reason are intend to be automatically caught for easy integration.
 */
public class MediaProviderDelegate {

    // We ONLY support API 18, it's special!
    public static final boolean IS_V18 = (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR2);
    public static final String TAG = LogUtils.makeLogTag(MediaProviderDelegate.class);

    private static MediaProviderDelegate delegate;
    public synchronized static MediaProviderDelegate getDelegate(Context context) {
        if (null == delegate)
            delegate = new MediaProviderDelegate(context);
        return delegate;
    }

    protected static boolean isPlaying(PlayState state) {
        switch (state) {
            case PLAYING:
            case SKIPPING_BACKWARDS:
            case SKIPPING_FORWARDS:
            case FAST_FORWARDING:
                return true;
        }

        return false;
    }

    private RemoteMetadataProvider remoteMetadataProvider;

    protected final PlaybackInfo playbackInfo = new PlaybackInfo();
    protected final Metadata metadata = new Metadata();

    protected MediaProviderDelegate(Context context) {
        if (!IS_V18) {
            remoteMetadataProvider = null;
            return;
        }

        try {
            remoteMetadataProvider = RemoteMetadataProvider.getInstance(context);
        } catch (Throwable t) {
            LOGE(TAG, "Could not create RemoteMetadataProvider", t);
            remoteMetadataProvider = null;
            return;
        }

        MainThreadBus.get().register(this);

        // Listener, if album art is requested, for retrieving album art.
        remoteMetadataProvider.setOnArtworkChangeListener(new OnArtworkChangeListener() {
            @Override
            public void onArtworkChanged(Bitmap artwork) {
                metadata.setArtwork(artwork);
                LOGI(TAG, "onArtworkChanged(hasArtwork=" + (artwork != null) + ')');
                MainThreadBus.get().post(producePlaybackEvent());
            }
        });

        // Listener for when the music information (song, artist) has changed.
        remoteMetadataProvider.setOnMetadataChangeListener(new OnMetadataChangeListener() {
            @Override
            public void onMetadataChanged(String artist, String title, String album, String albumArtist, long duration) {
                metadata.setAlbum(album);
                metadata.setArtist(artist);
                metadata.setTitle(title);
                metadata.setDuration(duration);
                LOGI(TAG, "onMetadataChanged(" + metadata.toString() + ')');
                MainThreadBus.get().post(producePlaybackEvent());
            }
        });

        // Listener for when the playback state (paused, playing, position) has changed.
        remoteMetadataProvider.setOnPlaybackStateChangeListener(new OnPlaybackStateChangeListener() {
            @Override
            public void onPlaybackStateChanged(PlayState playbackState, long playbackPosition, float speed) {
                metadata.setPlayState(isPlaying(playbackState));
                playbackInfo.mCurrentPosMs = playbackPosition;
                playbackInfo.mSpeed = speed;
                playbackInfo.mState = getStateFromPlayState(playbackState);
                updatePackage();
                LOGI(TAG, "onPlaybackStateChanged(" + playbackInfo.toString() + ')');
                MainThreadBus.get().post(producePlaybackEvent());
                MainThreadBus.get().post(producePlaybackPosition());
            }
        });

        // Listener for when an app informs us of new control features.
        remoteMetadataProvider.setOnRemoteControlFeaturesChangeListener(new OnRemoteControlFeaturesChangeListener() {
            @Override
            public void onFeaturesChanged(List<RemoteControlFeature> remoteControlFeatures) {
                playbackInfo.mTransportControlFlags = getTransportFlagsFromFeatureList(remoteControlFeatures);
                LOGI(TAG, "onFeaturesChanged(" + playbackInfo.mTransportControlFlags + ')');
                updatePackage();
                MainThreadBus.get().post(producePlaybackEvent());
            }
        });
    }

    /*package*/ static int getStateFromPlayState(PlayState playState) {
        switch (playState) {
            case BUFFERING:
                return RemoteControlClient.PLAYSTATE_BUFFERING;
            case ERROR:
                return RemoteControlClient.PLAYSTATE_ERROR;
            case FAST_FORWARDING:
                return RemoteControlClient.PLAYSTATE_FAST_FORWARDING;
            case PAUSED:
                return RemoteControlClient.PLAYSTATE_PAUSED;
            case PLAYING:
                return RemoteControlClient.PLAYSTATE_PLAYING;
            case REWINDING:
                return RemoteControlClient.PLAYSTATE_REWINDING;
            case SKIPPING_BACKWARDS:
                return RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS;
            case SKIPPING_FORWARDS:
                return RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS;
            case STOPPED:
                return RemoteControlClient.PLAYSTATE_STOPPED;
            default:
                return RemoteControlClient.PLAYSTATE_ERROR;
        }
    }

    /*package*/ static int getTransportFlagsFromFeatureList(List<RemoteControlFeature> remoteControlFeatures) {
        int flags = 0;
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_FAST_FORWARD)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_FAST_FORWARD;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_NEXT)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_NEXT;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_PAUSE)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_PAUSE;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_PLAY)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_PLAY;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_PLAY_PAUSE)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_PREVIOUS)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_REWIND)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_REWIND;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_STOP)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_STOP;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        if (remoteControlFeatures.contains(RemoteControlFeature.USES_POSITIONING)) {
            int flag = RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;
            flags = (flags == 0) ? flag : (flags | flag);
        }
        return flags;
    }

    @Produce
    public Metadata.PlaybackPosition producePlaybackPosition() {
        return new Metadata.PlaybackPosition(playbackInfo.mCurrentPosMs);
    }

    @Produce
    public Metadata.PlaybackEvent producePlaybackEvent() {
        Metadata.PlaybackEvent event = new Metadata.PlaybackEvent();
        event.mMetadata = metadata;
        event.mPlaybackInfo = playbackInfo;
        event.mMetadata.hasRemote(true);
        return event;
    }

    protected void updatePackage() {
        // This is an optional parameter obtained via Reflection, if possible.
        // Useful for obtaining information such as the app's icon and using it in the volume_3 panel.
        if (!IS_V18 || null == remoteMetadataProvider) return;
        LOGI(TAG, "updatePackage()");
        PendingIntent intent = remoteMetadataProvider.getCurrentClientPendingIntent();
        if (null == intent) return;
        playbackInfo.mRemotePackageName = intent.getCreatorPackage();
        if (null != metadata)
            metadata.setRemotePackage(playbackInfo.mRemotePackageName);
    }

    protected boolean isAcquired = false;

    /** @see com.woodblockwithoutco.remotemetadataprovider.v18.media.RemoteMetadataProvider#acquireRemoteControls(int, int) */
    public void acquire(final int width, final int height) {
        if (!IS_V18 || remoteMetadataProvider == null) return;
        try {
            LOGI(TAG, "acquire(" + width + ", " + height + ')');
            remoteMetadataProvider.acquireRemoteControls(width, height);
            isAcquired = true;
        } catch (Throwable t) {
            LOGE(TAG, "Could not acquire IRemoteControlDisplay [" + width + ", " + height + "]", t);
        }
    }

    /** @see com.woodblockwithoutco.remotemetadataprovider.v18.media.RemoteMetadataProvider#dropRemoteControls(boolean) */
    public void relinquish(final boolean destroy) {
        if (!IS_V18 || remoteMetadataProvider == null) return;
        try {
            LOGI(TAG, "relinquish(" + destroy + ')');
            if (isAcquired) remoteMetadataProvider.dropRemoteControls(destroy);
        } catch (Throwable t) {
            LOGE(TAG, "Could not unregister IRemoteControlDisplay", t);
        }
        isAcquired = false;
    }

    /** @see com.woodblockwithoutco.remotemetadataprovider.v18.media.RemoteMetadataProvider#isClientActive() */
    public boolean isClientActive() {
        if (!IS_V18 || remoteMetadataProvider == null) return false;
        try {
            LOGI(TAG, "isClientActive()");
            return remoteMetadataProvider.isClientActive();
        } catch (Throwable t) {
            LOGE(TAG, "Couldn't call RemoteMetadataProvider#isClientActive()", t);
        }
        return false;
    }

    public void destroy() {
        LOGI(TAG, "destroy()");
        relinquish(true);
        metadata.clearArtwork();
        metadata.recycle();
        remoteMetadataProvider = null;
        delegate = null; // Remove static

        try {
            MainThreadBus.get().unregister(this);
        } catch (Throwable t) {
            LOGE(TAG, "Failed to unregister from Otto's EventBus.");
        }
    }
}