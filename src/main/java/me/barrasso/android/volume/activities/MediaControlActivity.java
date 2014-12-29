package me.barrasso.android.volume.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGD;

/**
 * Activity meant to control media playback, opened via a shortcut.
 */
public class MediaControlActivity extends Activity {

    public static final String TAG = LogUtils.makeLogTag(MediaControlActivity.class);

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        LOGD(TAG, "onCreate()");
        onHandleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        LOGD(TAG, "onNewIntent()");
        onHandleIntent(intent);
    }

    public void onHandleIntent(Intent intent) {
        if (null == intent) return;
        LOGD(TAG, "onHandleIntent(" + intent.toString() + ")");

        // TRACK: activity view.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.setScreenName(getClass().getSimpleName());
            t.send(new HitBuilders.AppViewBuilder().build());
        }

        Bundle extras = intent.getExtras();

        if (null == extras || !extras.containsKey(Intent.EXTRA_KEY_EVENT)) {
            Log.e(TAG, "KeyEvent null, cannot dispatch media event.");
            finish();
            return;
        }

        final int keyCode = extras.getInt(Intent.EXTRA_KEY_EVENT);
        if (!Utils.isMediaKeyCode(keyCode)) {
            Log.e(TAG, "KeyEvent was not one of KEYCODE_MEDIA_* events.");
            finish();
            return;
        }

        LOGD(TAG, "Dispatching media event: " + keyCode);
        AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioHelper helper = AudioHelper.getHelper(getApplicationContext(), manager);
        helper.dispatchMediaKeyEvent(getApplicationContext(), keyCode);

        finish();
    }
}