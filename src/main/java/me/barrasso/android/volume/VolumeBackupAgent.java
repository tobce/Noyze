package me.barrasso.android.volume;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.os.Build;

/** Simple {@link android.app.backup.BackupAgent} to store {@link android.content.SharedPreferences}. */
@TargetApi(Build.VERSION_CODES.FROYO)
public final class VolumeBackupAgent extends BackupAgentHelper {
    @Override
    public void onCreate() {
        addHelper(SharedPreferences.class.getSimpleName(),
                new SharedPreferencesBackupHelper(this, getPackageName() + "_preferences"));
    }
}