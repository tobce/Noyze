package me.barrasso.android.volume.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import me.barrasso.android.volume.R;

/**
 * Simple helper for dealing with third-party resources. Allows access to a
 * specified app's resources and information for use with themes.
 */
public final class ThemeManager {

    private final Resources appResources;
    private final Context appContent;
    private final String packageName;

    public ThemeManager(Context thisContext, String packageName) throws PackageManager.NameNotFoundException{
        this.packageName = packageName;
        appContent = thisContext.createPackageContext(packageName,
                (Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE));
        appResources = thisContext.getPackageManager().getResourcesForApplication(packageName);
    }

    public Context getContext() { return appContent; }
    public Resources getResources() { return appResources; }
    public String getPackageName() { return packageName; }

    public boolean getBoolean(String name) {
        int resId = appResources.getIdentifier(name, R.bool.class.getSimpleName(), packageName);
        return appResources.getBoolean(resId);
    }

    public int getColor(String name) {
        int resId = appResources.getIdentifier(name, R.color.class.getSimpleName(), packageName);
        return appResources.getColor(resId);
    }

    public int getDimensionPixelSize(String name) {
        int resId = appResources.getIdentifier(name, R.dimen.class.getSimpleName(), packageName);
        return appResources.getDimensionPixelSize(resId);
    }

    public Drawable getDrawable(String name) {
        int resId = appResources.getIdentifier(name, R.drawable.class.getSimpleName(), packageName);
        return appResources.getDrawable(resId);
    }

    public Drawable getDrawableForDensity(String name, int density) {
        int resId = appResources.getIdentifier(name, R.drawable.class.getSimpleName(), packageName);
        return appResources.getDrawableForDensity(resId, density);
    }

    public int getInteger(String name) {
        int resId = appResources.getIdentifier(name, R.integer.class.getSimpleName(), packageName);
        return appResources.getInteger(resId);
    }

    public String  getString(String name) {
        int resId = appResources.getIdentifier(name, R.string.class.getSimpleName(), packageName);
        return appResources.getString(resId);
    }
}