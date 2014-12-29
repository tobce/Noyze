package me.barrasso.android.volume;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RemoteController;
import android.os.*;
import android.os.Process;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;

import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.squareup.otto.MainThreadBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.barrasso.android.volume.media.compat.RemoteControlCompat;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

// NOTE: DO NOT CHANGE THIS CLASS NAME ONCE PUBLISHED!
// Aside from being part of the system-app API interaction, we've had to
// hardcode its class name to avoid API compatibility issues (because it
// descends from NotificationListenerService, trying to access anything
// on it will result in a NoClassDefFoundError). This only applies to
// JellyBean MR2 (4.3)

/**
 * Simple {@link android.service.notification.NotificationListenerService} meant to be used
 * merely as a proxy for the media-related events we care about. Broadcasts an {@link android.content.Intent}
 * locally for our app to consume and monitor media events (sent locally to avoid issues relating
 * to performance and security).
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class MediaControllerService extends NotificationListenerService
    implements RemoteControlCompat.MediaControlListener, RemoteController.OnClientUpdateListener {

    public static final String ACTION_REQUEST_INTERRUPTION_FILTER =
            MediaControllerService.class.getPackage().getName() + '.' + "ACTION_REQUEST_INTERRUPTION_FILTER";
    public static final String EXTRA_FILTER = "filter";

    // Must remain as such to monitor the SharedPreferences.
    public static final String TAG = MediaControllerService.class.getSimpleName();

    /** @return True if {@link VolumeAccessibilityService} is enabled. */
    public static boolean isEnabled(Context mContext) {
        return Utils.isNotificationListenerServiceRunning(mContext, MediaControllerService.class);
    }

    /** @return True if {@link VolumeAccessibilityService} is running. */
    public static boolean isRunning(Context context) {
        return Utils.isMyServiceRunning(context, MediaControllerService.class);
    }

    protected final Map<String, Bitmap> mLargeIconMap = new ConcurrentHashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        LOGI(TAG, "onNotificationPosted(" + sbn.getPackageName() + ')');
        super.onNotificationPosted(sbn);
        mLargeIconMap.put(sbn.getPackageName(), sbn.getNotification().largeIcon);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        LOGI(TAG, "onNotificationRemoved(" + sbn.getPackageName() + ')');
        super.onNotificationRemoved(sbn);
        mLargeIconMap.remove(sbn.getPackageName());
    }

    protected RemoteControlCompat mController;

    public MediaControllerService() { super(); }

    public static Intent getInterruptionFilterRequestIntent(Context context, final int filter) {
        Intent request = new Intent(ACTION_REQUEST_INTERRUPTION_FILTER);
        request.setComponent(new ComponentName(context, MediaControllerService.class));
        request.setPackage(context.getPackageName());
        request.putExtra(EXTRA_FILTER, filter);
        return request;
    }

    /** Convenience method for sending an {@link android.content.Intent} with {@link #ACTION_REQUEST_INTERRUPTION_FILTER}. */
    public static void requestInterruptionFilter(Context context, final int filter) {
        Intent request = getInterruptionFilterRequestIntent(context, filter);
        context.sendBroadcast(request);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOGI(TAG, "onStartCommand(" + intent.getAction() + ", " + flags + ", " + startId + ')');

        // Handle being told to change the interruption filter (zen mode).
        if (!TextUtils.isEmpty(intent.getAction())) {
            if (ACTION_REQUEST_INTERRUPTION_FILTER.equals(intent.getAction())) {
                if (intent.hasExtra(EXTRA_FILTER)) {
                    final int filter = intent.getIntExtra(EXTRA_FILTER, INTERRUPTION_FILTER_NONE);
                    switch (filter) {
                        case INTERRUPTION_FILTER_NONE:
                        case INTERRUPTION_FILTER_ALL:
                        case INTERRUPTION_FILTER_PRIORITY:
                            LOGI(TAG, "requestInterruptionFilter(" + filter + ')');
                            requestInterruptionFilter(filter);
                            break;
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        LOGI(TAG, "onCreate(pid=" + Process.myPid() +
                ", uid=" + Process.myUid() +
                ", tid=" + Process.myTid() + ")");
        registerController();
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        MainThreadBus.get().register(this);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        LOGD(TAG, "onDestroy()");
        unregisterController();
        MainThreadBus.get().unregister(this);
        super.onDestroy();
    }

    protected void registerController() {
        mController = RemoteControlCompat.get(this, getClass());
        mController.setMediaControlListener(this);
    }

    protected void unregisterController() {
        if (null != mController) {
            mController.release();
            mController.setMediaControlListener(null);
        }
        mController = null;
    }

    @Override
    public void onMetadataChanged(MediaMetadataCompat metadata) {
        LOGI(TAG, "onMetadataChanged()");
        broadcast();
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        LOGI(TAG, "onPlaybackStateChanged()");
        broadcast();
    }

    protected void broadcast() {
        MainThreadBus.get().post(produceEvent());
    }

    // Stupid RemoteController Proxy Shit

    @Override
    public void onClientChange(boolean clearing) {
        ((RemoteController.OnClientUpdateListener) mController).onClientChange(clearing);
    }

    @Override
    public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
        ((RemoteController.OnClientUpdateListener) mController).onClientMetadataUpdate(metadataEditor);
    }

    @Override
    public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs, long currentPosMs, float speed) {
        ((RemoteController.OnClientUpdateListener) mController).onClientPlaybackStateUpdate(state, stateChangeTimeMs, currentPosMs, speed);
    }

    @Override
    public void onClientPlaybackStateUpdate(int state) {
        ((RemoteController.OnClientUpdateListener) mController).onClientPlaybackStateUpdate(state);
    }

    @Override
    public void onClientTransportControlUpdate(int transportControlFlags) {
        LOGI("RemoteControlJellyBeanMR2", "onClientTransportControlUpdate(" + transportControlFlags + ")");
        ((RemoteController.OnClientUpdateListener) mController).onClientTransportControlUpdate(transportControlFlags);
    }

    // Android 5.0 Lollipop

    @Override public void onListenerConnected() {
        LOGI(TAG, "onListenerConnected()");
    }
    @Override public void onListenerHintsChanged(int hints) {
        LOGI(TAG, "onListenerHintsChanged(" + hints + ')');
    }

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        LOGI(TAG, "onInterruptionFilterChanged(" + interruptionFilter + ')');
    }

    // ========== EventBus ==========

    @Produce
    public Pair<MediaMetadataCompat, PlaybackStateCompat> produceEvent() {
        final MediaMetadataCompat metadata;
        if (null == mController.getMetadata()) {
            metadata = (new MediaMetadataCompat.Builder()).build();
        } else {
            // If the notification didn't provide an icon, add one!
            MediaMetadataCompat metadata1 = mController.getMetadata();
            String packageName = metadata1.getString(RemoteControlCompat.METADATA_KEY_PACKAGE);
            if (!metadata1.containsKey(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON) &&
                    mLargeIconMap.containsKey(packageName)) {
                MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder(metadata1);
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, mLargeIconMap.get(packageName));
                metadata1 = builder.build();
            }
            metadata = metadata1;
        }
        return Pair.create(metadata, mController.getPlaybackState());
    }
}