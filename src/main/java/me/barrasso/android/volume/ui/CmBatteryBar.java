package me.barrasso.android.volume.ui;

// Android Packages
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.view.Gravity;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Originally used to display a MIUI-style battery bar. Now it's
 * just a VERY simple {@link android.widget.ProgressBar} that shows
 * a solid color and respects right-to-left layouts.
 */
public final class CmBatteryBar extends ProgressBar
{
    private static final String SUPERSTATE = "superstate",
    							COLOR = "color";

    /**
     * Default color of a {@link CmBatteryBar}, #FF33B5E5.
     */
    public static final int DEFAULT_COLOR = Color.argb(0xFF, 0x33, 0xB5, 0xE5);

    // Color of the bar. #FF33B5E5 default
    private int color = DEFAULT_COLOR;

    public CmBatteryBar(Context context) {
        this(context, null);
        init();
    }

    public CmBatteryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public CmBatteryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init() { }

    private ShapeDrawable mSecondaryProgress;
    private ShapeDrawable mProgress;
    private ShapeDrawable mBackground;
        
    /**
     * Creates a {@link Drawable} of a solid color.
     */
    private LayerDrawable getDrawableForColor(int color)
    {
    	if (mBackground == null)
    		mBackground = new ShapeDrawable(new RectShape());
    	if (mProgress == null)
			mProgress = new ShapeDrawable(new RectShape());
        if (mSecondaryProgress == null)
            mSecondaryProgress = new ShapeDrawable(new RectShape());
			
		// Set the colors based on whether or not we are "inverse".
    	mProgress.getPaint().setColor(color);
    	mBackground.getPaint().setColor(Color.TRANSPARENT);
        mSecondaryProgress.getPaint().setColor(Color.TRANSPARENT);

    	final ClipDrawable mProgClip = new ClipDrawable(
    		mProgress, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        final ClipDrawable mSecClip = new ClipDrawable(
            mSecondaryProgress, Gravity.LEFT, ClipDrawable.HORIZONTAL);
    	
    	final LayerDrawable mLayers = new LayerDrawable(
    		new Drawable[] { mBackground, mSecClip, mProgClip });
    	
    	// Do funky layered crap.
    	mLayers.setDrawableByLayerId(android.R.id.background, mBackground);
        mLayers.setDrawableByLayerId(android.R.id.secondaryProgress, mSecClip);
    	mLayers.setDrawableByLayerId(android.R.id.progress, mProgClip);
    	mLayers.setId(0, android.R.id.background);
        mLayers.setId(1, android.R.id.secondaryProgress);
    	mLayers.setId(2, android.R.id.progress);

    	return mLayers;
    }

    public final boolean isInverse() { return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    /**
     * Sets the color of the battery bar.
     */
    public final void setColor(int mColor) {
    	this.color = mColor;
    	setProgressDrawable(getDrawableForColor(mColor), mColor);
        invalidate();
    }

    @Override
    public void setProgress(int progress) {
        super.setProgress(progress);
        invalidate();
    }

    /**
     * Updates the {@link ShapeDrawable} which displays the
     * color of the bar across the screen.
     */
    public void setProgressDrawable(Drawable mDrawable, int mNewColor)
    {
    	if (mDrawable instanceof LayerDrawable &&
    		getProgressDrawable() instanceof LayerDrawable)
    	{
            final LayerDrawable mDraw = (LayerDrawable) getProgressDrawable();
            final ClipDrawable mShape = (ClipDrawable)
                mDraw.findDrawableByLayerId(android.R.id.progress);

            // Make sure we've got everything.
            if (mShape != null && mProgress != null &&
                mProgress.getPaint() != null)
            {
                mProgress.getPaint().setColor(mNewColor);
                final Rect mBounds = mDraw.getBounds();
                super.setProgressDrawable(mDraw);
                getProgressDrawable().setBounds(mBounds);
                return;
            }
    	}
    	
    	super.setProgressDrawable(mDrawable);
    }
    
    /**
     * @return The color of the battery bar.
     */
    public final int getColor() {
    	return this.color;
    }

    
    // Custom onDraw to handle inverse stuff!
    @Override
	protected synchronized void onDraw(Canvas canvas) {
        if (getVisibility() != View.VISIBLE || getProgress() <= 0) return;
		if (!isInverse()) {
			super.onDraw(canvas); 
		} else {
			int count = canvas.save();
						
			// Set the canvas to the middle of the screen.
			// The calculation is the center (px) minus half
			// of the barHeight of the actual visible bar.
			// if (mCentered)
			// 	canvas.translate((getWidth() / 2.0f) - ((getProgress()
			// 		/ (getMax() * 2.0f)) * getWidth()), 0);
			// Now we change the matrix.
			// We need to rotate around the center of our text.
			// Otherwise it rotates around the origin, and that's bad. 
			if (isInverse())
				canvas.rotate(180, (getWidth() / 2.0f), (getHeight() / 2.0f));			 
			
			// Draw the progress bar with the matrix applied. 
			super.onDraw(canvas); 
			
			// Restore the old matrix. 
			canvas.restoreToCount(count);
		}
	}
    
    // ====================
	//     Saved State
	// ====================
		
	@Override
	public Parcelable onSaveInstanceState() {
		final Bundle state = new Bundle();
		state.putParcelable(SUPERSTATE, super.onSaveInstanceState());
		state.putInt(COLOR, color);
		return(state);
	}

	@Override
	public void onRestoreInstanceState(Parcelable ss) {
		final Bundle state = (Bundle) ss;
		super.onRestoreInstanceState(state.getParcelable(SUPERSTATE));
		int color = state.getInt(COLOR);
		setColor(color);
	}
}