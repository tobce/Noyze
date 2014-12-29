package me.barrasso.android.volume.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import me.barrasso.android.volume.R;

public class BackgroundLinearLayout extends LinearLayout {

    /*package*/ int backgroundColor;
    /*package*/ Drawable mCustomBackground;

    // This is a faster way to draw the background on devices without hardware acceleration
    /*package*/ final Drawable mBackgroundDrawable = new Drawable() {
        @Override
        public void draw(Canvas canvas) {
            if (mCustomBackground != null) {
                final Rect bounds = mCustomBackground.getBounds();
                final int vWidth = getWidth();
                final int vHeight = getHeight();

                final int restore = canvas.save();
                canvas.translate(-(bounds.width() - vWidth) / 2,
                        -(bounds.height() - vHeight) / 2);
                mCustomBackground.draw(canvas);
                canvas.restoreToCount(restore);
            } else {
                canvas.drawColor(backgroundColor, PorterDuff.Mode.SRC);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    };

    public BackgroundLinearLayout(Context context) { super(context); init(); }
    public BackgroundLinearLayout(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BackgroundLinearLayout(Context context, AttributeSet attrs, int theme) {
        super(context, attrs, theme);
        init();
    }

    protected void init() {
        backgroundColor = getResources().getColor(R.color.windows_phone_theme_dark);
        setBackground(mBackgroundDrawable);
    }

    public void setCustomBackgroundColor(int color) {
        backgroundColor = color;
    }

    public void setCustomBackground(Drawable d) {
        mCustomBackground = d;
        if (d != null && d instanceof BitmapDrawable) {
            // This is to add a tint of the background color to the image.
            // It prevents overly exposed or bright backgrounds from ruining the ambiance.
            BitmapDrawable bd = (BitmapDrawable) d;
            bd.getPaint().setColor(backgroundColor);
            ColorFilter filter = new LightingColorFilter(backgroundColor, 1);
            bd.setColorFilter(filter);
        }
        setBackground(mBackgroundDrawable);
        computeCustomBackgroundBounds();
        invalidate();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void computeCustomBackgroundBounds() {
        if (mCustomBackground == null) return; // Nothing to do
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            if (!isLaidOut()) return;

        final int bgWidth = mCustomBackground.getIntrinsicWidth();
        final int bgHeight = mCustomBackground.getIntrinsicHeight();
        final int vWidth = getWidth();
        final int vHeight = getHeight();

        final float bgAspect = (float) bgWidth / bgHeight;
        final float vAspect = (float) vWidth / vHeight;

        if (bgAspect > vAspect) {
            mCustomBackground.setBounds(0, 0, (int) (vHeight * bgAspect), vHeight);
        } else {
            mCustomBackground.setBounds(0, 0, vWidth, (int) (vWidth / bgAspect));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeCustomBackgroundBounds();
    }
}