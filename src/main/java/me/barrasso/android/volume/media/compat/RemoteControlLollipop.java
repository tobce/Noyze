package me.barrasso.android.volume.media.compat;

import android.annotation.TargetApi;
import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Pair;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link me.barrasso.android.volume.media.compat.RemoteControlCompat} that utilizes the
 * {@link android.media.session.MediaController} API for Android 5.0 Lollipop.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RemoteControlLollipop extends RemoteControlCompat
    implements MediaSessionManager.OnActiveSessionsChangedListener {

    private final ComponentName mControllerService;
    private final Map<MediaSession.Token, Pair<MediaController, MediaController.Callback>> mControllers;
    private final MediaSessionManager mMediaSessionManager;

    private boolean mRegistered;

    protected RemoteControlLollipop(Context context, Class<? extends NotificationListenerService> clazz) {
        super(context);
        mControllerService = new ComponentName(context, clazz);
        mControllers = new ConcurrentHashMap<>();
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mMediaSessionManager.addOnActiveSessionsChangedListener(this, mControllerService);
        mRegistered = true;
    }

    @Override
    public boolean isRegistered() {
        return mRegistered;
    }

    @Override
    public void release() {
        mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
        unregister();
        mRegistered = false;
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        int controllerCount = ((null == controllers) ? 0 : controllers.size());

        Set<MediaSession.Token> tokens = new HashSet<>(controllerCount);
        for (int i = 0; i < controllerCount; ++i) {
            MediaController controller = controllers.get(i);
            tokens.add(controller.getSessionToken());
            // Only add tokens that we don't already have.
            if (!mControllers.containsKey(controller.getSessionToken())) {
                MediaController.Callback callback = new Callback(controller);
                controller.registerCallback(callback);
                Pair<MediaController, MediaController.Callback> pair = Pair.create(controller, callback);
                synchronized (mControllers) {
                    mControllers.put(controller.getSessionToken(), pair);
                }
                callback.onMetadataChanged(controller.getMetadata());
                callback.onPlaybackStateChanged(controller.getPlaybackState());
            }
        }

        // Now remove old sessions that are not longer active.
        for (Map.Entry<MediaSession.Token, Pair<MediaController, MediaController.Callback>> entry : mControllers.entrySet()) {
            MediaSession.Token token = entry.getKey();
            if (!tokens.contains(token)) {
                Pair<MediaController, MediaController.Callback> pair = entry.getValue();
                pair.first.unregisterCallback(pair.second);
                synchronized (mControllers) {
                    mControllers.remove(token);
                }
            }
        }
    }

    private void unregister() {
        for (Map.Entry<MediaSession.Token, Pair<MediaController, MediaController.Callback>> entry : mControllers.entrySet()) {
            Pair<MediaController, MediaController.Callback> pair = entry.getValue();
            pair.first.unregisterCallback(pair.second);
        }
        synchronized (mControllers) {
            mControllers.clear();
        }
    }

    // Bridge to handle callbacks from MediaControllers.
    protected class Callback extends MediaController.Callback {

        private MediaController mController;

        public Callback(MediaController controller) {
            mController = controller;
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (null == metadata) return;
            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(
                    MediaMetadataCompat.fromMediaMetadata(metadata));
            builder.putString(METADATA_KEY_PACKAGE, mController.getPackageName());
            metadataChanged(builder.build());
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (null == state) return;
            playbackStateChanged(PlaybackStateCompat.fromPlaybackState(state));
        }
    }
}