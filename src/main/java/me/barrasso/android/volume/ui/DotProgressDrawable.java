package me.barrasso.android.volume.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import me.barrasso.android.volume.LogUtils;

/**
 * Simple {@link android.graphics.drawable.Drawable} to display
 * in a {@link android.widget.ProgressBar} that shows the progress
 * as several dots with different color based on the progress.
 */
public class DotProgressDrawable extends Drawable {

    public static enum DisplayMode {
        SQUARES, CIRCLES
    }

    private boolean complete = false;
    private DisplayMode mode = DisplayMode.SQUARES;
    private int mCompleteColor = Color.WHITE;
    private int mIncompleteColor = Color.BLACK;
    private int nSteps = 10;
    private int mIndex = 1;
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void setProgress(int index, int max) {
        nSteps = max;
        mIndex = index;
        invalidateSelf();
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        float backPadding = 1.9f;
        Rect b = getBounds();
        float width = b.width();
        mPaint.setColor(mIncompleteColor);
        float boxLeft = width / nSteps;
        float boxRight = boxLeft + 0.9f * width / nSteps;
        float boxTop = (b.height() - (boxRight - boxLeft)) / 2;
        float boxBottom = boxTop + (boxRight - boxLeft);
        RectF b2 = new RectF(b);
        b2.top = backPadding;
        b2.bottom = boxBottom;
        LogUtils.LOGI("DotProgess", "Background: " + b2.toString());
        canvas.drawRect(b, mPaint);
        width -= 2 * backPadding;
        for (int i = 0; i < nSteps; i++) {
            // Dimensions for the dot are a square, vertically centered to the
            // maximum number of squares that can fit in a container horizontally.
            float left = width * i / nSteps;
            float right = left + 0.9f * width / nSteps;
            float top = (b.height() - (right - left)) / 2;
            float bottom = top + (right - left);
            left += 2 * backPadding;
            right += backPadding;
            final RectF dot = new RectF(left, top, right, bottom);

            // Set the color and actually draw the rectangle.
            final boolean isComplete = (i + 1) <= mIndex;
            mPaint.setColor(isComplete ? mCompleteColor : mIncompleteColor);

            // Actually draw the objects on a canvas based on the display mode.
            switch (mode) {
                case CIRCLES:
                    if (!isComplete) {
                        mPaint.setColor(mCompleteColor);
                        canvas.drawCircle(dot.centerX(), dot.centerY(), dot.width() / 2, mPaint);
                        mPaint.setColor(mIncompleteColor);
                    }
                    canvas.drawCircle(dot.centerX(), dot.centerY(), (dot.width() / 2) - 0.9f, mPaint);
                    break;
                case SQUARES:
                    canvas.drawRect(dot, mPaint);
                    break;
            }
        }
    }

    public void setDisplayMode(DisplayMode mMode) { mode = mMode; }

    public void setCompleteColor(int color) {
        mCompleteColor = color;
    }

    public void setIncompleteColor(int color) {
        mIncompleteColor = color;
    }

    public void setSteps(int steps) {
        nSteps = steps;
        invalidateSelf();
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** Saves a DotProgressDrawable state to a Bundle. */
    public void save(Bundle bundle) {
        if (null == bundle) return;
        final Bundle save = new Bundle();
        save.putInt("completeColor", mCompleteColor);
        save.putInt("incompleteColor", mIncompleteColor);
        save.putInt("displayMode", mode.ordinal());
        bundle.putBundle("DotProgressDrawable", save);
    }

    /** Restores a DotProgressDrawable state from a Bundle. */
    public void restore(Bundle bundle) {
        if (null == bundle) return;
        final Bundle save = bundle.getBundle("DotProgressDrawable");
        if (null != save) {
            if (save.containsKey("completeColor"))
                mCompleteColor = save.getInt("completeColor");
            if (save.containsKey("incompleteColor"))
                mIncompleteColor = save.getInt("incompleteColor");
            if (save.containsKey("displayMode"))
                mode = DisplayMode.values()[save.getInt("displayMode")];
        }
    }
}