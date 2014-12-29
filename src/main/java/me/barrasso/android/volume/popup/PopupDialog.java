package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.TypedValue;
import android.util.Log;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.text.TextUtils;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import me.barrasso.android.volume.BuildConfig;
import me.barrasso.android.volume.R;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;

/**
 * A {@link android.app.Dialog}-like {@link PopupWindow}. Unlike Dialog, this class
 * does not require an application {@link Context} and can be displayed from a background
 * {@link android.app.Service}. A Dialog is comprised of a title and a content view, the latter
 * managed by the user of this class.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PopupDialog extends PopupWindow implements DialogInterface {

	/**
	 * {@link Log} tag also used in identifying this {@link PopupWindow}.
	 * @see {@link PopupWindow#getName}
	 */
	public static final String TAG = PopupDialog.class.getSimpleName();
	
	public static final int THEME_DARK	= R.drawable.dialog_full_holo_dark;
	public static final int THEME_LIGHT = R.drawable.dialog_full_holo_light;
	
	private static final int DISMISS = 0x43;
    private static final int CANCEL = 0x44;
    private static final int SHOW = 0x45;
	
	// Create and initialize our window parameters.
	// Note (from the API): "With the default gravity it (x and y) are ignored."
	private static WindowManager.LayoutParams getWindowLayoutParams() {
		int flags = (LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH	|
				     LayoutParams.FLAG_DIM_BEHIND			);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			flags |= LayoutParams.FLAG_HARDWARE_ACCELERATED;
		if (!BuildConfig.DEBUG) flags |= LayoutParams.FLAG_SECURE;
		LayoutParams WPARAMS = new WindowManager.LayoutParams(
			LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
			LayoutParams.TYPE_SYSTEM_ALERT, flags, PixelFormat.TRANSLUCENT);
		final int windowAnimations = getInternalStyle("Animation_Dialog");
		if (windowAnimations > 0) WPARAMS.windowAnimations = windowAnimations;
		WPARAMS.dimAmount = 0.6f;
		WPARAMS.packageName = PopupDialog.class.getPackage().getName();
		WPARAMS.setTitle(TAG);
		WPARAMS.gravity = Gravity.CENTER;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
			WPARAMS.screenBrightness = WPARAMS.buttonBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		return WPARAMS;
	}
	
	private Handler mListenersHandler;
	
	private Message mCancelMessage;
    private Message mDismissMessage;
    private Message mShowMessage;
	
	private int mTheme = THEME_DARK;
	private boolean mFullscreen;
	private WindowManager.LayoutParams mWindowAttributes;
	
	public PopupDialog(PopupWindowManager manager) {
		super(manager);
		mListenersHandler = new ListenersHandler(this);
	}
	
	@Override
	protected void onCreate() {
		Context context = pWindowManager.getContext();
		Resources mResources = context.getResources();
		updateDimens();
		LayoutInflater inflater = LayoutInflater.from(context);
		mLayout = (ViewGroup) inflater.inflate(R.layout.dialog_title_holo, null);
	}
	
	/** Set whether or not to show a title icon. */
	protected void setShowIcon(boolean show) {
		View icon = findViewById(R.id.icon);
		if (null != icon) icon.setVisibility(((show) ? View.VISIBLE : View.GONE));
	}
	
	/** Set the drawable resource for the title icon; 0 to hide. */
	public void setIcon(int resId) {
		if (resId <= 0) {
			setShowIcon(false);
			return;
		}
		
		ImageView icon = (ImageView) findViewById(R.id.icon);
		if (null != icon) {
			icon.setImageResource(resId);
		}
	}
	
	/** Set the drawable for the title icon; null to hide. */
	public void setIcon(Drawable iconDrawable) {
		if (null == iconDrawable) {
			setShowIcon(false);
			return;
		}
		
		ImageView icon = (ImageView) findViewById(R.id.icon);
		if (null != icon) {
			icon.setImageDrawable(iconDrawable);
		}
	}
	
	/** Set whether the dialog should be fullscreen. */
	public void setFullscreen(boolean fullscreen) {
		mFullscreen = fullscreen;
		updateDimens();
		onWindowAttributesChanged();
	}
	
	/** Set the theme: {@link #THEME_DARK} or {@link #THEME_LIGHT}.
	 * Default is {@link #THEME_DARK}. */
	public void setTheme(int theme) {
		if (mTheme != theme && (theme == THEME_DARK || theme == THEME_LIGHT)) {
			mTheme = theme;
			TextView messageView = (TextView) findViewById(android.R.id.message);
			if (null != messageView) messageView.setTextColor(((mTheme == THEME_DARK) ? Color.WHITE : Color.BLACK));
			mLayout.setBackgroundResource(mTheme);
			FrameLayout contentContainer = (FrameLayout) findViewById(R.id.contentPanel);
			if (null != contentContainer) {
				switch (mTheme) {
					case THEME_DARK:
						contentContainer.setForeground(
							getResources().getDrawable(R.drawable.ab_solid_shadow_holo));
						break;
					case THEME_LIGHT:
						contentContainer.setForeground(null);
						break;
				}
			}
		}
	}
	
	/**
     * Cancel the dialog.  This is essentially the same as calling {@link #dismiss()}, but it will
     * also call your {@link DialogInterface.OnCancelListener} (if registered).
     */
	@Override
	public void cancel() {
		if (isAttached() && mCancelMessage != null) {
            Message.obtain(mCancelMessage).sendToTarget();
        }
		dismiss();
	}
	
	/**
	 * Dismiss the dialog. This will remove the window and destroy all associated
	 * Views. Will call your {@link DialogInterface.OnDismissListener} (if registered).
	 */
	@Override
    public void dismiss() {
    	if (isAttached() && mDismissMessage != null) {
            Message.obtain(mDismissMessage).sendToTarget();
        }
    	onDestroy();
    }
    
    @Override
    public void show() {
    	if (!isShowing() && mShowMessage != null) {
            Message.obtain(mShowMessage).sendToTarget();
    	}
    	super.show();
    }
	
	/** @see {@link #setTheme} */
	public int getTheme() {
		return mTheme;
	}
	
	protected void updateDimens() {
		final int[] wDims = getWindowDimensions();
		LayoutParams wParams = getWindowParams();
		if (mFullscreen) {
			wParams.width = wParams.height = LayoutParams.MATCH_PARENT;
		} else {
			wParams.width = ((3 * wDims[0]) / 4);
			wParams.height = ((3 * wDims[1]) / 4);
		}
	}
	
	@Override
	protected WindowManager.LayoutParams getWindowParams() {
		if (null == mWindowAttributes)
			mWindowAttributes = getWindowLayoutParams();
		return mWindowAttributes;
	}
	
	@Override
	public void onRotationChanged(int rotation) {
		super.onRotationChanged(rotation);
		updateDimens();
		onWindowAttributesChanged();
	}
	
	/**
     * Set the title text for this dialog's window. The text is retrieved
     * from the resources with the supplied identifier.
     *
     * @param titleId the title's text resource identifier
     */
	public void setTitle(int titleId) {
		setTitle(pWindowManager.getContext().getText(titleId));
	}
	
	/**
	 * Set the message text for this dialog's window. The text is retrieved
     * from the resources with the supplied identifier.
	 */
	public void setMessage(int messageId) {
		setMessage(pWindowManager.getContext().getText(messageId));
	}
	
	/**
     * Set the title text for this dialog's window.
     * 
     * @param title The new text to display in the title.
     */
	public void setTitle(CharSequence title) {
		setText(title, android.R.id.title);
	}
	
	/**
	 * Set the message to display using the given string.
	 */
	public void setMessage(CharSequence message) {
		Log.d(TAG, "Set message: " + message);
		setText(message, android.R.id.message);
	}
	
	protected void setText(CharSequence title, int resId) {
		if (!(mLayout instanceof ViewGroup)) return;
		ViewGroup mDecor = (ViewGroup) mLayout;
		TextView titleView = (TextView) mDecor.findViewById(resId);
		if (null == titleView) return;
		titleView.setText(title);
		((ViewGroup) titleView.getParent()).setVisibility(View.VISIBLE);
	}
	
	/**
     * Set the screen content from a layout resource.  The resource will be
     * inflated, adding all top-level views to the screen.
     * 
     * @param layoutResID Resource ID to be inflated.
     */
	public void setContentView(int layoutResID) {
		LayoutInflater inflater = LayoutInflater.from(pWindowManager.getContext());
		setContentView(inflater.inflate(layoutResID, null), null);
	}
	
	/**
     * Set the screen content to an explicit view.  This view is placed
     * directly into the screen's view hierarchy.  It can itself be a complex
     * view hierarhcy.
     * 
     * @param view The desired content to display.
     */
	public void setContentView(View view) {
		setContentView(view, null);
	}
	
	/**
     * Set the screen content to an explicit view.  This view is placed
     * directly into the screen's view hierarchy.  It can itself be a complex
     * view hierarhcy.
     * 
     * @param view The desired content to display.
     * @param params Layout parameters for the view.
     */
    public void setContentView(View view, ViewGroup.LayoutParams params) {
    	if (!(mLayout instanceof ViewGroup)) return;
        ViewGroup mDecor = (ViewGroup) mLayout;
		ViewGroup mContent = (ViewGroup) mDecor.findViewById(android.R.id.content);
		if (null == mContent) return;
		if (null == params)
			mContent.addView(view);
		else
			mContent.addView(view, params);
    }
    
    // ========== Listeners ==========
    // Imported from /android/app/Dialog.java
    
    /**
     * Set a listener to be invoked when the dialog is dismissed.
     * @param listener The {@link DialogInterface.OnDismissListener} to use.
     */
    public void setOnDismissListener(OnDismissListener listener) {
        if (listener != null) {
            mDismissMessage = mListenersHandler.obtainMessage(DISMISS, listener);
        } else {
            mDismissMessage = null;
        }
    }
    
    /**
     * Sets a listener to be invoked when the dialog is shown.
     * @param listener The {@link DialogInterface.OnShowListener} to use.
     */
    public void setOnShowListener(OnShowListener listener) {
        if (listener != null) {
            mShowMessage = mListenersHandler.obtainMessage(SHOW, listener);
        } else {
            mShowMessage = null;
        }
    }
    
    /**
     * Set a listener to be invoked when the dialog is canceled.
     * <p>
     * This will only be invoked when the dialog is canceled, if the creator
     * needs to know when it is dismissed in general, use
     * {@link #setOnDismissListener}.
     * 
     * @param listener The {@link DialogInterface.OnCancelListener} to use.
     */
    public void setOnCancelListener(OnCancelListener listener) {
        if (listener != null) {
            mCancelMessage = mListenersHandler.obtainMessage(CANCEL, listener);
        } else {
            mCancelMessage = null;
        }
    }
    
    private static final class ListenersHandler extends Handler {
        private WeakReference<DialogInterface> mDialog;

        public ListenersHandler(PopupDialog dialog) {
            mDialog = new WeakReference<DialogInterface>(dialog);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS:
                    ((OnDismissListener) msg.obj).onDismiss(mDialog.get());
                    break;
                case CANCEL:
                    ((OnCancelListener) msg.obj).onCancel(mDialog.get());
                    break;
                case SHOW:
                    ((OnShowListener) msg.obj).onShow(mDialog.get());
                    break;
            }
        }
    }
}