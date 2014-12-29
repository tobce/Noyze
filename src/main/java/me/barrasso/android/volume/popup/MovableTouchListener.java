package me.barrasso.android.volume.popup;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.animation.Animator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;

import me.barrasso.android.volume.BuildConfig;
import me.barrasso.android.volume.R;

/**
 * {@link View#OnTouchListener} for a {@link PopupWindow} to become movable!
 * Offers to ability to "lock" motion for either/ both the X and Y axes.
 * <em>Min SDK:</em> {@link Build$VERSION_CODES#HONEYCOMB}, but
 * could be configured to use support library.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MovableTouchListener implements View.OnTouchListener {
	
	public static enum Anchor {
		/** Snap along x-axis (top & bottom). */		AXIS_X,
		/** Snap along y-axis (left & right). */		AXIS_Y,
		/** Snap along quadrants (4 corners/ edges). */	EDGES
	}
	
	private final PointEvaluator pEvaluator = new PointEvaluator();
	private final int mTouchSlop;
	private ValueAnimator mAnimator;
	
	protected PopupWindow mPopupWindow;
	protected Anchor mAnchor;
	
	public MovableTouchListener(PopupWindow pw) {
		mPopupWindow = pw;
		final ViewConfiguration mViewConfiguration = ViewConfiguration.get(mPopupWindow.getContext());
		mTouchSlop = mViewConfiguration.getScaledWindowTouchSlop();
	}
	
	public void anchor(final Anchor anchor) { mAnchor = anchor; }
	public void lockX(final boolean lX) { lockX = lX; }
	public void lockY(final boolean lY) { lockY = lY; }
	
	private boolean lockX, lockY;
	private int downX, downY, aPID = MotionEvent.INVALID_POINTER_ID; // MotionEvent handling...
	
	@Override
    public boolean onTouch(View v, MotionEvent event) {
    	if (null != mAnimator && mAnimator.isRunning()) return false;
    	if (null == mPopupWindow) return false;
    
    	switch (event.getAction() & MotionEvent.ACTION_MASK) {
    		case MotionEvent.ACTION_DOWN:
    			LayoutParams params = mPopupWindow.getWindowParams();
    			aPID = event.getPointerId(0);
    			downX = (int) (params.x - event.getRawX());
    			downY = (int) (params.y - event.getRawY());
				break;
			case MotionEvent.ACTION_UP:
				// Handle "snapping" to the right/ left edge of the screen.
				if (null != mAnchor) {
					final Rect mBounds = mPopupWindow.getBounds();
					final int[] screen = mPopupWindow.getWindowDimensions();
					switch (mAnchor) {
						case AXIS_Y:
							if (mBounds.left + (mBounds.width() / 2) > (screen[0] / 2)) { // Snap right.
								snap(screen[0] - mBounds.width(), PopupWindow.POSITION_UNCHANGED);
							} else { // Snap left.
								snap(0, PopupWindow.POSITION_UNCHANGED);
							}
							break;
						case AXIS_X:
							if (mBounds.top + (mBounds.height() / 2) > (screen[1] / 2)) { // Snap bottom.
								snap(PopupWindow.POSITION_UNCHANGED, screen[1] - mBounds.height());
							} else { // Snap top.
								snap(PopupWindow.POSITION_UNCHANGED, 0);
							}
							break;
						case EDGES:
							if (mBounds.top + (mBounds.height() / 2) > (screen[1] / 2)) { // Snap bottom half.
								if (mBounds.left + (mBounds.width() / 2) > (screen[0] / 2)) // Snap bottom right.
									snap(screen[0] - mBounds.width(), screen[1] - mBounds.height());
								else // Snap bottom left.
									snap(0, screen[1] - mBounds.height());
							} else { // Snap top half.
								if (mBounds.left + (mBounds.width() / 2) > (screen[0] / 2)) // Snap top right.
									snap(screen[0] - mBounds.width(), 0);
								else // Snap top left.
									snap(0, 0);
							}
							break;
					}
				}
				break;
			case MotionEvent.ACTION_POINTER_UP:
				// Extract the index of the pointer that left the touch sensor
				final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
				final int pointerId = event.getPointerId(pointerIndex);
				if (pointerId == aPID) {
					// This was our active pointer going up. Choose a new
					// active pointer and adjust accordingly.
					final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
					aPID = event.getPointerId(newPointerIndex);
				}
			case MotionEvent.ACTION_MOVE:
				// Find the index of the active pointer and fetch its position
				final int mPID = event.findPointerIndex(aPID);
				float xMove = event.getRawX();
				float yMove = event.getRawY();

				// From http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
				final int dx = (int) (xMove + downX);
				final int dy = (int) (yMove + downY);

				mPopupWindow.position(((lockX) ? PopupWindow.POSITION_UNCHANGED : dx),
								  ((lockY) ? PopupWindow.POSITION_UNCHANGED : dy));
								  
				break;
			case MotionEvent.ACTION_CANCEL:
      			aPID = MotionEvent.INVALID_POINTER_ID;
      			break;
    	}
    	
    	return true;
    }
    
    public static class PointEvaluator implements TypeEvaluator {    
    	@Override
        public Object evaluate(float fraction, Object startValue, Object endValue) {
            PointF startPoint = (PointF) startValue;
            PointF endPoint = (PointF) endValue;
            return new PointF(startPoint.x + fraction * (endPoint.x - startPoint.x),
                startPoint.y + fraction * (endPoint.y - startPoint.y));
        }
    }
    
    public void destroy() {
    	if (null != mAnimator) mAnimator.cancel();
    	mAnimator = null;
    	mPopupWindow = null;
    }
    
    /** Snap the view to this position.
     * @see {@link PopupWindow#POSITION_UNCHANGED} */
    private void snap(final int x, final int y) {
    	final Rect mBounds = mPopupWindow.getBounds();
    	final PointF pStart = new PointF(mBounds.left, mBounds.top);
    	final PointF pFinish = new PointF(((x == PopupWindow.POSITION_UNCHANGED) ? mBounds.left : x),
    									 ((y == PopupWindow.POSITION_UNCHANGED) ? mBounds.top : y));
    	mAnimator = ValueAnimator.ofObject(pEvaluator, pStart, pFinish);
    	mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override public void onAnimationUpdate(ValueAnimator animation) {
				PointF cPoint = (PointF) animation.getAnimatedValue();
				mPopupWindow.position((int) cPoint.x, (int) cPoint.y);
			}
		});
		// Destroy the animator when the animation is complete.
		mAnimator.addListener(new ValueAnimator.AnimatorListener() {
			@Override public void onAnimationCancel(Animator animation) { mAnimator = null; }
			@Override public void onAnimationEnd(Animator animation) { mAnimator = null; }
			@Override public void onAnimationRepeat(Animator animation) { mAnimator = null; }
			@Override public void onAnimationStart(Animator animation) { }
		});
    	mAnimator.start();
    }
}