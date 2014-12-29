package me.barrasso.android.volume.activities;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;

/**
 * Simple {@link android.app.Activity} to display a list of options for
 * media playback control. Options are presented and when one is clicked,
 * the activity is finished and the shortcut created.
 */
public class MediaShortcutActivity extends ListActivity {

    public static final String TAG = LogUtils.makeLogTag(MediaShortcutActivity.class);

    private int[] MEDIA_KEYCODES;
    private String[] MEDIA_KEYNAMES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // We've got one mission and one mission only!
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            finish();
            return;
        }

        // TRACK: activity view.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.setScreenName(getClass().getSimpleName());
            t.send(new HitBuilders.AppViewBuilder().build());
        }

        ConfigurationActivity.setupActionBar(this);
        setContentView(R.layout.media_shortcut);

        MEDIA_KEYCODES = getResources().getIntArray(R.array.media_control_keycodes);
        MEDIA_KEYNAMES = getResources().getStringArray(R.array.media_controls);
        assert(MEDIA_KEYCODES.length == MEDIA_KEYNAMES.length);

        ControlAdapter adapter = new ControlAdapter(getApplicationContext(),
                android.R.layout.simple_selectable_list_item, MEDIA_KEYNAMES);
        setListAdapter(adapter);
    }

    protected class ControlAdapter extends ArrayAdapter<String> {
        public ControlAdapter(Context context, int layout, String[] strs) {
            super(context, layout, strs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            text.setTextColor(Color.BLACK);
            text.setCompoundDrawablesWithIntrinsicBounds(
                    getResourceForKeyCode(MEDIA_KEYCODES[position]), 0, 0, 0);
            text.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.list_fading_edge_length));
            return view;
        }
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        if (position >= MEDIA_KEYCODES.length || position < 0) return;
        LOGD(TAG, "onListItemClick(position=" + position + ", keyCode=" + MEDIA_KEYCODES[position] + ")");
        setupShortcut(MEDIA_KEYCODES[position], MEDIA_KEYNAMES[position]);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int getResourceForKeyCode(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return R.drawable.ic_media_stop;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return R.drawable.ic_media_play2;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return R.drawable.ic_media_ff;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return R.drawable.ic_media_rew;
        }

        return 0;
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
    private void setupShortcut(final int keyCode, final String name) {
        LOGD(TAG, "setupShortcut(" + keyCode + ")");
        if (!Utils.isMediaKeyCode(keyCode)) {
            LOGE(TAG, "Cannot create shortcut with invalid keycode (" + keyCode + ")");
            return;
        }

        // First, set up the shortcut intent.  For this example, we simply create an intent that
        // will bring us directly back to this activity.  A more typical implementation would use a
        // data Uri in order to display a more specific result, or a custom action in order to
        // launch a specific operation.

        // NOTE: Only contain primitive extras or the Intent might not be saved as a Uri!
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(getApplicationContext(), MediaControlActivity.class);
        shortcutIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyCode);

        // Then, set up the container intent (the response to the caller)

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext(this, getResourceForKeyCode(keyCode));
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        // Now, return the result to the launcher

        setResult(RESULT_OK, intent);
        finish();
    }
}