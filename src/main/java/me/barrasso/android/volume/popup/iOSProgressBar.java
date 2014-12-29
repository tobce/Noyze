package me.barrasso.android.volume.popup;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.ui.DotProgressDrawable;

/**
 * Simple {@link android.widget.ProgressBar} that uses a {@link me.barrasso.android.volume.ui.DotProgressDrawable}
 * to display the style of the progress. This effect cannot otherwise be accomplished in code.
 */
public final class iOSProgressBar extends ProgressBar {

    private final DotProgressDrawable progressDrawable = new DotProgressDrawable();

    public iOSProgressBar(Context context) {
        this(context, null);
    }

    public iOSProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public iOSProgressBar(Context context, AttributeSet attrs, int theme) {
        super(context, attrs, theme);
        setAttrs(attrs);
    }

    private void setAttrs(AttributeSet attrs) {
        int mCompleteColor = Color.WHITE;
        int mIncompleteColor = Color.BLACK;

        if (null != attrs) {
            TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs,
                    R.styleable.iOSProgressBar, 0, 0);

            try {
                mCompleteColor = a.getColor(R.styleable.iOSProgressBar_completeColor, mCompleteColor);
                mIncompleteColor = a.getColor(R.styleable.iOSProgressBar_incompleteColor, mIncompleteColor);
                final int displayMode = a.getInt(R.styleable.iOSProgressBar_displayMode, DotProgressDrawable.DisplayMode.SQUARES.ordinal());
                DotProgressDrawable.DisplayMode[] modes = DotProgressDrawable.DisplayMode.values();
                if (displayMode >= 0 && displayMode < modes.length)
                    setDisplayMode(modes[displayMode]);
            } finally {
                a.recycle();
            }
        }

        progressDrawable.setCompleteColor(mCompleteColor);
        progressDrawable.setIncompleteColor(mIncompleteColor);
        setProgressDrawable(progressDrawable);
    }

    protected void setIncompleteColor(int color) {
        progressDrawable.setIncompleteColor(color);
        progressDrawable.invalidateSelf();
        invalidate();
    }

    protected void setCompleteColor(int color) {
        progressDrawable.setCompleteColor(color);
        progressDrawable.invalidateSelf();
        invalidate();
    }

    public void setProgress(final int index, final int max) {
        progressDrawable.setProgress(index, max);
        setProgress((100 * index) / max);
        invalidate();
    }

    public void setDisplayMode(DotProgressDrawable.DisplayMode mode) {
        progressDrawable.setDisplayMode(mode);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable("superstate", super.onSaveInstanceState());
        progressDrawable.save(state);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable ss) {
        final Bundle state = (Bundle) ss;
        super.onRestoreInstanceState(state.getParcelable("superstate"));
        progressDrawable.restore(state);
    }
}