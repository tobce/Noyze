package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.Surface;
import android.text.TextUtils;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;
import android.database.ContentObserver;

import me.barrasso.android.volume.BuildConfig;
import me.barrasso.android.volume.utils.ReflectionUtils;

// Hidden APIs
import android.view.IRotationWatcher;

import java.util.Collection;
import java.util.List;
import java.util.WeakHashMap;
import java.lang.reflect.Method;

/**
 * Manager of {@link PopupWindow}s. Useful to monitoring system-wide events such as
 * the home button being pressed, the screen being turned off, and the device rotating.
 * This class is the centralized location linking PopupWindows to their owner.<br />
 * Classes using this tool may hold on to references of {@link PopupWindow}s and show/
 * hide them as necessary. Although it is not required, hard references to these windows
 * should be held and {@link PopupWindowManager#remove} should be called to discard unused
 * windows and better facilitate clean up.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class PopupWindowManager extends IRotationWatcher.Stub {

	public static final String TAG = PopupWindowManager.class.getSimpleName();
		
	/** @return True if the current {@link Thread} is the main, UI thread. */
    public static boolean isUiThread() {
        return (Looper.myLooper() != null && Looper.myLooper().equals(Looper.getMainLooper()));
        // return (Looper.getMainLooper().getThread().equals(Thread.currentThread()));
    }
    
    protected static void postOnUiThread(Runnable run) {
    	(new Handler(Looper.getMainLooper())).post(run);
    }

	//	========== CANCEL RECEIVER ==========
	
	// Localized from PhoneWindowManager... these are helpful!
	static public final String SYSTEM_DIALOG_REASON_KEY = "reason";
    static public final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    static public final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static public final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    static public final String SYSTEM_DIALOG_REASON_KEYGUARD = "lock";
    
    // Filter for a BroadcastReceiver to cancel this Window, as necessary.
    private static final IntentFilter CANCEL_FILTER = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    static {
		CANCEL_FILTER.addAction(Intent.ACTION_SCREEN_ON);
		CANCEL_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
    	CANCEL_FILTER.addAction(Intent.ACTION_USER_PRESENT);
    	CANCEL_FILTER.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
    }
    
    private static IInterface iWindowManager;
    
    /** @return An instance of android.view.IWindowManager. */
	public synchronized static IInterface getIWindowManager() {
		if (null == iWindowManager) {
			iWindowManager = ReflectionUtils.getIInterface(
                    Context.WINDOW_SERVICE, "android.view.IWindowManager$Stub");
		}
		return iWindowManager;
	}
	
	@SuppressWarnings("depecation")
	protected static int getRotation(Display mDisplay) {		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
			return mDisplay.getRotation();
		else
			return mDisplay.getOrientation();
	}
	
	/** Register an {@link IRotationWatcher} with IWindowManager. */
	protected static boolean watchRotations(IRotationWatcher watcher, final boolean watch) {
		// NOTE: removeRotationWatcher is only available on Android 4.3 (API 18) and 4.4 (API 19 & 20).
		final String methodName = ((watch) ? "watchRotation" : "removeRotationWatcher" );
		final IInterface mWM = getIWindowManager();
        if (null == mWM) return false;
        
        try {
            Method mMethod = mWM.getClass().getDeclaredMethod(
            	methodName, new Class[]{ IRotationWatcher.class });
            if (mMethod == null) return false;
            mMethod.setAccessible(true);
            mMethod.invoke(mWM, watcher);

            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Cannot register " + IRotationWatcher.class.getSimpleName() , t);
            return false;
        }
	}
    
    /**
     * {@link BroadcastReceiver} to handle events like turning the screen off,
     * pressing the home button, etc. These might result in canceling the
     * popup window depending on the settings.
     */
    /*package*/ final class CancelReceiver extends BroadcastReceiver {
    	@Override
		public void onReceive(Context context, Intent intent) {
			if (null == intent || TextUtils.isEmpty(intent.getAction())) return;
			final String mAction = intent.getAction();
			
			// Handle global actions (often used to notify dialogs).
			if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equalsIgnoreCase(mAction)) {
				final String mReason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
				closeSystemDialogs(mReason);
			} else if (Intent.ACTION_SCREEN_OFF.equalsIgnoreCase(mAction) ||
					   Intent.ACTION_SCREEN_ON.equalsIgnoreCase(mAction)) {
				final boolean sOn = Intent.ACTION_SCREEN_ON.equalsIgnoreCase(mAction);
				screen(sOn); // The screen state has changed (on/ off).
			}
		}
    }
    
    /**
     *	{@link ContentObserver} to handle changes in the active Input Method.
     */
    /*package*/ final class InputMethodObserver extends ContentObserver {
		public InputMethodObserver(Handler h) { super(h); }
		@Override public boolean deliverSelfNotifications() { return true; }

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			mActiveInputMethod = retrieveActiveInputMethod();
		}
	}
    
    // Map of managed PopupWindows identified by their unique IDs.
	protected final WeakHashMap<Integer, PopupWindow> mPopupWindows = new WeakHashMap<Integer, PopupWindow>();
	protected final Handler mUiHandler = new Handler(Looper.getMainLooper());
	protected CancelReceiver mCancelReceiver;
	protected WindowManager mWindowManager;
	protected InputMethodObserver mInputMethodObserver;
	protected InputMethodManager mInputMethodManager;
	protected ComponentName mActiveInputMethod;
	protected Context mContext;
	protected int mRotation;
    protected boolean isScreenOn = true;
	
	public PopupWindowManager(Context context) {
		mContext = context;
		mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		mInputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		mActiveInputMethod = retrieveActiveInputMethod();
		mRotation = getRotation(mWindowManager.getDefaultDisplay());
		registerCancelReceiver();
		watchRotations(this, true);
		registerInputMethodObserver();
	}
	
	/** @return True if the device is currently in landscape. */
	public boolean isLandscape() {
		return (mRotation == Surface.ROTATION_90 ||
				mRotation == Surface.ROTATION_270);
	}
	
	/** @see {@link InputMethodManager#isActive} */
	public boolean isInputViewShown() {
		return mInputMethodManager.isActive();
	}
	
	/**
	 * @return The {@link ComponentName} for the active Input Method,
	 * or null if one could not be obtained.
	 */
	public ComponentName getActiveInputMethod() {
		return mActiveInputMethod;
	}
	 
	protected ComponentName retrieveActiveInputMethod() {
        if (null == mContext) return null;
		final String id = Settings.Secure.getString(
			mContext.getContentResolver(), 
			Settings.Secure.DEFAULT_INPUT_METHOD
		);
		
		if (TextUtils.isEmpty(id)) return null;
		List<InputMethodInfo> mInputMethodProperties = mInputMethodManager.getEnabledInputMethodList();
		for (InputMethodInfo mInputMethod : mInputMethodProperties) {
			if (id.equals(mInputMethod.getId())) {
				return mInputMethod.getComponent();
			}
		}
		
		return null;
	}
	
	public int getRotation() {
		return mRotation;
	}
	
	public final Context getContext() {
		return mContext;
	}
	
	/** Add a {@link PopupWindow} to be managed. */
	public void add(PopupWindow window) {
		synchronized (mPopupWindows) {
			mPopupWindows.put(window.getId(), window);
		}
	}
	
	/**
	 * Removes and destroys a {@link PopupWindow}.
	 * @return True if a PopupWindow was removed this way.
	 */
	public boolean remove(PopupWindow window) {
		synchronized (mPopupWindows) {
			PopupWindow removed = mPopupWindows.remove(window.getId());
			if (null != removed) removed.onDestroy();
			return (null != removed);
		}
	}
		
	/**
	 * Hide all cancelable {@link PopupWindow}s.
	 * @param force True if even non-cancelable windows should be hidden.
	 */
	protected void hide(final boolean force) {
		if (BuildConfig.DEBUG) {
			Log.v(TAG, ((force) ? "Hiding ALL PopupWindows." :
								  "Hiding cancelable PopupWindows."));
		}
		
		// Hide all popup windows as necessary.
		postOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mPopupWindows) {
					for (PopupWindow pw : mPopupWindows.values()) {
						if (force || pw.isCancelable()) {
							pw.hide();
						}
					}
				}
			}
		});
	}
	
	/**
	 * @see {@link Intent#ACTION_CLOSE_SYSTEM_DIALOGS}
	 */
	protected void closeSystemDialogs(final String reason) {
		if (BuildConfig.DEBUG) {
			Log.v(TAG, Intent.ACTION_CLOSE_SYSTEM_DIALOGS + ':' + reason);
		}
		
		// Hide all popup windows as necessary.
		postOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mPopupWindows) {
					for (PopupWindow pw : mPopupWindows.values()) {
						pw.closeSystemDialogs(reason);
					}
				}
			}
		});
	}

    /** @return True if the screen is on. */
    public boolean isScreenOn() { return isScreenOn; }
	
	/**
	 * @see {@link Intent#ACTION_SCREEN_OFF}
	 */
	protected void screen(final boolean on) {
		if (BuildConfig.DEBUG) {
			Log.v(TAG, ((on) ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF));
		}
        isScreenOn = on;
		
		// Hide all popup windows as necessary.
		postOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mPopupWindows) {
					for (PopupWindow pw : mPopupWindows.values()) {
                        if (null != pw) {
                            pw.screen(on);
                        }
					}
				}
			}
		});
	}
	
	@Override
	public void onRotationChanged(final int rotation) throws RemoteException {
		if (BuildConfig.DEBUG) Log.v(TAG, "--onRotationChanged(" + String.valueOf(rotation) + ')');
		mRotation = rotation;
		
		// Propagate the rotation change event to all PopupWindows.
		postOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mPopupWindows) {
					for (PopupWindow pw : mPopupWindows.values()) {
                        if (null != pw) {
                            pw.onRotationChanged(rotation);
                        }
					}
				}
			}
        });
	}
	
	// ========== WINDOW MANAGER ==========
	
	public final WindowManager getWindowManager() {
		return mWindowManager;
	}
	
	/** Convenience method for {@link WindowManager#updateViewLayout}. */
	public void updateViewLayout(View layout, WindowManager.LayoutParams params) {
		mWindowManager.updateViewLayout(layout, params);
	}
	
	/** Convenience method for {@link WindowManager#addView}. */
	public void addView(View layout, WindowManager.LayoutParams params) {
		mWindowManager.addView(layout, params);
	}
	
	/** Convenience method for {@link WindowManager#removeView}. */
	public void removeView(View layout) {
		mWindowManager.removeView(layout);
	}
	
	protected void registerCancelReceiver() {
		if (null == mCancelReceiver)
			mCancelReceiver = new CancelReceiver();
		mContext.registerReceiver(mCancelReceiver, CANCEL_FILTER);
	}
	
	protected void registerInputMethodObserver() {
		if (null == mInputMethodObserver)
			mInputMethodObserver = new InputMethodObserver(mUiHandler);
		mContext.getContentResolver().registerContentObserver(
			Settings.Secure.CONTENT_URI, true, mInputMethodObserver); 
	}
	
	protected void unregisterCancelReceiver() {
		if (null != mCancelReceiver)
			mContext.unregisterReceiver(mCancelReceiver);
		mCancelReceiver = null;
	}
	
	protected void unregisterInputMethodObserver() {
		if (null != mInputMethodObserver)
			mContext.getContentResolver().unregisterContentObserver(mInputMethodObserver);
		mInputMethodObserver = null;
	}
	
	/** Destroy all {@link PopupWindow}s and all receivers/ listeners. */
	public void destroy() {
		unregisterCancelReceiver();
		unregisterInputMethodObserver();
		watchRotations(this, false);
		synchronized (mPopupWindows) {
            Collection<PopupWindow> windows = mPopupWindows.values();
            synchronized (windows) {
                for (PopupWindow pw : windows) {
                    pw.onDestroy();
                }
            }
            mPopupWindows.clear();
        }
        mWindowManager = null;
        mInputMethodManager = null;
        mContext = null;
	}
	
}