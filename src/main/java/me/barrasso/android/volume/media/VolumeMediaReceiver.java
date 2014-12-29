package me.barrasso.android.volume.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.popup.VolumePanel;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * {@link android.content.BroadcastReceiver} to handle events related to audio/ media.
 * Requires a {@link android.os.Handler} to respond to the messages, and to have it's
 * screen state updated to determine whether to send {@link android.os.Message}s
 * synchronously or not.
 */
public final class VolumeMediaReceiver extends BroadcastReceiver {

    public static final String TAG = LogUtils.makeLogTag(VolumeMediaReceiver.class);

    public static final int MSG_VOLUME_CHANGED = 0;
    public static final int MSG_RINGER_MODE_CHANGED = 1;
    public static final int MSG_MUTE_CHANGED = 2;
    public static final int MSG_SPEAKERPHONE_CHANGED = 3;
    public static final int MSG_VIBRATE_MODE_CHANGED = 4;
    public static final int MSG_MEDIA_BUTTON_EVENT = 5;
    public static final int MSG_VOLUME_LONG_PRESS = 6;
    public static final int MSG_PLAY_STATE_CHANGED = 7;
    public static final int MSG_DISPATCH_KEYEVENT = 8;
    public static final int MSG_CALL_STATE_CHANGED = 9;
    public static final int MSG_AUDIO_ROUTES_CHANGED = 10;
    public static final int MSG_HEADSET_PLUG = 11;
    public static final int MSG_USER_PRESENT = 12;
    public static final int MSG_ALARM_CHANGED = 13;

    public static String getMsgName(final int msg) {
        switch (msg) {
            case MSG_VOLUME_CHANGED:
                return "MSG_VOLUME_CHANGED";
            case MSG_RINGER_MODE_CHANGED:
                return "MSG_RINGER_MODE_CHANGED";
            case MSG_MUTE_CHANGED:
                return "MSG_MUTE_CHANGED";
            case MSG_SPEAKERPHONE_CHANGED:
                return "MSG_SPEAKERPHONE_CHANGED";
            case MSG_VIBRATE_MODE_CHANGED:
                return "MSG_VIBRATE_MODE_CHANGED";
            case MSG_MEDIA_BUTTON_EVENT:
                return "MSG_MEDIA_BUTTON_EVENT";
            case MSG_VOLUME_LONG_PRESS:
                return "MSG_VOLUME_LONG_PRESS";
            case MSG_PLAY_STATE_CHANGED:
                return "MSG_PLAY_STATE_CHANGED";
            case MSG_DISPATCH_KEYEVENT:
                return "MSG_DISPATCH_KEYEVENT";
            case MSG_CALL_STATE_CHANGED:
                return "MSG_CALL_STATE_CHANGED";
            case MSG_AUDIO_ROUTES_CHANGED:
                return "MSG_AUDIO_ROUTES_CHANGED";
            case MSG_HEADSET_PLUG:
                return "MSG_HEADSET_PLUG";
            case MSG_USER_PRESENT:
                return "MSG_USER_PRESENT";
            case MSG_ALARM_CHANGED:
                return "MSG_ALARM_CHANGED";
            default:
                return "MSG_UNKNOWN";
        }
    }

    private boolean mScreen = true;
    private Handler mHandler;

    public VolumeMediaReceiver(Handler handler) {
        mHandler = handler;
    }

    public void setScreen(final boolean screen) { mScreen = screen; }
    public void setHandler(Handler handler) { mHandler = handler; }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (null == intent || TextUtils.isEmpty(intent.getAction())) return;
        final String mAction = intent.getAction();

        if (Intent.ACTION_MEDIA_BUTTON.equals(mAction))
        {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (null == event) return;
            LOGI(TAG, Intent.ACTION_MEDIA_BUTTON + ':' + String.valueOf(event.getKeyCode()));
            sendMessage(Message.obtain(mHandler, MSG_MEDIA_BUTTON_EVENT, event));
        }
        else if (Intent.ACTION_USER_PRESENT.equals(mAction))
        {
            LOGI(TAG, Intent.ACTION_USER_PRESENT);
            sendMessage(Message.obtain(mHandler, MSG_USER_PRESENT));
        }
        else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(mAction))
        {
            int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
            LOGI(TAG, AudioManager.RINGER_MODE_CHANGED_ACTION + ':' + String.valueOf(ringerMode));
            sendMessage(Message.obtain(mHandler, MSG_RINGER_MODE_CHANGED, ringerMode, 0));
        }
        else if (Constants.VOLUME_CHANGED_ACTION.equals(mAction))
        {
            int streamType = intent.getIntExtra(Constants.EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_SYSTEM);
            int streamValue = intent.getIntExtra(Constants.EXTRA_VOLUME_STREAM_VALUE, 0);
            int prevValue = intent.getIntExtra(Constants.EXTRA_PREV_VOLUME_STREAM_VALUE, 0);
            LOGI(TAG, Constants.VOLUME_CHANGED_ACTION + "[type=" + String.valueOf(streamType) +
                    ", vol=" + String.valueOf(streamValue) + ", prev=" + String.valueOf(prevValue) + ']');
            int[] vals = new int[] { streamType, streamValue, prevValue };
            sendMessage(Message.obtain(mHandler, MSG_VOLUME_CHANGED, vals));
        }
        else if (Constants.MASTER_MUTE_CHANGED_ACTION.equals(mAction))
        {
            int streamType = VolumePanel.STREAM_MASTER;
            boolean muted = intent.getBooleanExtra(Constants.EXTRA_MASTER_VOLUME_MUTED, false);
            LOGI(TAG, Constants.MASTER_MUTE_CHANGED_ACTION + "[stream=" + String.valueOf(streamType)
                    + ", muted=" + String.valueOf(muted) + ']');
            sendMessage(Message.obtain(mHandler, MSG_MUTE_CHANGED, streamType, ((muted) ? 1 : 0)));
        }
        else if (Constants.MASTER_VOLUME_CHANGED_ACTION.equals(mAction))
        {
            int streamType = VolumePanel.STREAM_MASTER;
            int prevValue = intent.getIntExtra(Constants.EXTRA_PREV_MASTER_VOLUME_VALUE, 0);
            int streamValue = intent.getIntExtra(Constants.EXTRA_MASTER_VOLUME_VALUE, 0);
            LOGI(TAG, Constants.MASTER_VOLUME_CHANGED_ACTION + "[type=" + String.valueOf(streamType) +
                    ", vol=" + String.valueOf(streamValue) + ", prev=" + String.valueOf(prevValue) + ']');
            int[] vals = new int[] { streamType, streamValue, prevValue };
            sendMessage(Message.obtain(mHandler, MSG_VOLUME_CHANGED, vals));
        }
        else if (Intent.ACTION_HEADSET_PLUG.equals(mAction))
        {
            int state = intent.getIntExtra("state", 0);
            LOGI(TAG, Intent.ACTION_HEADSET_PLUG + "[state=" + String.valueOf(state) + ']');
            sendMessage(Message.obtain(mHandler, MSG_HEADSET_PLUG, state, 0));
        }
        else if (Constants.ACTION_ALARM_CHANGED.endsWith(mAction))
        {
            boolean alarmSet = intent.getBooleanExtra(Constants.ALARM_SET_EXTRA, false);
            LOGI(TAG, Constants.ALARM_SET_EXTRA + "[alarmSet=" + String.valueOf(alarmSet) + ']');
            sendMessage(Message.obtain(mHandler, MSG_ALARM_CHANGED, (alarmSet) ? 1 : 0));
        }
        else
        {
            processMediaIntent(context, intent);
        }
    }

    // Respond to an unknown media Intent (try to extract metadata from its extras)
    private void processMediaIntent(Context context, Intent intent) {
        // Only handle media intents if we don't have a NotificationListenerService active.
        boolean notifRunning = Utils.isMediaControllerRunning(context);
        LOGI(TAG, "processMediaIntent(" + notifRunning + ')');
        if (!notifRunning) {
            Pair<MediaMetadataCompat, PlaybackStateCompat> data = MediaEventResponder.respond(context, intent);
            if (null != data) {
                sendMessage(Message.obtain(mHandler, MSG_PLAY_STATE_CHANGED, data));
            }
        }
    }

    // Handles a Message, even when the screen is off.
    private void sendMessage(Message msg) {
        if (null == msg) return;
        if (mScreen) msg.sendToTarget();
        else         mHandler.handleMessage(msg);
    }
}