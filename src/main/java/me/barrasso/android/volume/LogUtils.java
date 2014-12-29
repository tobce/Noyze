/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.barrasso.android.volume;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.WindowManager;

// import com.crashlytics.android.Crashlytics;

import com.levelup.logutils.FLog;
import com.levelup.logutils.LogCollecting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import me.barrasso.android.volume.media.conditions.RingerNotificationLink;
import me.barrasso.android.volume.media.conditions.SystemVolume;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.utils.Utils;
import me.barrasso.android.volume.utils.VolumeManager;

/**
 * Helper methods that make logging more consistent throughout the app.
 */
public class LogUtils {

    private static final String TAG = makeLogTag(LogUtils.class);

    private static final boolean SILENT = true; //!Accountant.FREE_FOR_ALL;

	private static final String SUPPORT_EMAIL = "";
    private static final boolean CRASHLYTICS = false;
    private static final boolean FILE_LOGGER = true;
	
    private static final String LOG_PREFIX = "volume_";
    private static final int LOG_PREFIX_LENGTH = LOG_PREFIX.length();
    private static final int MAX_LOG_TAG_LENGTH = 23;

    private static Set<String> ERRORS = new HashSet<String>();

    private LogUtils() {
    }

    public static String makeLogTag(String str) {
        if (str.length() > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1);
        }

        return LOG_PREFIX + str;
    }

    /**
     * WARNING: Don't use this when obfuscating class names with Proguard!
     */
    public static String makeLogTag(Class cls) {
        return makeLogTag(cls.getSimpleName());
    }

    public static void LOGD(final String tag, String message) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (!SILENT && (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG))) {
            Log.d(tag, message);
        }
        if (FILE_LOGGER) FLog.d(tag, message);
    }

    public static void LOGD(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (!SILENT && (BuildConfig.DEBUG || Log.isLoggable(tag, Log.DEBUG))) {
            Log.d(tag, message, cause);
        }
        if (FILE_LOGGER) FLog.d(tag, message, cause);
    }

    public static void LOGV(final String tag, String message) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (!SILENT && (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE))) {
            Log.v(tag, message);
        }
        if (FILE_LOGGER) FLog.v(tag, message);
    }

    public static void LOGV(final String tag, String message, Throwable cause) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (!SILENT && (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE))) {
            Log.v(tag, message, cause);
        }
        if (FILE_LOGGER) FLog.v(tag, message, cause);
    }

    public static void LOGI(final String tag, String message) {
        if (!SILENT) Log.i(tag, message);
        if (FILE_LOGGER) FLog.i(tag, message);
        // if (CRASHLYTICS) Crashlytics.log(Log.INFO, tag, message);
    }

    public static void LOGI(final String tag, String message, Throwable cause) {
        Log.i(tag, message, cause);
        if (FILE_LOGGER) FLog.i(tag, message, cause);
        /*if (CRASHLYTICS) {
            Crashlytics.log(Log.INFO, tag, message);
            Crashlytics.logException(cause);
        }*/
    }

    public static void LOGW(final String tag, String message) {
        if (!SILENT) Log.w(tag, message);
        if (FILE_LOGGER) FLog.w(tag, message);
        // if (CRASHLYTICS) Crashlytics.log(Log.WARN, tag, message);
    }

    public static void LOGW(final String tag, String message, Throwable cause) {
        Log.w(tag, message, cause);
        if (FILE_LOGGER) FLog.w(tag, message, cause);
        /*if (CRASHLYTICS) {
            Crashlytics.log(Log.WARN, tag, message);
            Crashlytics.logException(cause);
        }*/
    }

    public static void LOGE(final String tag, String message) {
        Log.e(tag, message);
        if (FILE_LOGGER) FLog.e(tag, message);
        ERRORS.add(tag + ": " + message);
        // if (CRASHLYTICS) Crashlytics.log(Log.ERROR, tag, message);
    }

    public static void LOGE(final String tag, String message, Throwable cause) {
        Log.e(tag, message, cause);
        if (FILE_LOGGER) FLog.e(tag, message);
        ERRORS.add(tag + ": " + message);
        /* if (CRASHLYTICS) {
            Crashlytics.log(Log.ERROR, tag, message);
            Crashlytics.logException(cause);
        }*/
    }

    /**
     * @return A log of the contents of a {@link android.util.SparseArray},
     * including recursive mapping of SparseArray values.
     */
    public static String logSparseArray(SparseArray<?> sparseArray) {
        StringBuilder log = new StringBuilder();
        log.append('[');
        for(int i = 0, e = sparseArray.size(); i < e; i++) {
            int key = sparseArray.keyAt(i);
            Object obj = sparseArray.get(key);
            String value = (obj instanceof SparseArray) ?
                    logSparseArray((SparseArray) obj) : String .valueOf(obj);
            log.append(key).append('=').append(value);
            if (i < (e - 1)) log.append(',');
        }
        log.append(']');
        return log.toString();
    }

    public static void sendDebugLogAsync(final Context context) {
        FLog.collectlogs(context, new LogCollecting() {
            @Override
            public void onLogCollected(File path, String mimeType) {
                LOGI(TAG, "onLogCollected(" + mimeType + ')');
                StringBuilder text = new StringBuilder();
                if (null != path) {
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(path));
                        String line;
                        while ((line = br.readLine()) != null) {
                            text.append(line);
                            text.append('\n');
                        }
                    } catch (IOException e) {
                        text = new StringBuilder();
                        text.append(e.getMessage());
                    }
                } else {
                    text.append(mimeType); // reason?
                }
                sendDebugLog(context, text.toString());
            }

            @Override
            public void onEmptyLogCollected() { onLogCollected(null, null); }

            @Override
            public void onLogCollectingError(String reason) {
                onLogCollected(null, reason);
            }
        });
    }

    /**
     * Only for use with debug versions of the app!
     */
    public static void sendDebugLog(Context context) {
        sendDebugLog(context, null);
    }

    /** @see {@link #sendDebugLog(Context)} */
    public static void sendDebugLog(Context context, String extra) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        StringBuilder log = new StringBuilder();

        // Append device build fingerprint, timestamp, installer, version info, enabled services, etc.
        PackageManager pm = context.getPackageManager();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String packageName = context.getPackageName();
        String versionName;
        int code = 0;
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            versionName = info.versionName;
            code = info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "??";
        }
        log.append("App version:\n").append(versionName).append(" : ").append(code).append("\n");
        SimpleDateFormat dateFormat = new SimpleDateFormat("KK:mm:ss a");
        log.append("Device fingerprint:\n").append(Build.FINGERPRINT).append("\n");
        log.append("Timestamp: ").append(dateFormat.format(Calendar.getInstance().getTime())).append("\n");
        log.append("Build: ").append(Utils.lastBuildTimestamp(context)).append("\n");
        log.append("Installer: ").append(Utils.getInstaller(context).getPackageName()).append("\n");
        log.append("HTC?: ").append(AudioHelper.isHTC(context)).append("\n");
        // CHECK: what app is the current media receiver.
        String receiverName = Settings.System.getString(
                context.getContentResolver(), Constants.getMediaButtonReceiver());
        if (!TextUtils.isEmpty(receiverName)) {
            ComponentName receiverComponent = ComponentName.unflattenFromString(receiverName);
            log.append(Constants.getMediaButtonReceiver()).append(": ").append(receiverComponent.getPackageName()).append("\n");
        }
        // CHECK: what notification and Accessibility services are activated.
        String notifKey = Constants.getEnabledNotificationListeners();
        String enabledNotifs = Settings.Secure.getString(context.getContentResolver(), notifKey);
        String enabledAccess = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        log.append(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).append(": ").append(enabledAccess).append("\n");
        log.append(notifKey).append(": ").append(enabledNotifs).append("\n");
        // CHECK: how the device handles notif-ringer link and other predicates.
        RingerNotificationLink linkChecker = new RingerNotificationLink();
        SystemVolume sysChecked = new SystemVolume();
        log.append(linkChecker.getClass().getSimpleName()).append(": ").append(linkChecker.apply(audioManager)).append("\n");
        log.append(sysChecked.getClass().getSimpleName()).append(": ").append(sysChecked.apply(audioManager)).append("\n");

        // Append map of all preferences
        log.append("Preferences:\n\n").append(Utils.getPreferencesString(
                PreferenceManager.getDefaultSharedPreferences(context))).append("\n\n");

        // Append any errors the app experienced
        if (ERRORS.size() > 0) {
            log.append("Errors:\n\n");
            for (String error : ERRORS) {
                log.append(error).append("\n");
            }
            log.append("\n\n");
            ERRORS.clear();
        }

        // Append extra logs from FileLogger
        if (!TextUtils.isEmpty(extra)) {
            log.append("FileLogger:\n\n").append(extra).append("\n\n");
        }

        // Append all system volume levels and ringer mode
        VolumeManager manager = new VolumeManager(audioManager);
        log.append("VolumeManager:\n\n").append(manager.toString()).append("\n");
        log.append("Ringer mode: ").append(audioManager.getRingerMode()).append("\n");
        log.append("Media receiver: ").append(Utils.getPackageNames(
                Utils.getMediaReceivers(context.getPackageManager()))).append("\n\n");

        // Log information about the device screen/ resolution
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        Point size = new Point();
        display.getSize(size);
        display.getMetrics(dm);
        log.append("Display width: ").append(size.x).append("px\n");
        log.append("Display height: ").append(size.y).append("px\n");
        log.append("Density: ").append(dm.densityDpi).append("dpi\n");
        log.append("Density: ").append(dm.density).append("\n\n");

        // Log available/ total memory info
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        log.append("Memory total: ").append(mi.totalMem / 1048576L).append("mb\n");
        log.append("Memory available: ").append(mi.availMem / 1048576L).append("mb\n");
        log.append("Memory threshold: ").append(mi.threshold / 1048576L).append("mb\n");
        log.append("Memory low?: ").append(mi.lowMemory);

        try {
            // Write everything to a file
            File logsDir = context.getCacheDir();
            if (logsDir == null) {
                throw new IOException("Cache directory inaccessible");
            }
            logsDir = new File(logsDir, "logs");
            deleteRecursive(logsDir);
            logsDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String fileName = "Noyze_log_" + sdf.format(new Date()) + ".txt";
            File logFile = new File(logsDir, fileName);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile)));
            writer.write(log.toString());
            writer.close();
            logFile.setWritable(true, false);

            // Send the file
            Intent sendIntent = new Intent(Intent.ACTION_SENDTO)
                    .setData(Uri.parse("mailto:" + SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "Noyze debug log")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(
                            "content://" + LogAttachmentProvider.AUTHORITY + "/" + fileName));

            // Make sure there's an app for that!
            if (null != sendIntent.resolveActivity(context.getPackageManager())) {
                context.startActivity(Intent.createChooser(sendIntent,
                        context.getString(R.string.send_logs_chooser_title)));
            }

        } catch (IOException e) {
            LOGE(TAG, "Error accessing or sending app's logs.", e);
        } catch (ActivityNotFoundException nfe) {
            LOGE(TAG, "Error launching an email app to mail the app's logs.");
        }
    }

    private static void deleteRecursive(File file) {
        if (file != null) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            } else {
                file.delete();
            }
        }
    }
}