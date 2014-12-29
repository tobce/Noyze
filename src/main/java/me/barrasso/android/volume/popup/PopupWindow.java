package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.util.Log;
import android.util.DisplayMetrics;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Property;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.ViewTreeObserver;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.widget.FrameLayout;
import android.graphics.Rect;

import me.barrasso.android.volume.BuildConfig;
import me.barrasso.android.volume.ui.SystemInfo;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for a popup window that is always attached to {@link WindowManager},
 * but toggles it's visibility between {@link View#VISIBLE} and {@link View#GONE}.<br />
 * Implementing classes need to create and manage their own {@link View}'s as well
 * as user interaction, but this class takes care of adding, removing, showing, and
 * hiding the window on the screen base on user interaction.<br />
 * <em>Note:</em> All layouts are assigned to a decor {@link FrameLayout} as their
 * parent. This is used to keep track of key events and prevent the broadcasting
 * of accessibility events.<br />
 * All subclasses must implement {@link #getWindowParams} and {@link #onCreate},
 * returning {@link android.view.WindowManager.LayoutParams} and creating the
 * {@link android.view.View}s to be presented, respectively.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public abstract class PopupWindow implements View.OnKeyListener,
	View.OnTouchListener, OnSystemUiVisibilityChangeListener, View.OnLongClickListener {

	public static final int MESSAGE_HIDE = 0x00000001;
	public static final int MESSAGE_SHOW = 0x00000010;

    // Property of PopupWindows: auto hide duration.
    public static final Property<PopupWindow, Integer> TIMEOUT =
            Property.of(PopupWindow.class, Integer.TYPE, "autoHideDuration");

	public static final int POSITION_UNCHANGED = Integer.MIN_VALUE;

	private static final AtomicInteger VIEW_ID = new AtomicInteger(0);
	private static final AtomicInteger ATTACHED_WINDOWS = new AtomicInteger(0);
	private static final AtomicInteger VISIBLE_WINDOWS = new AtomicInteger(0);

    protected static int getInternalStyle(final String clazzName, final String resIdName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            if (null != clazz) {
                Field aDialog = clazz.getField(resIdName);
                if (null != aDialog) {
                    aDialog.setAccessible(true);
                    return (Integer) aDialog.get(null);
                }
            }
        } catch (Throwable e) { /* Shit */ }

        return 0;
    }

    /** @returns The resource ID for "com.android.systemui.R$dimen#resIdName", or 0. */
    protected static int getSystemUiDimen(final String resIdName) {
        return getInternalStyle("com.android.systemui.R$dimen", resIdName);
    }

	/** @returns The resource ID for "com.android.internal.R$style#resIdName", or 0. */
	protected static int getInternalStyle(final String resIdName) {
		return getInternalStyle("com.android.internal.R$style", resIdName);
	}
	
	/** Set hidden {@link WindowManager.LayoutParams$hasListeners} */
	public static WindowManager.LayoutParams setHasSystemUiListeners(
		WindowManager.LayoutParams mParams, boolean hasListeners) {
		if (mParams == null) return mParams;	
		try {
			final Field mField = mParams.getClass()
				.getDeclaredField("hasSystemUiListeners");
			
			if (mField == null) return mParams;
			mField.setAccessible(true);
			mField.setBoolean(mParams, hasListeners);
		} catch (Throwable e) { }
		
		return mParams;
	}
      
    //	========== WINDOW MANAGEMENT ==========
        
	/** Generate and return a new View ID, [1, {@link Integer#MAX_VALUE}]. */
	protected static int generateId() {
		return VIEW_ID.incrementAndGet();
	}
	
	/** @return The number of active popup windows. */
	public static int getAttachedWindows() {
		return ATTACHED_WINDOWS.get();
	}
	
	/** @return The number of visible popup windows. */
	public static int getVisibleWindows() {
		return VISIBLE_WINDOWS.get();
	}
	
	/*package*/ class HideHandler extends Handler {
		public HideHandler(Looper loopah) {
			super(loopah);
		}
		
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
            	case MESSAGE_HIDE:
            		hide();
            		break;
            	case MESSAGE_SHOW:
            		show();
            		break;
            }
        }
	}
	
	// Window barHeight and height (only to be used based on rotation!)
	private final int widthPixels;
	private final int heightPixels;
	
	// 0 for never, otherwise time in milliseconds.
	protected int autoHideDuration = 0;
	protected int mStatusBarHeight;
	
	protected boolean mShowing;
	protected boolean attached;
	/*package*/ boolean mAllowOffScreen = false;
	/*package*/ boolean mCancelable = true;
	/*package*/ boolean mCloseOnTouchOutside = true;
	/*package*/ boolean mDelayAutoHideOnUserInteraction = true;
	/*package*/ boolean mCloseOnLongClick = false;
    protected boolean enabled = true;

	protected final ViewConfiguration mViewConfiguration;
	protected final Handler mUiHandler;
	
	protected PopupWindowManager pWindowManager;
	protected View mLayout;
	private ViewGroup mDecor;

    protected boolean created = false;
		
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public PopupWindow(PopupWindowManager windowManager) {
		pWindowManager = windowManager;
		
		// Obtain height & barHeight once, then determine which is which
		// based on the orientation/ rotation of the device.
		DisplayMetrics dm = new DisplayMetrics();
        Display mDisplay = pWindowManager.getWindowManager().getDefaultDisplay();
        mRotation = PopupWindowManager.getRotation(mDisplay);
		mDisplay.getMetrics(dm);
		widthPixels = ((isLandscape()) ? dm.heightPixels : dm.widthPixels);
		heightPixels = ((isLandscape()) ? dm.widthPixels : dm.heightPixels);
		
		Context context = pWindowManager.getContext();
		mStatusBarHeight = SystemInfo.getStatusBarHeight(context);
		mViewConfiguration = ViewConfiguration.get(context);
		mUiHandler = new HideHandler(context.getMainLooper());

		onCreate();
		
		// If a layout was made, initialize the PopupWindow and handle
		// system-wide events. A layout MUST be supplied at initialization
		// or the PopupWindow cannot be displayed.
		if (mLayout != null) {
			SilentFrameLayout layout = new SilentFrameLayout(context);
			layout.setId(generateId());
			layout.setOnTouchListener(this);
			layout.setOnDispatchKeyListener(this);
			layout.setOnSystemUiVisibilityChangeListener(this);
			layout.getViewTreeObserver().addOnScrollChangedListener(gScrollListener);
			layout.setOnLongClickListener(this);
			// Place the designated layout in a container.
			layout.addView(mLayout);
			mDecor = layout;
            created = true;
			attach();
		}
		
		// Become managed by PopupWindowManager. It's already been created, so we
		// might as well become managed by it. 
		pWindowManager.add(this);
	}
	
	public Resources getResources() {
		return getContext().getResources();
	}
	
	public Context getContext() {
		return pWindowManager.getContext();
	}
	
	/** @return The height, in pixels, of the system status bar. */
	public int getStatusBarHeight() {
		return mStatusBarHeight;
	}
	
	/**
	 * If true, {@link #setAutoHideDuration} will be delayed by {@link @onUserInteraction}.
	 * Motion, keyboard, trackpad, etc. events will all delay the hiding of this window.<br />
	 * Default value is true.
	 */
	public void setTransient(boolean mTransient) {
		mDelayAutoHideOnUserInteraction = mTransient;
	}
	
	/**
	 * If true (and this window parameters has {@link android.view.WindowManager.LayoutParams#FLAG_WATCH_OUTSIDE_TOUCH}
	 * this window will automatically close when the user touches outside of it.<br />
	 * Default value is true.
	 */
	public void setCanceledOnTouchOutside(boolean mCloseOnTouch) {
		mCloseOnTouchOutside = mCloseOnTouch;
	}
	
	/**
	 * If true, a {@link View.OnLongClickListener#onLongClick(android.view.View)}
	 * will hide this popup window. Default value is false.
	 */
	public void setCloseOnLongClick(boolean mCloseOnLong) {
		mCloseOnLongClick = mCloseOnLong;
	}
	
	/**
	 * The time, in milliseconds, to automatically hide this
	 * popup window after being shown. 0 for never (default).
	 */
	public void setAutoHideDuration(int duration) {
		autoHideDuration = duration;
	}

    public int getAutoHideDuration() { return autoHideDuration; }
	
	/**
	 * Allowing the PopupWindow to be shown, partly or entirely,
	 * off of the screen. This will bound all calls to {@link #move(int, int)}
	 * and {@link #position(int, int)} by {0, statusBarHeight} and {screenWidth, screenHeight}.
	 */
	public void setAllowOffScreen(boolean allowOffScreen) {
		mAllowOffScreen = allowOffScreen;
	}
	
	/** Set the click listener for the popup window as a whole. */
	public void setOnClickListener(OnClickListener mClickListener) {
		mDecor.setOnClickListener(mClickListener);
	}
	
	/** @see {@link Intent#ACTION_CLOSE_SYSTEM_DIALOGS} */
	public void closeSystemDialogs(final String reason) {
		// Override to handle specific requests to close system dialogs.
		if (mCancelable) {
			hide();
		}
	}

	/** @see {@link Intent#ACTION_SCREEN_OFF} */
	public void screen(final boolean on) {
		// Override to specially handle screen on/ off events.
		if (mCancelable && !on) {
			hide();
		}
	}

    /** Set true to enable/ disable this PopupWindow. */
    public void setEnabled(final boolean mEnabled) {
        enabled = mEnabled;
        if (enabled) {
            attach();
        } else {
            hide();
            detach();
        }
    }

    /** @return True if this PopupWindow is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

	/**
	 * Set whether this window can be cancelled by user interaction (i.e. back button).<br />
	 * Default value is true.
	 */
	public void setCancelable(boolean cancelable) {
		mCancelable = cancelable;
	}
	
	/** @return True if this window is cancelable. Default value is true. */
	public boolean isCancelable() {
		return mCancelable;
	}
	
	/** @return The decor window {@link View}. */
	public View peekDecor() {
		return mDecor;
	}
	
	/**
	 * Move the PopupWindow by a delta x and y. Pass 0 to keep the
	 * current position. <em>Note</em>: not all windows observe this behavior.
	 */
	public void move(final int dx, final int dy) {
		LayoutParams wParams = getWindowParams();
		wParams.gravity = (Gravity.LEFT | Gravity.TOP);
		wParams.x += dx;
		wParams.y += dy;
		if (!mAllowOffScreen) bound();
		onWindowAttributesChanged();
	}
	
	/**
	 * Update the x and y positions of the window. Pass
	 * {@link PopupWindow#POSITION_UNCHANGED} to keep the
	 * current position. <em>Note</em>: not all windows
	 * observe this behavior.
	 */
	public void position(final int x, final int y) {
		LayoutParams wParams = getWindowParams();
		wParams.gravity = (Gravity.LEFT | Gravity.TOP);
		if (x != POSITION_UNCHANGED) wParams.x = x;
		if (y != POSITION_UNCHANGED) wParams.y = y;
		if (!mAllowOffScreen) bound();
		onWindowAttributesChanged();
	}
	
	/** Bind the {@link WindowManager.LayoutParams} within the screen. */
	private void bound() {
		LayoutParams wParams = getWindowParams();
		wParams.x = Math.min(Math.max(0, wParams.x), getWindowWidth());
		wParams.y = Math.min(Math.max(mStatusBarHeight, wParams.y), getWindowHeight());
	}
	
	/** @return A {@link android.graphics.Rect} of the root view's position.	 */
	public Rect getBounds() {
		LayoutParams wParams = getWindowParams();
		return new Rect(wParams.x, wParams.y, wParams.x + getWidth(), wParams.y + getHeight());
	}
	
	/**
	 * Called when the status bar changes visibility because of a call to
	 * {@link View#setSystemUiVisibility(int)}.
	 *
	 * @param visibility  Bitwise-or of flags {@link android.view.View#SYSTEM_UI_FLAG_LOW_PROFILE} or
	 * {@link android.view.View#SYSTEM_UI_FLAG_HIDE_NAVIGATION}.  This tells you the
	 * <strong>global</strong> state of the UI visibility flags, not what your
	 * app is currently applying.
	 */
	@Override
	public void onSystemUiVisibilityChange(int visibility) {
		if (BuildConfig.DEBUG) {
			Log.v("PopupWindow", "--onSystemUiVisibilityChange(" + String.valueOf(visibility) + ')');
		}
	}
	
	// onKey is used for Views with focus. If they do not handle the event
	// we want to be sure to handle the {@link KeyEvent#KEYCODE_BACK} event!
	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (BuildConfig.DEBUG) {
			Log.v("PopupWindow", "--onKey(" + String.valueOf(keyCode) + ')');
		}
		
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK: {
				switch (event.getAction()) {
					case KeyEvent.ACTION_UP: {
						if (!event.isCanceled()) {
							onBackPressed();
						}
						break;
					}
				}
				return true;
			}
		}
		
		// Delay auto-hiding if set to do so (and we didn't already handle this event).
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			onUserInteraction();
		}
		
		return false;
	}
	
	// Handle scroll events globally, cause user interaction event.
	/*package*/ ViewTreeObserver.OnScrollChangedListener gScrollListener = new ViewTreeObserver.OnScrollChangedListener() {
		public void onScrollChanged() {
			if (BuildConfig.DEBUG) Log.v("PopupWindow", "--OnScrollChangedListener()");
			onUserInteraction();
		}
	};
	
	@Override
	public boolean onLongClick(View decor) {
		if (BuildConfig.DEBUG) Log.v("PopupWindow", "--onLongClick()");
		
		if (mCloseOnLongClick) {
			hide();
			return true;
		}
		return false;
	}
	
	/**
     * Finds a view that was identified by the id attribute from the XML.
     *
     * @param id the identifier of the view to find
     * @return The view if found or null otherwise.
     */
    public View findViewById(int id) {
        return mDecor.findViewById(id);
    }
    
    /**
     * Called whenever a key, touch, or trackball event is dispatched to the
     * popup window. Implement this method if you wish to know that the user has
     * interacted with the device in some way while your window is running.
     */
    protected void onUserInteraction() {
    	if (BuildConfig.DEBUG) Log.v("PopupWindow", "--onUserInteraction()");
    
    	if (mShowing && autoHideDuration > 0 && mDelayAutoHideOnUserInteraction) {
    		mUiHandler.removeMessages(MESSAGE_HIDE);
    		mUiHandler.sendEmptyMessageDelayed(MESSAGE_HIDE, autoHideDuration);
    	}
    }
	
	/**
	 * Show the popup window.<br /><em>Note:</em> not all inheriting
	 * classes can be shown without providing additional information!
	 */
	protected void show() {
        if (!attached) attach(); // If we're not attached, so it!
		if (!mShowing && null != mDecor) {
			onVisibilityChanged(View.VISIBLE);
			mDecor.setVisibility(View.VISIBLE);
			mShowing = true;
			VISIBLE_WINDOWS.incrementAndGet();
			// Auto-hide after a certain time?
			if (autoHideDuration > 0) {
				mUiHandler.sendEmptyMessageDelayed(MESSAGE_HIDE, autoHideDuration);
			}
		}
        onUserInteraction();
	}
	
	/** Hide the popup window. */
	public void hide() {
		if (mShowing && null != mDecor) {
			onVisibilityChanged(View.GONE);
			mDecor.setVisibility(View.GONE);
			mShowing = false;
			VISIBLE_WINDOWS.decrementAndGet();
			// Remove unnecessary calls to hide().
			mUiHandler.removeMessages(MESSAGE_HIDE);
		}
	}
	
	/** @return The ID for the root view of this popup window. */
	public int getId() {
		return ((mDecor == null) ? View.NO_ID : mDecor.getId());
	}
	
	/** @return A name for this PopupWindow.
	 * Default implementation returns the class name. */
	public String getName() { return getClass().getSimpleName(); }
	
	/** Initialize this popup window and create a base layout to display. */
	abstract void onCreate();
	
	/** @return The window parameters for displaying this popup window. */
	abstract WindowManager.LayoutParams getWindowParams();

	/** Window visibility changed.
	 * @see {@link android.view.View} */
	public void onVisibilityChanged(int visibility) {}

    protected int mRotation;

	/** Device rotation changed.
	 * @see {@link android.view.Surface} */	
	public void onRotationChanged(int rotation) {
        mRotation = rotation;
		onUserInteraction();
	}

    public int getRotation() { return mRotation; }

    /** @return True if the device is currently in landscape. */
    public boolean isLandscape() {
        return (mRotation == Surface.ROTATION_90 ||
                mRotation == Surface.ROTATION_270);
    }
	
	public void onAttachedToWindow() {}
    public void onDetachedFromWindow() {}
	
	/**
     * Called when the dialog has detected the user's press of the back
     * key.  The default implementation simply cancels the window (only if
     * it is cancelable), but you can override this to do whatever you want.
     */
    public void onBackPressed() {
    	if (BuildConfig.DEBUG) 
			Log.v("PopupWindow", "--onBackPressed()");
    
        if (mCancelable) {
            hide();
        }
    }
	
	/** Proxy for {@link WindowManager#updateViewLayout}. */
	public void onWindowAttributesChanged() {
		if (BuildConfig.DEBUG) {
			Log.v("PopupWindow", "Updating " + getName() + " with the following window parameters:");
			Log.v("PopupWindow", getWindowParams().toString());
		}

        WindowManager.LayoutParams params = setHasSystemUiListeners(getWindowParams(), true);
        if (!attached) {
            attach();
        } else {
            pWindowManager.updateViewLayout(mDecor, params);
        }

		if (null != mDecor) {
            mDecor.requestLayout();
            mDecor.invalidate();
        }
	}
	
	/** @return True if the popup window is mShowing. */
	public boolean isShowing() {
		return mShowing;
	}
	
	/** @return True if the popup window is attached. */
	public boolean isAttached() {
		return attached;
	}
	
	/** Attach the popup window via {@link WindowManager}. */
	protected void attach() {
		if (!attached && null != mDecor) {
			onAttachedToWindow();
			mDecor.setVisibility(View.GONE);
			pWindowManager.addView(mDecor,
				setHasSystemUiListeners(getWindowParams(), true));
			attached = true;
			ATTACHED_WINDOWS.incrementAndGet();
			if (BuildConfig.DEBUG) {
				Log.v("PopupWindow", "Attaching " + getName() + " with the following window parameters:");
				Log.v("PopupWindow", getWindowParams().toString());
			}
		}
	}
	
	/** Detach the popup window via {@link WindowManager}. */
	protected void detach() {
		if (attached && null != mDecor) {
			onDetachedFromWindow();
			pWindowManager.removeView(mDecor);
			mDecor.setVisibility(View.VISIBLE);
			attached = false;
			ATTACHED_WINDOWS.decrementAndGet();
		}
	}
	
	/** Destroy this popup window. If this method is overridden, be SURE to call super! */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onDestroy() {
		detach();

        // We should remove ourself or we'll run into issues
        // with "NullPointerException: Attempt to invoke virtual method"
        if (null != pWindowManager)
            pWindowManager.remove(this);

		pWindowManager = null;
		if (null != mDecor) {
			mDecor.setOnKeyListener(null);
			mDecor.setOnClickListener(null);
			mDecor.setOnTouchListener(null);
			mDecor.getViewTreeObserver().removeOnScrollChangedListener(gScrollListener);
			mDecor.setOnSystemUiVisibilityChangeListener(null);
			final int children = mDecor.getChildCount();
			if (children > 0) mDecor.removeViewsInLayout(0, children);
			if (mDecor instanceof SilentFrameLayout)
				((SilentFrameLayout) mDecor).setOnDispatchKeyListener(null);
		}
		mLayout = null;
		mDecor = null;
		mShowing = false;
	}
	
	/** @return A {@link View.OnClickListener} that hides this popup window. */
	public View.OnClickListener getHideOnClickListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				hide();
			}
		};
	}
	
	/** @return The popup window's height. */
	public int getHeight() {
		return mDecor.getHeight();
	}
	
	/** @return The popup window's barHeight. */
	public int getWidth() {
		return mDecor.getWidth();
	}
	
	/** @return The {@link android.view.Window} barHeight. */
    public int getWindowWidth() {
        return (getWindowDimensions())[0];
    }

    /** @return The {@link android.view.Window} height. */
    public int getWindowHeight() {
        return (getWindowDimensions())[1];
    }
    
    /** @return The {@link android.view.Window} barHeight and height (array index 0 and 1 respectively). */
    public int[] getWindowDimensions() {
    	final int[] WINDOW_DIMS = new int[2];
    	final boolean isLandscape = pWindowManager.isLandscape();
		WINDOW_DIMS[0] = ((isLandscape) ? heightPixels : widthPixels);
		WINDOW_DIMS[1] = ((isLandscape) ? widthPixels : heightPixels);
    	return WINDOW_DIMS;
    }
    
    // Methods localized from android.view.Window (hidden from SDK).
    
    /**
     * Called when a touch screen event was not handled by any of the views
     * under it. This is most useful to process touch events that happen outside
     * of your window bounds, where there is no view to receive it.
     * 
     * @param event The touch screen event being processed.
     * @return Return true if you have consumed the event, false if you haven't.
     *         The default implementation will cancel the dialog when a touch
     *         happens outside of the window bounds.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
    	final boolean outOfBounds = isOutOfBounds(event);
        if (mShowing && shouldCloseOnTouch(event)) {
            hide();
            return true;
        } else if (mShowing && !outOfBounds &&
        		   (event.getActionMasked() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
        	onUserInteraction();
        }
        
        return false;
    }
    
    /** @hide */
    public boolean shouldCloseOnTouch(MotionEvent event) {
        if (mCloseOnTouchOutside && (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE || isOutOfBounds(event)) && mDecor != null) {
            return true;
        }
        
        return false;
    }
    
    public boolean isOutOfBounds(MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final int slop = mViewConfiguration.getScaledWindowTouchSlop();
        return (x < -slop) || (y < -slop)
                || (x > (getWidth()+slop))
                || (y > (getHeight()+slop));
    }
    
    // Imported from eyes-free, SimpleOverlay.java
    // Speech Enabled Eyes-Free Android Applications
    // Copyright (C) 2010 Google Inc.
    // https://code.google.com/p/eyes-free/source/browse/trunk/libraries/utils/src/com/googlecode/eyesfree/widget/SimpleOverlay.java?r=799
    
    /**
     * {@link FrameLayout} that does not send accessibility events and
     * proxies all key events to a designated listener.
     */
    private static final class SilentFrameLayout extends FrameLayout {
    
    	private View.OnKeyListener mOnDispatchKeyListener;
    
        public SilentFrameLayout(Context context) {
            super(context);
        }
        
        public void setOnDispatchKeyListener(View.OnKeyListener onDispatchKeyListener) {
        	mOnDispatchKeyListener = onDispatchKeyListener;
        }

        @Override
        public boolean requestSendAccessibilityEvent(View view, AccessibilityEvent event) {
            return false; // Never send accessibility events.
        }

        @Override
        public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
            return; // Never send accessibility events.
        }
        
        @Override
		public boolean dispatchKeyEvent(KeyEvent event) {
			if (null != mOnDispatchKeyListener) {
				if (mOnDispatchKeyListener.onKey(this, event.getKeyCode(), event)) {
					return true;
				}
			}
			
			return super.dispatchKeyEvent(event);
		}
    }
}