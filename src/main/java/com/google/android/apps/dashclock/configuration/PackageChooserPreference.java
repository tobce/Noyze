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

package com.google.android.apps.dashclock.configuration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.apps.dashclock.ui.SimplePagedTabsHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;

/**
 * A preference that allows the user to choose a set of applications.
 */
public class PackageChooserPreference extends Preference {

    public PackageChooserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PackageChooserPreference(Context context) {
        super(context);
    }

    public PackageChooserPreference(Context context, AttributeSet attrs,
                                    int defStyle) {
        super(context, attrs, defStyle);
    }

    private static final TextUtils.SimpleStringSplitter COLON_SPLITTER = new TextUtils.SimpleStringSplitter(':');

    public void setPackages(Set<String> packages) {
        if (null == packages)  {
            setValue("");
            return;
        }
        setValue((packages.size() == 0) ? "" : TextUtils.join(":", packages));
    }

    public Set<String> getPackages(String value, String defaultValue) {
        String listVal = value;
        if (TextUtils.isEmpty(value)) {
            listVal = defaultValue;
        }

        Set<String> packages = new HashSet<String>();
        TextUtils.SimpleStringSplitter splitter = COLON_SPLITTER;
        splitter.setString(listVal);
        while (splitter.hasNext()) {
            packages.add(splitter.next());
        }

        return packages;
    }

    public Set<String> getPackages() {
        return getPackages(getPersistedString(""), "");
    }

    public void setValue(String value) {
        if (callChangeListener(value)) {
            persistString(value);
            notifyChanged();
        }
    }

    @Override
    protected void onClick() {
        super.onClick();

        AppChooserDialogFragment fragment = AppChooserDialogFragment.newInstance();
        fragment.setPreference(this);

        Activity activity = (Activity) getContext();
        activity.getFragmentManager().beginTransaction()
                .add(fragment, getFragmentTag())
                .commit();
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

        Activity activity = (Activity) getContext();
        AppChooserDialogFragment fragment = (AppChooserDialogFragment) activity
                .getFragmentManager().findFragmentByTag(getFragmentTag());
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
            setValue(restoreValue ? getPersistedString("") : (String) defaultValue);
    }

    public String getFragmentTag() {
        return "app_chooser_" + getKey();
    }

    public static class AppChooserDialogFragment extends DialogFragment {
        public static int REQUEST_CREATE_SHORTCUT = 1;

        private PackageChooserPreference mPreference;
        private Set<String> appPackages;

        private ActivityListAdapter mAppsAdapter;

        private ListView mAppsList;

        public AppChooserDialogFragment() {
        }

        public static AppChooserDialogFragment newInstance() {
            return new AppChooserDialogFragment();
        }

        public void setPreference(PackageChooserPreference preference) {
            mPreference = preference;
            tryBindLists();
            appPackages = mPreference.getPackages();
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            tryBindLists();
        }

        static DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        };

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Force Holo Light since ?android:actionBarXX would use dark action bar
            Context layoutContext = new ContextThemeWrapper(getActivity(),
                    android.R.style.Theme_Holo_Light);

            LayoutInflater layoutInflater = LayoutInflater.from(layoutContext);
            View rootView = layoutInflater.inflate(R.layout.dialog_app_chooser, null);
            final ViewGroup tabWidget = (ViewGroup) rootView.findViewById(android.R.id.tabs);
            final ViewPager pager = (ViewPager) rootView.findViewById(R.id.pager);
            pager.setPageMargin((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    getResources().getDisplayMetrics()));

            SimplePagedTabsHelper helper = new SimplePagedTabsHelper(layoutContext,
                    tabWidget, pager);
            helper.addTab(R.string.title_apps, R.id.apps_list);

            // Set up apps
            mAppsList = (ListView) rootView.findViewById(R.id.apps_list);
            mAppsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> listView, View view,
                        int position, long itemId) {
                    Log.i("PackageChooserPreference", "onItemClick(" + position + ")");
                    String appPackage = mAppsAdapter.getPackageName(position);
                    Checkable checker = (Checkable) view;
                    checker.setChecked(!checker.isChecked());
                    if (checker.isChecked()) {
                        appPackages.add(appPackage);
                    } else {
                        appPackages.remove(appPackage);
                    }
                    mPreference.setPackages(appPackages);
                }
            });

            tryBindLists();

            return new AlertDialog.Builder(getActivity())
                    .setView(rootView)
                    .setPositiveButton(android.R.string.ok, dismissListener)
                    .create();
        }

        private void tryBindLists() {
            if (mPreference == null) {
                return;
            }

            if (isAdded() && mAppsAdapter == null) {
                mAppsAdapter = new ActivityListAdapter(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
            }

            if (mAppsAdapter != null && mAppsList != null) {
                mAppsList.setAdapter(mAppsAdapter);
            }
        }

        static class ActivityInfo {
            CharSequence label;
            Drawable icon;
            ComponentName componentName;
        }

        private class ActivityListAdapter extends BaseAdapter {
            private Intent mQueryIntent;
            private LayoutInflater inflater;
            private List<ActivityInfo> mInfos;

            private ActivityListAdapter(Intent queryIntent) {
                mQueryIntent = queryIntent;
                inflater = LayoutInflater.from(getActivity());
                PackageManager mPackageManager = getActivity().getPackageManager();

                mInfos = new ArrayList<ActivityInfo>();
                List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(queryIntent,
                        0);
                for (ResolveInfo ri : resolveInfos) {
                    ActivityInfo ai = new ActivityInfo();
                    try {
                        ai.icon = ri.loadIcon(mPackageManager);
                    } catch (OutOfMemoryError ome) {
                        LogUtils.LOGE("AppChooserPreference", "Ran out of memory displaying icon.", ome);
                    }
                    ai.label = ri.loadLabel(mPackageManager);
                    ai.componentName = new ComponentName(ri.activityInfo.packageName,
                            ri.activityInfo.name);
                    mInfos.add(ai);
                }

                // Make sure to sort by alphabetical order (not comparing capitalization).
                Collections.sort(mInfos, new Comparator<ActivityInfo>() {
                    @Override
                    public int compare(ActivityInfo activityInfo, ActivityInfo activityInfo2) {
                        return String.valueOf(activityInfo.label).toUpperCase().compareTo(
                                String.valueOf(activityInfo2.label).toUpperCase());
                    }
                });
            }

            @Override
            public int getCount() {
                return mInfos.size();
            }

            @Override
            public boolean isEnabled(int position) {
                return true;
            }

            @Override
            public boolean areAllItemsEnabled() {
                return true;
            }

            @Override
            public Object getItem(int position) {
                return mInfos.get(position);
            }

            public String getPackageName(int position) {
                return mInfos.get(position).componentName.getPackageName();
            }

            public Intent getIntent(int position) {
                return new Intent(mQueryIntent)
                        .setComponent(mInfos.get(position).componentName);
            }

            @Override
            public long getItemId(int position) {
                return mInfos.get(position).componentName.hashCode();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup container) {
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item_intent_check, container, false);
                }

                ActivityInfo ai = mInfos.get(position);
                TextView title = (TextView) convertView;
                title.setText(ai.label);
                int iconSize = Resources.getSystem().getDimensionPixelSize(android.R.dimen.app_icon_size);
                ai.icon.setBounds(0, 0, iconSize, iconSize);
                title.setCompoundDrawablesRelative(ai.icon, null, null, null);

                if (title instanceof Checkable) {
                    Checkable checker = (Checkable) title;
                    checker.setChecked(appPackages.contains(ai.componentName.getPackageName()));
                }

                return convertView;
            }
        }
    }
}