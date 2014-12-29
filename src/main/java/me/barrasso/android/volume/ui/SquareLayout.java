package me.barrasso.android.volume.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SquareLayout extends LinearLayout {

    public SquareLayout(Context context) {
        super(context);
    }
    public SquareLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public SquareLayout(Context context, AttributeSet attrs, int theme) {
        super(context, attrs, theme);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.max(width, height);
        setMeasuredDimension(size, size);
    }
}