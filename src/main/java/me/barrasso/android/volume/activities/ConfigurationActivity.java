package me.barrasso.android.volume.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.backup.BackupManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.squareup.otto.Subscribe;
import com.mikepenz.aboutlibraries.Libs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.keyboardsurfer.android.widget.crouton.Style;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import me.barrasso.android.volume.BuildConfig;
import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.VolumeAccessibilityService;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.media.conditions.RingerNotificationLink;
import me.barrasso.android.volume.popup.StatusBarVolumePanel;
import me.barrasso.android.volume.popup.VolumePanel;
import me.barrasso.android.volume.utils.Constants;
import com.squareup.otto.MainThreadBus;

import me.barrasso.android.volume.utils.DateUtils;
import me.barrasso.android.volume.utils.Utils;

import android.support.v7.widget.Toolbar;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

public class ConfigurationActivity extends PreferenceActivity
        implements CompoundButton.OnCheckedChangeListener, SharedPreferences.OnSharedPreferenceChangeListener
{
    public static final String TAG = LogUtils.makeLogTag(ConfigurationActivity.class);

    private SharedPreferences sp;
    private CompoundButton switchView;
    private Toolbar bar;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        LinearLayout root = (LinearLayout)findViewById(android.R.id.list).getParent().getParent().getParent();
        bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.settings_toolbar, root, false);
        bar.setTitle(getTitle());
        bar.inflateMenu(R.menu.toggle_switch);
        final MenuItem toggle = bar.getMenu().findItem(R.id.action_switch);
        if (null != toggle) {
            switchView = (CompoundButton) toggle.getActionView().findViewById(R.id.switch_view);
            if (null != switchView) {
                switchView.setChecked(VolumeAccessibilityService.isEnabled(this));
                switchView.setOnCheckedChangeListener(this);
            }
        }
        if (isTaskRoot()) bar.setNavigationIcon(null);
        root.addView(bar, 0);
        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    public void setToolbarTitle(String title) {
        bar.setTitle(title);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar(this);
        MainThreadBus.get().register(this);
        ChangeLogDialog.show(this, true);

        // TRACK: activity view.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.setScreenName(getClass().getSimpleName());
            t.send(new HitBuilders.AppViewBuilder().build());
        }
    }

    @Override
    protected void onRestart() {
        LOGI(TAG, "onRestart()");
        super.onRestart();
        updateSwitchState();
    }

    @Override protected void onStart() {
        LOGI(TAG, "onStart()");
        super.onStart();
        updateSwitchState();
    }

    @Override protected void onResume() {
        LOGI(TAG, "onResume()");
        super.onResume();
        // Accountant.getInstance(this);
        updateSwitchState();
    }

    @Override protected void onPause() {
        LOGI(TAG, "onPause()");
        super.onPause();
        if (null != sp)
            sp.unregisterOnSharedPreferenceChangeListener(this);
        sp = null;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onVolumePanelChangeEvent(VolumeAccessibilityService.VolumePanelChangeEvent event) {
        LOGI(TAG, "onVolumePanelChangeEvent(" + event.getName() + ')');
        boolean rControllerEnabled = Utils.isMediaControllerEnabled(this);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2 &&
            event.supportsMediaPlayback() && !isFinishing() &&
            !rControllerEnabled) {
            NotificationFragment fragment = NotificationFragment.getInstance(false);
            if (null != fragment && null == getFragmentManager().findFragmentByTag(NotificationFragment.class.getSimpleName())) {
                fragment.show(getFragmentManager(), NotificationFragment.class.getSimpleName());
            }
        }

        // TRACK: what theme is being used.
        if (NoyzeApp.GOOGLE_ANALYTICS) {
            Tracker t = ((NoyzeApp) getApplication()).getTracker(
                    NoyzeApp.TrackerName.APP_TRACKER);
            t.send(new HitBuilders.EventBuilder()
                    .setCategory("Setting")
                    .setAction("Change")
                    .setLabel(event.getName())
                    .build());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            MainThreadBus.get().unregister(this);
        } catch (Throwable t) {
            LOGE(TAG, "Error unregistering Otto bus.", t);
        }
        invokeFragmentManagerNoteStateNotSaved();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void invokeFragmentManagerNoteStateNotSaved() {
        // For post-Honeycomb devices
        if (Build.VERSION.SDK_INT < 11) {
            return;
        }
        try {
            Class<?> cls = getClass();
            do {
                cls = cls.getSuperclass();
            } while (!"Activity".equals(cls.getSimpleName()));
            Field fragmentMgrField = cls.getDeclaredField("mFragments");
            fragmentMgrField.setAccessible(true);

            Object fragmentMgr = fragmentMgrField.get(this);
            cls = fragmentMgr.getClass();

            Method noteStateNotSavedMethod = cls.getDeclaredMethod("noteStateNotSaved", new Class[] {});
            noteStateNotSavedMethod.invoke(fragmentMgr);
        } catch (Exception ex) {
            LOGE(TAG, "Error on FM.noteStateNotSaved", ex);
        }
    }

    protected void updateSwitchState() {
        if (null == sp) {
            sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sp.registerOnSharedPreferenceChangeListener(this);
        }

        if (null != switchView) {
            switchView.setOnCheckedChangeListener(null);
            switchView.setChecked(VolumeAccessibilityService.isEnabled(this));
            switchView.setOnCheckedChangeListener(this);
        }
    }

    // Supported mainly to inform BackupManager of a change in settings.
    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        LOGI(TAG, "onSharedPreferenceChanged(" + key + ')');
        Context context = getApplicationContext();
        if (null != context) {
            (new BackupManager(context)).dataChanged();
        }

        if (Constants.PREF_VOLUME_PANEL.equals(key)) {
            // TRACK: what theme is being used.
            String panel = sharedPreferences.getString(key, StatusBarVolumePanel.TAG);
            VolumeAccessibilityService.VolumePanelChangeEvent event =
                    new VolumeAccessibilityService.VolumePanelChangeEvent(panel, Utils.supportsMediaPlayback(panel));
            onVolumePanelChangeEvent(event);
        } else if (Constants.PREF_REPORTING.equals(key)) {
            // TRACK: when the user opts out of being tracked.
        }
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        super.onBuildHeaders(target);
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        super.onHeaderClick(header, position);
        // NOTE: get IAP service ready BEFORE we open the setting.
        // Accountant.getInstance(this).connect();
        LOGI(TAG, "onHeaderClick(" + header.id + ")");
        switch ((int) header.id) {
            case R.id.header_about:
                launchAbout(findViewById((int) header.id));
                break;
            case R.id.header_rate:
                Utils.launchMarketActivity(getApplicationContext());
                break;
            case R.id.header_google_plus:
                launchGooglePlus();
                break;
        }
    }

    private void launchGooglePlus() {
        Intent google = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.google_plus_url)));
        if (google.resolveActivity(getPackageManager()) != null) {
            startActivity(google);
        } else {
            Crouton.showText(this, R.string.url_error, Style.ALERT);
        }
    }

    private void launchAbout(View view) {
        Intent about = new Intent(getApplicationContext(), NoyzeLibActivity.class);
        about.putExtra(Libs.BUNDLE_FIELDS, Libs.toStringArray(R.string.class.getFields()));
        about.putExtra(Libs.BUNDLE_VERSION, true);
        about.putExtra(Libs.BUNDLE_LICENSE, true);
        about.putExtra(Libs.BUNDLE_TITLE, getString(R.string.about_settings));
        about.putExtra(Libs.BUNDLE_THEME, R.style.AboutTheme);
        if (null == view) {
            startActivity(about);
        } else {
            ActivityOptionsCompat anim = ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, "Google Plus");
            ActivityCompat.startActivity(this, about, anim.toBundle());
        }
    }

    private static final Set<String> VALID_FRAGMENTS = new HashSet<String>();
    static {
        VALID_FRAGMENTS.add(InterfaceSettings.class.getName());
        VALID_FRAGMENTS.add(MediaSettings.class.getName());
        VALID_FRAGMENTS.add(LabSettings.class.getName());
        VALID_FRAGMENTS.add(AdvancedSettings.class.getName());
    }

    @Override
    public boolean isValidFragment(String name) {
        LOGD(TAG, "isValidFragment(" + name + ")");
        return VALID_FRAGMENTS.contains(name);
    }

    public static class InterfaceSettings extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        String mVolumePanel = "";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Accountant.getInstance(getActivity());
            LOGI(getClass().getSimpleName(), "onCreate()");
            getActivity().setTitle(getString(R.string.interface_settings));
            addPreferencesFromResource(R.xml.interface_preferences);

            // Set whether the preference should be checked or not.
            mVolumePanel = VolumePanel.class.getSimpleName();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.registerOnSharedPreferenceChangeListener(this);
            updateFeatureList(null);
        }

        @Override
        public void onStart() {
            LOGI(getClass().getSimpleName(), "onStart()");
            super.onStart();
        }

        @Override
        public void onDestroy() {
            LOGI("InterfaceSettings", "onDestroy()");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        protected void updateFeatureList(String name) {
            LOGI("InterfaceSettings", "updateFeatureList()");
            if (TextUtils.isEmpty(name)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                name = prefs.getString(mVolumePanel, name);
            }
            List<String> features = VolumePanelInfo.getSupportedFeatures(name);
            String[] allFeatures = VolumePanelInfo.getAllFeatures();
            for (String feature : allFeatures) {
                Preference pref = findPreference(feature);
                pref.setEnabled(features.contains(feature));
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            LOGI("InterfaceSettings", "onSharedPreferenceChanged(" + key + ")");
            if (mVolumePanel.equals(key)) {
                updateFeatureList(null);
            }
        }
    }

    public static class MediaSettings extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Accountant.getInstance(getActivity());
            getActivity().setTitle(getString(R.string.media_settings));
            addPreferencesFromResource(R.xml.media_preferences);

            // Check whether to show the notification-ringer volume link setting.
            AudioManager manager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            RingerNotificationLink linkChecker = new RingerNotificationLink();
            boolean isNotifRingLinked = linkChecker.apply(manager);
            Preference link = findPreference("VolumePanel_linkNotifRinger");
            link.setEnabled(!isNotifRingLinked);
            if (isNotifRingLinked) {
                PreferenceCategory category = (PreferenceCategory) findPreference("AudioMedia");
                category.removePreference(link);
            }
        }

        @Override
        public void onStart() {
            LOGI(getClass().getSimpleName(), "onStart()");
            super.onStart();
        }
    }

    public static class LabSettings extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener {

        TwoStatePreference notifPref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Accountant.getInstance(getActivity());
            getActivity().setTitle(getString(R.string.labs_title));
            addPreferencesFromResource(R.xml.lab_preferences);

            // Set whether the preference should be checked or not.
            Preference pref = findPreference("MediaControllerService");

            // For builds other than KitKat, hide RemoteController API.
            if (null != pref) pref.setOnPreferenceChangeListener(this);

            // Add out listeners and state change stuff.
            if (pref instanceof TwoStatePreference) {
                notifPref = (TwoStatePreference) pref;
                updateNotifPref();
            }
        }

        protected void updateNotifPref() {
            if (null != notifPref) notifPref.setChecked(Utils.isMediaControllerEnabled(getActivity()));
        }

        @Override public void onStart() {
            LOGI(getClass().getSimpleName(), "onStart()");
            super.onStart();
            updateNotifPref();
        }

        @Override public void onResume() {
            LOGI("LabSettings", "onResume()");
            super.onStart();
            updateNotifPref();
        }

        @Override
        public boolean onPreferenceChange(Preference pref, Object newValue) {
            LOGD(TAG, "onPreferenceChange(" + newValue.toString() + ")");
            boolean isNotif = ("MediaControllerService".equals(pref.getKey()));
            if (isNotif && !getActivity().isFinishing()) {
                NotificationFragment fragment = NotificationFragment.getInstance(Utils.isMediaControllerEnabled(getActivity()));
                if (null != fragment && null == getFragmentManager().findFragmentByTag(NotificationFragment.class.getSimpleName())) {
                    fragment.show(getFragmentManager(), NotificationFragment.class.getSimpleName());
                }
                return false;
            }
            return true;
        }
    }

    public static class AdvancedSettings extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Accountant.getInstance(getActivity());
            getActivity().setTitle(getString(R.string.advanced_title));
            addPreferencesFromResource(R.xml.advanced_settings);

            // If available, place our build number in the version Preference.
            Preference version = findPreference("version");
            if (null != version)
                version.setSummary(Utils.lastBuildTimestamp(getActivity()));
        }

        @Override
        public void onStart() {
            LOGI(getClass().getSimpleName(), "onStart()");
            super.onStart();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference pref) {
            LOGI(TAG, "onPreferenceTreeClick(" + pref.getKey() + ")");
            if (Constants.PREF_DEBUG_LOG.equals(pref.getKey())) {
                LogUtils.sendDebugLogAsync(getActivity()); // DEBUG
                // LogUtils.sendDebugLog(getActivity()); // DEBUG
                return true;
            } else if (Constants.PREF_CHANGELOG.equals(pref.getKey())) {
                ChangeLogDialog.show(getActivity(), false);
            }
            return super.onPreferenceTreeClick(screen, pref);
        }
    }

    public static void setupActionBar(Activity activity) {

        // Tint the status bar, if available.
        SystemBarTintManager tintManager = new SystemBarTintManager(activity);
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setTintColor(activity.getResources().getColor(R.color.action_bar_dark));

        ActionBar actionBar = activity.getActionBar();
        if (null != actionBar) {
            actionBar.setIcon(DateUtils.AppIcon());
            actionBar.setDisplayHomeAsUpEnabled(!activity.isTaskRoot());
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // R.menu.configure_overflow
        getMenuInflater().inflate(R.menu.toggle_switch, menu);

        // Get the action view used in your switch item
        final MenuItem toggle = menu.findItem(R.id.action_switch);
        if (null != toggle) {
            switchView = (CompoundButton) toggle.getActionView().findViewById(R.id.switch_view);
            if (null != switchView) {
                switchView.setChecked(VolumeAccessibilityService.isEnabled(this));
                switchView.setOnCheckedChangeListener(this);
            }
        }

        return super.onCreateOptionsMenu(menu);
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

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        LOGI(TAG, "Master switch: " + String.valueOf(isChecked));
        boolean isRunning = VolumeAccessibilityService.isEnabled(this);
        switchView.setChecked(!isChecked);
        if (isFinishing()) return;
        AccessibilityFragment fragment = AccessibilityFragment.getInstance(isRunning);
        if (null != fragment && null == getFragmentManager().findFragmentByTag(AccessibilityFragment.class.getSimpleName())) {
            fragment.show(getFragmentManager(), AccessibilityFragment.class.getSimpleName());
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!BuildConfig.DEBUG) {
            MenuItem sendLogsItem = menu.findItem(R.id.action_send_logs);
            if (sendLogsItem != null) {
                sendLogsItem.setVisible(false);
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {
        LOGI(TAG, "onDestroy()");
        Crouton.cancelAllCroutons();
        try {
            MainThreadBus.get().unregister(this);
        } catch (Throwable t) {
            LOGE(TAG, "Unable to unregister from Otto.", t);
        }
        super.onDestroy();
    }

    /** {@link android.app.Dialog} to inform the user about {@link android.accessibilityservice.AccessibilityService}. */
    public static class AccessibilityFragment extends DialogFragment {

        static boolean isShowing = false;

        public static AccessibilityFragment getInstance(boolean isRunning) {
            if (isShowing) return null;
            AccessibilityFragment fragment = new AccessibilityFragment();
            Bundle args = ((fragment.getArguments() == null) ? new Bundle() : fragment.getArguments());
            args.putBoolean("active", isRunning);
            fragment.setArguments(args);
            isShowing = true;
            return fragment;
        }

        private boolean active;
        public AccessibilityFragment() {
            if (null != getArguments()) {
                active = getArguments().getBoolean("active");
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            isShowing = false;
        }

        @Override
        public void dismiss() {
            super.dismiss();
            isShowing = false;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            return builder.setTitle(R.string.accessibility_dialog_title)
                          .setIcon(R.drawable.ic_settings_accessibility_black)
                          .setMessage(((active) ? R.string.accessibility_dialog_description_on :
                                                  R.string.accessibility_dialog_description))
                          .setNegativeButton(android.R.string.cancel, null)
                          .setPositiveButton(R.string.take_me, new DialogInterface.OnClickListener() {
                              @Override
                              public void onClick(DialogInterface dialog, int which) {
                                  if (which == Dialog.BUTTON_POSITIVE) {
                                      dismiss();
                                      dialog.dismiss();
                                      openAccessibility();
                                  }
                              }
                          }).create();
        }

        private void openAccessibility() {
            Intent accessibility = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            try {
                startActivity(accessibility);
            } catch (ActivityNotFoundException e) {
                LOGE(TAG, "Error opening Accessibility.", e);
                Crouton.showText(getActivity(), R.string.accessibility_error, Style.ALERT);
            }
        }
    }

    /** {@link android.app.Dialog} to inform the user about {@link android.service.notification.NotificationListenerService}. */
    public static class NotificationFragment extends DialogFragment {

        static boolean isShowing = false;

        public static NotificationFragment getInstance(boolean isRunning) {
            if (isShowing) return null;
            NotificationFragment fragment = new NotificationFragment();
            Bundle args = ((fragment.getArguments() == null) ? new Bundle() : fragment.getArguments());
            args.putBoolean("active", isRunning);
            fragment.setArguments(args);
            isShowing = true;
            return fragment;
        }

        private boolean active;
        public NotificationFragment() {
            if (null != getArguments()) {
                active = getArguments().getBoolean("active");
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            isShowing = false;
        }

        @Override
        public void dismiss() {
            super.dismiss();
            isShowing = false;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            return builder.setTitle(R.string.notification_dialog_title)
                    .setMessage(((active) ? R.string.notification_dialog_description_on :
                            R.string.notification_dialog_description))
                    .setIcon(R.drawable.ic_audio_notification_pm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.take_me, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == Dialog.BUTTON_POSITIVE) {
                                dismiss();
                                dialog.dismiss();
                                openNotification();
                            }
                        }
                    }).create();
        }

        private void openNotification() {
            Intent notifications = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            try {
                startActivity(notifications);
            } catch (ActivityNotFoundException e) {
                LOGE(TAG, "Error opening NotificationListener.", e);
                Crouton.showText(getActivity(), R.string.accessibility_error, Style.ALERT);
            }
        }
    }
}