package me.barrasso.android.volume.ui;

/*
 * SystemInfo.java
 *
 * Copyright (C) 2014 Thomas James Barrasso
 */
 
// Android Packages
import android.view.WindowManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * Simple helper class for obtaing info about the device's
 * system bar/ status bar dimensions.
 *
 * @author		Thomas James Barrasso <contact @ tombarrasso.com>
 * @since		06-25-2012
 * @version		1.00
 * @category	Helper
 */

public final class SystemInfo
{
	public static final String TAG = SystemInfo.class.getSimpleName(),
							   PACKAGE = SystemInfo.class.getPackage().getName();

	public static final String ANDROID_PACKAGE = "android";
	
	// Localized constants for status bar height.
	private static final int LOW_DPI_STATUS_BAR_HEIGHT = 19;
	private static final int MEDIUM_DPI_STATUS_BAR_HEIGHT = 25;
	private static final int HIGH_DPI_STATUS_BAR_HEIGHT = 38;
	private static final int XHIGH_DPI_STATUS_BAR_HEIGHT = 50;
	
	// Localized constants for system bar height.
	private static final int LOW_DPI_SYSTEM_BAR_HEIGHT = 48;
	private static final int MEDIUM_DPI_SYSTEM_BAR_HEIGHT = 48;
	private static final int HIGH_DPI_SYSTEM_BAR_HEIGHT = 48;
	private static final int XHIGH_DPI_SYSTEM_BAR_HEIGHT = 48;

	private static final String STATUS_BAR_HEIGHT = "status_bar_height";
	private static final String DIMEN = "dimen";
	private static final String SYSTEM_BAR_HEIGHT = "system_bar_height";

	/**
	 * Find the height of the current system status bar.
	 * If this cannot be determined rely on a default.
	 */
	private static final int mHeightId = Resources.getSystem()
		.getIdentifier(STATUS_BAR_HEIGHT, DIMEN, ANDROID_PACKAGE);
	private static final int mHeightId2 = Resources.getSystem()
		.getIdentifier(SYSTEM_BAR_HEIGHT, DIMEN, ANDROID_PACKAGE);
	private static int mBarHeight = -1;
	private static int mBarHeight2 = -1;

	// Try to retrieve the system's status bar height
	// by querying the system's resources.
	static {
        Resources sysRes = Resources.getSystem();
		if (mHeightId != 0) {
			try {
				mBarHeight = sysRes.getDimensionPixelSize(mHeightId);
			} catch(Resources.NotFoundException e) {}
		}
		
		if (mHeightId2 != 0) {
			try {
				mBarHeight2 = sysRes.getDimensionPixelSize(mHeightId2);
			} catch(Resources.NotFoundException e) {}
		}
	}

	/**
	 * @return The height of the system status bar. This is
	 * done by querying the system's resources, or if that fails
	 * by using fallbacks for different screen densities.
	 */
	public static int getStatusBarHeight(Context context)
	{
		if (mBarHeight > 0) return mBarHeight;

		// Get display metrics for window.
		final DisplayMetrics mMetrics = new DisplayMetrics();
		((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
			.getDefaultDisplay().getMetrics(mMetrics);

		int statusBarHeight;

		switch (mMetrics.densityDpi)
		{
			case DisplayMetrics.DENSITY_HIGH:
				statusBarHeight = HIGH_DPI_STATUS_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_MEDIUM:
				statusBarHeight = MEDIUM_DPI_STATUS_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_LOW:
				statusBarHeight = LOW_DPI_STATUS_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_XHIGH:
				statusBarHeight = XHIGH_DPI_STATUS_BAR_HEIGHT;
				break;
			default:
				statusBarHeight = MEDIUM_DPI_STATUS_BAR_HEIGHT;
		}

		return statusBarHeight;
	}
	
	/**
	 * @return The height of the system bar. This is
	 * done by querying the system's resources, or if that fails
	 * by using fallbacks for different screen densities.
	 */
	public static int getSystemBarHeight(Context context)
	{
		if (mBarHeight2 > 0) return mBarHeight2;

		// Get display metrics for window.
		final DisplayMetrics mMetrics = new DisplayMetrics();
		((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
			.getDefaultDisplay().getMetrics(mMetrics);

		int statusBarHeight;

		switch (mMetrics.densityDpi)
		{
			case DisplayMetrics.DENSITY_HIGH:
				statusBarHeight = HIGH_DPI_SYSTEM_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_MEDIUM:
				statusBarHeight = MEDIUM_DPI_SYSTEM_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_LOW:
				statusBarHeight = LOW_DPI_SYSTEM_BAR_HEIGHT;
				break;
			case DisplayMetrics.DENSITY_XHIGH:
				statusBarHeight = XHIGH_DPI_SYSTEM_BAR_HEIGHT;
				break;
			default:
				statusBarHeight = MEDIUM_DPI_SYSTEM_BAR_HEIGHT;
		}

		return statusBarHeight;
	}
}