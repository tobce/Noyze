package me.barrasso.android.volume.activities;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.levelup.logutils.FLog;
import com.levelup.logutils.FileLogger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.utils.ReflectionUtils;

@SuppressWarnings("unused")
public class NoyzeApp extends Application
        implements Application.ActivityLifecycleCallbacks {

    public static final boolean GOOGLE_ANALYTICS = false;

    /**
     * Enum used to identify the tracker that needs to be used for tracking.
     *
     * A single tracker is usually enough for most purposes. In case you do need multiple trackers,
     * storing them all in Application object helps ensure that they are created only once per
     * application instance.
     */
    public static enum TrackerName {
        APP_TRACKER, // Tracker used only in this app.
        GLOBAL_TRACKER, // Tracker used by all the apps from a company. eg: roll-up tracking.
        ECOMMERCE_TRACKER, // Tracker used by all ecommerce transactions from a company.
    }

    final Map<TrackerName, Tracker> mTrackers = new HashMap<TrackerName, Tracker>();

    private static String getTrimName(final int level) {
        switch (level) {
            case TRIM_MEMORY_COMPLETE:
                return "TRIM_MEMORY_COMPLETE";
            case TRIM_MEMORY_RUNNING_MODERATE:
                return "TRIM_MEMORY_RUNNING_MODERATE";
            case TRIM_MEMORY_RUNNING_LOW:
                return "TRIM_MEMORY_RUNNING_LOW";
            case TRIM_MEMORY_RUNNING_CRITICAL:
                return "TRIM_MEMORY_RUNNING_CRITICAL";
            case TRIM_MEMORY_BACKGROUND:
                return "TRIM_MEMORY_BACKGROUND";
            case TRIM_MEMORY_MODERATE:
                return "TRIM_MEMORY_MODERATE";
            case TRIM_MEMORY_UI_HIDDEN:
                return "TRIM_MEMORY_UI_HIDDEN";
            default:
                return String.valueOf(level);
        }
    }

    public static final String FLID = "C8MP6KDPZK8PHWY7GFWY";

    synchronized Tracker getTracker(TrackerName trackerId) {
        if (!mTrackers.containsKey(trackerId)) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            Tracker t = (trackerId == TrackerName.APP_TRACKER) ? analytics.newTracker("UA-41252760-2")
                    : (trackerId == TrackerName.GLOBAL_TRACKER) ? analytics.newTracker(R.xml.global_tracker)
                    : analytics.newTracker(R.xml.global_tracker);
            t.enableAdvertisingIdCollection(false);
            t.setAnonymizeIp(true);
            t.setUseSecure(true);
            mTrackers.put(trackerId, t);

        }
        return mTrackers.get(trackerId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Create initial file logger for use later.
            FileLogger logger = new FileLogger(getCacheDir(), "NoyzeApp");
            FLog.enableAndroidLogging(false);
            FLog.setFileLogger(logger);
        } catch (IOException ioe) {
            LogUtils.LOGE("FileLogger", "Could not create FileLogger.", ioe);
        }

        LogUtils.LOGI("NoyzeApp", "onCreate()");
        registerActivityLifecycleCallbacks(this);
        // Crashlytics.getInstance().setDebugMode(true);
        // Crashlytics.start(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        LogUtils.LOGI("NoyzeApp", "onTrimMemory(" + getTrimName(level) + ')');
    }

    @Override
    public void onTerminate() {
        LogUtils.LOGI("NoyzeApp", "onTerminate()");
        // unregisterActivityLifecycleCallbacks(this);
        super.onTerminate();
    }

    public boolean canReportAnonymously() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(Constants.PREF_REPORTING, true);
    }

    // Not needed
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        LogUtils.LOGI("NoyzeApp", "onActivityCreated(" + activity.getClass().getSimpleName() + ')');
    }

    @Override public void onActivityDestroyed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    private final AtomicInteger mActivityCount = new AtomicInteger(0);

    @Override
    public void onActivityStarted(Activity activity) {
        int activities = mActivityCount.incrementAndGet();
        LogUtils.LOGI("NoyzeApp", "onActivityStarted(" + activity.getClass().getSimpleName() +
                "), activities=" + activities);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        int activities = mActivityCount.decrementAndGet();
        if (activities == 0) {
            onLastActivityStopped();
        } else if (activities < 0) {
            activities = 0;
            mActivityCount.set(0); // Weird...
        }
        LogUtils.LOGI("NoyzeApp", "onActivityStopped(" + activity.getClass().getSimpleName() +
                "), activities=" + activities);

    }

    protected void onLastActivityStopped() {
        LogUtils.LOGI("NoyzeApp", "onLastActivityStopped()");
        mTrackers.clear();
        // Accountant.destroyInstance();
    }

    /** Proxy to {@link android.view.WindowManagerGlobal#closeAll(IBinder, String, String)} */
    public boolean closeAll() {
        Object wManagerGlobal = ReflectionUtils.getWindowManagerGlobal();
        if (null == wManagerGlobal) return false;
        try {
            // NOTE: All parameters must be null because we don't know the Binder
            // used, and we don't want to create another SessionLeak crash.
            Method mCloseAll = wManagerGlobal.getClass().getDeclaredMethod(
                    "closeAll", IBinder.class, String.class, String.class);
            if (null == mCloseAll) return false;
            mCloseAll.setAccessible(true);
            mCloseAll.invoke(wManagerGlobal, null, null, null);
        } catch (Throwable t) {
            LogUtils.LOGE("NoyzeApp", "Failed to execute WindowManagerGlobal#closeAll()", t);
            return false;
        }
        return true;
    }
}