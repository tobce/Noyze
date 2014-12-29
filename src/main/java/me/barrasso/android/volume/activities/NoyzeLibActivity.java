package me.barrasso.android.volume.activities;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.mikepenz.aboutlibraries.ui.LibsActivity;

public class NoyzeLibActivity extends LibsActivity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ConfigurationActivity.setupActionBar(this);

        // TRACK: activity view.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.setScreenName(getClass().getSimpleName());
            t.send(new HitBuilders.AppViewBuilder().build());
        }
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
}