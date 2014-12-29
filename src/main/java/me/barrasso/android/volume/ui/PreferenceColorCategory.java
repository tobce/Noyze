package me.barrasso.android.volume.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import me.barrasso.android.volume.R;

public class PreferenceColorCategory extends PreferenceCategory {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PreferenceColorCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        handleAttributes(attrs, defStyleAttr, defStyleRes);
    }

    public PreferenceColorCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        handleAttributes(attrs, defStyleAttr, 0);
    }

    public PreferenceColorCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        handleAttributes(attrs, 0, 0);
    }

    public PreferenceColorCategory(Context context) {
        super(context);
    }

    protected boolean allCaps = false;
    protected int titleColor = Color.BLACK;

    protected void handleAttributes(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.PreferenceColorCategory, defStyleAttr, defStyleRes);

        try {
            titleColor = a.getColor(R.styleable.PreferenceColorCategory_textColor, titleColor);
            allCaps = a.getBoolean(R.styleable.PreferenceColorCategory_textInAllCaps, allCaps);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView titleView = (TextView) view.findViewById(android.R.id.title);
        titleView.setAllCaps(allCaps);
        titleView.setTextColor(titleColor);
    }
}