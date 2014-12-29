package me.barrasso.android.volume.popup;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import me.barrasso.android.volume.LogUtils;

public class FullscreenPopupWindow extends PopupWindow implements ViewTreeObserver.OnGlobalLayoutListener {

    public FullscreenPopupWindow(PopupWindowManager pWindowManager) { super(pWindowManager); }

    public static interface OnFullscreenChangeListener {
        public void onFullscreenChange(boolean fullscreen);
    }

    private final List<SoftReference<OnFullscreenChangeListener>> mListeners =
            new ArrayList<SoftReference<OnFullscreenChangeListener>>();
    private boolean fullscreen;

    @Override
    public void onCreate() {
        Context context = getContext();
        mLayout = new FullscreenTestView(context);
    }

    public void addOnFullscreenChangeListener(OnFullscreenChangeListener listener) {
        mListeners.add(new SoftReference<OnFullscreenChangeListener>(listener));
    }

    /** @return True if the device is full screen (status bar is hidden). */
    public boolean isFullscreen() {
        return fullscreen;
    }

    @Override
    public void show() {
        mCancelable = false;
        mCloseOnTouchOutside = false;
        mCloseOnLongClick = false;
        super.show();
    }

    @Override
    public WindowManager.LayoutParams getWindowParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE      |
                     WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	);
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                1, WindowManager.LayoutParams.MATCH_PARENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, flags, PixelFormat.TRANSPARENT);
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(getName());
        WPARAMS.gravity = (Gravity.FILL_VERTICAL | Gravity.START);
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }

    /*package*/ void testFullScreen() {
        // Get screen dimensions and View height.
        final int height = getWindowHeight();
        if (height <= 0) return;

        final int mViewHeight = mLayout.getHeight();
        final int topOffset = Math.max(0, height - mViewHeight);
        final boolean mFull = (topOffset == 0);

        LogUtils.LOGI("FullscreenPopupWindow", "testFullScreen(viewHeight=" +
                mViewHeight + ", windowHeight=" + height + ')');

        // Make sure the state has changed.
        if (mFull != fullscreen) {
            fullscreen = mFull;
            notifyStateChanged();
        }
    }

    protected void notifyStateChanged() {
        LogUtils.LOGI("FullscreenPopupWindow", "notifyStateChanged()");
        int index = 0;
        for (Reference<OnFullscreenChangeListener> reference : mListeners) {
            if (null != reference) {
                OnFullscreenChangeListener listener = reference.get();
                if (null == listener) {
                    mListeners.remove(index);
                } else {
                    listener.onFullscreenChange(fullscreen);
                }
            } else {
                mListeners.remove(index);
            }
            index++;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver vto = mLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ViewTreeObserver vto = mLayout.getViewTreeObserver();
        vto.removeOnGlobalLayoutListener(this);
    }

    @Override
    public void onGlobalLayout() {
        testFullScreen();
    }

    // View for testing purposes.
    /*package*/ final class FullscreenTestView extends View {
        public FullscreenTestView(Context mContext) { super(mContext); }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            LogUtils.LOGI("FullscreenPopupWindow", "onLayout(" + changed + ")");
            if (changed) testFullScreen();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            LogUtils.LOGI("onSizeChanged", "onLayout(" + w + ", " + h + ")");
            testFullScreen();
        }
    }
}