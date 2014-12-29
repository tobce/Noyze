package me.barrasso.android.volume.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.barrasso.android.volume.LogUtils;

/**
 * {@link android.content.BroadcastReceiver} used to monitor package change events and cache a map
 * of apps that respond to given intents. Used to improve performance when looking up app information
 * by package name, but with a trivial memory overhead.
 */
public class AppTypeMonitor extends BroadcastReceiver {

    private final String[] mIntentActions;
    private final Map<String, boolean[]> mPackageResponse;

    public AppTypeMonitor(String... intentAction) {
        if (intentAction == null || intentAction.length <= 0)
            throw new IllegalArgumentException("AppTypeMonitor must be created with at least one Intent action.");
        mIntentActions = intentAction;
        mPackageResponse = new HashMap<String, boolean[]>();
    }

    private static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        return filter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        buildPackageList(context);
    }

    protected static String getPackageName(ResolveInfo info) {
        String packageName = info.resolvePackageName;
        if (TextUtils.isEmpty(packageName) && null != info.activityInfo)
            packageName = info.activityInfo.packageName;
        return packageName;
    }

    /** Pre-builds our list of packages that respond to given intent actions.  */
    protected void buildPackageList(Context context) {
        PackageManager pm = context.getPackageManager();
        mPackageResponse.clear();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> packages = pm.queryIntentActivities(mainIntent, 0);
        LogUtils.LOGD("AppTypeMonitor", "appPackages=" + packages.size());
        for (ResolveInfo packageInfo : packages) {
            String packageName = getPackageName(packageInfo);
            if (TextUtils.isEmpty(packageName)) continue;
            mPackageResponse.put(packageName, new boolean[mIntentActions.length]);
        }

        // Check all apps that respond to each action.
        int actionIndex = 0;
        for (String action : mIntentActions) {
            Intent intent = new Intent(action);
            List<ResolveInfo> apps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (apps == null || apps.size() == 0) {
                continue;
            }
            // Go through all apps that respond to this intent and set a flag.
            for (ResolveInfo app : apps) {
                String packageName = getPackageName(app);
                if (TextUtils.isEmpty(packageName)) continue;
                boolean[] responses = mPackageResponse.get(packageName);
                if (null == responses) responses = new boolean[mIntentActions.length];
                responses[actionIndex] = true;
                mPackageResponse.put(packageName, responses);
            }
            ++actionIndex;
        }
    }

    protected boolean registered;

    /** Register this app monitor. MUST be called when monitoring should begin. */
    public void register(Context context) {
        context.registerReceiver(this, getIntentFilter());
        buildPackageList(context);
        registered = true;
    }

    /** Unregister this app monitor. MUST be called when the object is no longer needed. */
    public void unregister(Context context) {
        if (registered) {
            context.unregisterReceiver(this);
            registered = false;
        }
    }

    /** @return True if a given package name responds to all of the provided actions. */
    public boolean doesPackageRespondToAny(String packageName) {
        return doesPackageRespondToAny(packageName, mIntentActions);
    }

    /** @return True if a given package name responds to all of the provided actions. */
    public boolean doesPackageRespondTo(String packageName) {
        return doesPackageRespondTo(packageName, mIntentActions);
    }

    public static int index(String[] set, String target) {
        if (null == set || set.length == 0 || TextUtils.isEmpty(target)) return -1;
        int index = 0;
        for (String subject : set) {
            if (target.equals(subject)) {
                return index;
            }
            ++index;
        }
        return -1;
    }

    /** @return True if a given package name responds to any of the given actions. */
    public boolean doesPackageRespondToAny(String packageName, String... actions) {
        boolean[] response = mPackageResponse.get(packageName);
        if (null == response || response.length != mIntentActions.length) return false;
        for (String action : actions) {
            // Make sure we've been monitoring the action and that the package responds to it.
            int index = index(mIntentActions, action);
            if (index < 0 || index >= response.length) return false;
            if (response[index]) return true;
        }
        return false;
    }

    /** @return True if a given package name responds to all of the given actions. */
    public boolean doesPackageRespondTo(String packageName, String... actions) {
        boolean[] response = mPackageResponse.get(packageName);
        if (null == response || response.length != mIntentActions.length) return false;
        for (String action : actions) {
            // Make sure we've been monitoring the action and that the package responds to it.
            int index = index(mIntentActions, action);
            if (index < 0 || index >= response.length || !response[index]) return false;
        }
        return true;
    }

    /** @return True if a given package name responds to a monitored intent action. */
    public boolean doesPackageRespondTo(String packageName, String action) {
        boolean[] response = mPackageResponse.get(packageName);
        if (null == response || response.length != mIntentActions.length) return false;
        int index = Arrays.binarySearch(mIntentActions, action);
        if (index < 0 || index >= response.length || !response[index]) return false;
        return true;
    }

    @Override
    public String toString() {
        String base = getClass().getSimpleName() + "@{";
        for (Map.Entry<String, boolean[]> app : mPackageResponse.entrySet()) {
            base += app.getKey() + " = " + Arrays.toString(app.getValue()) + ", ";
        }
        return base + "}";
    }
}