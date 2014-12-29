package me.barrasso.android.volume.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;

import it.gmariotti.changelibs.library.view.ChangeLogListView;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;

/**
 * Example with Dialog
 *
 * @author Gabriele Mariotti (gabri.mariotti@gmail.com)
 */
public class ChangeLogDialog extends DialogFragment {

    public static void show(Activity activity, final boolean check) {
        if (check && !showChangeLog(activity)) return;
        FragmentManager fm = activity.getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        String tag = ChangeLogDialog.class.getSimpleName();
        Fragment prev = fm.findFragmentByTag(tag);
        if (prev != null) ft.remove(prev);
        ft.addToBackStack(null);
        new ChangeLogDialog().show(ft, tag);
    }

    protected static boolean showChangeLog(Context context) {
        final String version = "version";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int versionCode = getVersionCode(context);
        final boolean hasVersion = prefs.contains(version);
        final int savedVersion = prefs.getInt(version, versionCode);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(version, versionCode);
        editor.apply();
        return (!hasVersion || (savedVersion < versionCode));
    }

    protected static int getVersionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException nnfe) {
            LogUtils.LOGE(ChangeLogDialog.class.getSimpleName(), "Error obtaining version code.", nnfe);
            return -1;
        }
    }

    public ChangeLogDialog() { }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
        ChangeLogListView chgList = (ChangeLogListView) layoutInflater.inflate(R.layout.changelog_fragment, null);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.changelog_title)
                .setView(chgList)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                ).create();
    }
}