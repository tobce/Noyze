package me.barrasso.android.volume.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;

public final class MaxWidthLinearLayout extends LinearLayout {

    private int mMaxWidth = Integer.MIN_VALUE;

    public MaxWidthLinearLayout(Context context) {
        this(context, null);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs, int theme) {
        super(context, attrs);

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout);
        mMaxWidth = a.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_maxWidth, mMaxWidth);
        a.recycle();
    }

    public void setMaxWidth(int width) {
        LogUtils.LOGI("MaxWidthLinearLayout", "setMaxWidth(" + width + ')');
        mMaxWidth = width;
        requestLayout();
    }

    public int getMaxWidth() {
        return mMaxWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (mMaxWidth > 0 && mMaxWidth < measuredWidth) {
            int measureMode = MeasureSpec.getMode(widthMeasureSpec);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidth, measureMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}