package me.barrasso.android.volume.ui;

import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Solution from StackOverflow, user: code578841441.
 * @see {@linkplain http://stackoverflow.com/questions/17831395/how-can-i-detect-a-click-in-an-ontouch-listener}
 * Additionally, a {@link android.view.View.OnLongClickListener} can also be assigned.
 */
public final class OnTouchClickListener implements View.OnTouchListener {

    /**
     * Defines the duration in milliseconds we will wait to see if a touch event
     * is a tap or a scroll. If the user does not move within this interval, it is
     * considered to be a tap.
     */
    private static final int TAP_TIMEOUT = 100;

    /**
     * Distance a touch can wander before we think the user is scrolling in pixels
     */
    private static final int TOUCH_SLOP = 16;

    private static final int MSG_LONG_CLICK = 1;

    /*package*/ final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LONG_CLICK:
                    onLongClick((View) msg.obj);
                    break;
            }
        }
    };

    private final View.OnLongClickListener mLongListener;
    private final View.OnClickListener mListener;

    public OnTouchClickListener(View.OnClickListener listener) {
        this(listener, null);
    }

    public OnTouchClickListener(View.OnClickListener listener, View.OnLongClickListener longListener) {
        mListener = listener;
        mLongListener = longListener;
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    private long mLongPressTimeout;
    private long startTime;
    private float startX;
    private float startY;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTime = System.currentTimeMillis();
                startX = event.getX();
                startY = event.getY();
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_LONG_CLICK, v), mLongPressTimeout);
                break;
            case MotionEvent.ACTION_UP: {
                mHandler.removeMessages(MSG_LONG_CLICK);
                float endX = event.getX();
                float endY = event.getY();
                if (isAClick(startX, endX, startY, endY))
                    if (null != mListener)
                        mListener.onClick(v);
                break;
            }
        }
        if (null != v.getParent())
            v.getParent().requestDisallowInterceptTouchEvent(true);
        return false;
    }

    /*package*/ void onLongClick(View v) {
        if (null != mLongListener) {
            mLongListener.onLongClick(v);
        }
    }

    private boolean isAClick(float startX, float endX, float startY, float endY) {
        // First check the timeout for a click.
        long time = System.currentTimeMillis();
        if (time - startTime > TAP_TIMEOUT)
            return false;

        // Next check the distance travelled.
        float differenceX = Math.abs(startX - endX);
        float differenceY = Math.abs(startY - endY);
        if (differenceX > TOUCH_SLOP || differenceY > TOUCH_SLOP)
            return false;

        return true;
    }

}
