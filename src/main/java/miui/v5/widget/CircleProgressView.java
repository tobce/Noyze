package miui.v5.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import me.barrasso.android.volume.R;

public class CircleProgressView extends View
{
    private static int MAX_PROGRESS = 100;
    private static String TAG = "CircleProgressView";
    private int mAngle;
    private RectF mArcRect;
    private int mCurProgress;
    private Bitmap mFgBitmap;
    private BitmapDrawable mForeground;
    private int mHeight;
    private int mMaxProgress = MAX_PROGRESS;
    private Bitmap mMemBitmap;
    private Canvas mMemCanvas;
    private Paint mPaint;
    private Paint mFgPaint;
    private int mWidth;

    public CircleProgressView(Context paramContext)
    {
        super(paramContext);
        init(paramContext, null);
    }

    public CircleProgressView(Context paramContext, AttributeSet paramAttributeSet)
    {
        super(paramContext, paramAttributeSet);
        init(paramContext, paramAttributeSet);
    }

    public CircleProgressView(Context paramContext, AttributeSet paramAttributeSet, int paramInt)
    {
        super(paramContext, paramAttributeSet, paramInt);
        init(paramContext, paramAttributeSet);
    }

    private void init(Context context, AttributeSet attrs)
    {
        if (null == attrs) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressView);
        Resources res = context.getResources();
        mForeground = ((BitmapDrawable) a.getDrawable(R.styleable.CircleProgressView_foreground));
        if (mForeground == null)
            mForeground = ((BitmapDrawable) res.getDrawable(R.drawable.v5_ic_audio_progress));
        mFgBitmap = mForeground.getBitmap();
        mWidth = mFgBitmap.getWidth();
        mHeight = mFgBitmap.getHeight();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mFgPaint.setAntiAlias(true);
        mPaint.setColor(0);
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mMemBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        mMemCanvas = new Canvas(mMemBitmap);
        mArcRect = new RectF(0.0F, 0.0F, mWidth, mHeight);
        a.recycle();
    }

    public void setColorFilter(ColorFilter filter) {
        mFgPaint.setColorFilter(filter);
    }

    public int getMax() {
        return this.mMaxProgress;
    }

    public int getProgress() {
        return this.mCurProgress;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        if (mForeground == null) {
            super.onDraw(canvas);
            return;
        }

        mMemBitmap.eraseColor(0);
        mMemCanvas.drawBitmap(mFgBitmap, 0.0F, 0.0F, mFgPaint);
        mMemCanvas.drawArc(mArcRect, 270 - mAngle, mAngle, true, mPaint);
        canvas.drawBitmap(mMemBitmap, 0.0F, 0.0F, null);
    }

    @Override
    protected void onMeasure(int paramInt1, int paramInt2) {
        setMeasuredDimension(mWidth, mHeight);
    }

    public void setMax(int max) {
        if ((max > 0) && (mMaxProgress != max)) {
            mMaxProgress = max;
            setProgress(mCurProgress);
        }
    }

    public void setProgress(int progress) {
        mCurProgress = progress;
        if (mCurProgress > mMaxProgress)
            mCurProgress = mMaxProgress;

        mCurProgress = (this.mMaxProgress - this.mCurProgress);
        int i = 360 * this.mCurProgress / this.mMaxProgress;
        if (i != this.mAngle) {
            Log.i(TAG, "progress:" + this.mCurProgress);
            this.mAngle = i;
            invalidate();
        }

        if (this.mCurProgress < 0)
            this.mCurProgress = 0;
    }
}