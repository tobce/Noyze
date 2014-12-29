package me.barrasso.android.volume.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.VolumeAccessibilityService;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;

/**
 * Activity meant to control media playback, opened via a shortcut.
 */
public class PanelShortcutActivity extends Activity {

    public static final String TAG = LogUtils.makeLogTag(PanelShortcutActivity.class);

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
        String action = intent.getAction();

        // TRACK: activity view.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.setScreenName(getClass().getSimpleName());
            t.send(new HitBuilders.AppViewBuilder().build());
        }

        // We've got one mission and one mission only!
        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            setupShortcut();
            return;
        }

        // If Noyze is active, open the panel.
        if (VolumeAccessibilityService.isEnabled(this)) {
            Intent openPanel = new Intent(getApplicationContext(), VolumeAccessibilityService.class);
            openPanel.putExtra("show", true);
            openPanel.setPackage(getPackageName());
            startService(openPanel);
        } else {
            // If it's not, bring up the app.
            Intent openApp = getPackageManager().getLaunchIntentForPackage(getPackageName());
            openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(openApp);
        }

        finish();
    }

    /**
     * This function creates a shortcut and returns it to the caller.  There are actually two
     * intents that you will send back.
     *
     * The first intent serves as a container for the shortcut and is returned to the launcher by
     * setResult().  This intent must contain three fields:
     *
     * <ul>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_INTENT} The shortcut intent.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_NAME} The text that will be displayed with
     * the shortcut.</li>
     * <li>{@link android.content.Intent#EXTRA_SHORTCUT_ICON} The shortcut's icon, if provided as a
     * bitmap, <i>or</i> {@link android.content.Intent#EXTRA_SHORTCUT_ICON_RESOURCE} if provided as
     * a drawable resource.</li>
     * </ul>
     *
     * If you use a simple drawable resource, note that you must wrapper it using
     * {@link android.content.Intent.ShortcutIconResource}, as shown below.  This is required so
     * that the launcher can access resources that are stored in your application's .apk file.  If
     * you return a bitmap, such as a thumbnail, you can simply put the bitmap into the extras
     * bundle using {@link android.content.Intent#EXTRA_SHORTCUT_ICON}.
     *
     * The shortcut intent can be any intent that you wish the launcher to send, when the user
     * clicks on the shortcut.  Typically this will be {@link android.content.Intent#ACTION_VIEW}
     * with an appropriate Uri for your content, but any Intent will work here as long as it
     * triggers the desired action within your Activity.
     */
    private void setupShortcut() {
        LOGD(TAG, "setupShortcut()");

        // First, set up the shortcut intent.  For this example, we simply create an intent that
        // will bring us directly back to this activity.  A more typical implementation would use a
        // data Uri in order to display a more specific result, or a custom action in order to
        // launch a specific operation.

        // NOTE: Only contain primitive extras or the Intent might not be saved as a Uri!
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(getApplicationContext(), getClass());
        shortcutIntent.putExtra("show", true);

        Intent.ShortcutIconResource icon =
                Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

        // Then, set up the container intent (the response to the caller)
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getTitle());
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

        // Now, return the result to the launcher
        setResult(RESULT_OK, intent);
        finish();
    }
}