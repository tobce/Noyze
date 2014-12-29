package me.barrasso.android.volume.activities;

// Java Packages
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

// Android Packages
import android.app.ActionBar;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.app.Fragment;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jakewharton.salvage.RecyclingPagerAdapter;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.ui.TypefaceSpan;

import static me.barrasso.android.volume.LogUtils.LOGD;

/**
 * Horizontally scrolling, paginated list of pictures and descriptions
 * designed to assist the user in using this application. It uses
 * Jake Wharton's {@link RecyclingPagerAdapter} to be more efficient 
 * (recycle {@link View}s) and to avoid {@link Fragment} creation.
 */
public final class HelpActivity extends Activity
	implements ViewPager.OnPageChangeListener {

	public static final String TAG = HelpActivity.class.getSimpleName();
	private static final String KEY_POSITION = TAG + ":POSITION";

	private HelpFragmentAdapter mAdapter = null;
	private ViewPager mPager = null;
	private MenuItem mDoneItem = null;

    /** Makes an {@link Activity} a "popup" like a {@link Dialog}. */
    public static void popup(final Activity mAct) {
        if (null == mAct) return;
        final Resources aRes = mAct.getResources();
        if (null == aRes) return;

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) mAct.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        final int[] mWindowDims = new int[] { dm.widthPixels, dm.heightPixels };
        final int mMaxWidth = aRes.getDimensionPixelSize(R.dimen.max_menu_width),
                  mGutter = aRes.getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final boolean isBounded = (mWindowDims[0] > mMaxWidth);

        mAct.requestWindowFeature(Window.FEATURE_ACTION_BAR);
        mAct.requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        final Window mWindow = mAct.getWindow();
        mWindow.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        // Now get our attributes and make the window dim it's background.
        final WindowManager.LayoutParams params = mWindow.getAttributes();
        params.alpha = 1.0f;
        params.dimAmount = 0.5f;

        // Bound the height and barHeight for this overlay.
        if (isBounded) {
            params.width = mMaxWidth + (4 * mGutter);
            // Bound to the maximum of a square.
            if (mWindowDims[1] > params.width) {
                params.height = params.width;
            } else {
                params.height = (int) (0.81f * mWindowDims[1]);
            }
        }

        mWindow.setAttributes(params);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        popup(this);
        mPager = new ViewPager(this);
        mPager.setId(R.id.pager);
        setContentView(mPager);
        
        // Set up our ActionBar
        final ActionBar mAB = getActionBar();
        if (null != mAB) {
            // Create a Spannable for a custom font face in the title.
            SpannableString title = new SpannableString(getString(R.string.welcome_to));
            title.setSpan(new TypefaceSpan(this, "TimeBurner_Regular.ttf"), 0, title.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            mAB.setTitle(title);
            mAB.setDisplayShowTitleEnabled(true);
            mAB.setDisplayHomeAsUpEnabled(true);
        }

		// Set our Adapter for the Pager.
        mAdapter = new HelpFragmentAdapter(this);
        mPager.setAdapter(mAdapter);
        
        // We have to add the listener here, because CirclePageIndicator
        // uses the default listener to know when to change state.
        mPager.setOnPageChangeListener(this);
        
        // Save the last/ current item.
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_POSITION)) {
        	mPager.setCurrentItem(savedInstanceState.getInt(KEY_POSITION));
        }
    }
    
    @Override
    public void onPageScrollStateChanged(int state) { }
    
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }
    
    @Override
    public void onPageSelected(int position) {
    	LOGD(TAG, "position=" + String.valueOf(position) + '.');
    	setDoneVisible(position);
    }
    
    private void setDoneVisible(final int position) {
    	if (mDoneItem != null) {
    		mDoneItem.setVisible((mAdapter != null && position == (mAdapter.getCount()-1)));
    	}
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_POSITION, mPager.getCurrentItem());
        super.onSaveInstanceState(outState);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {    	
    	// Load our menu...
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.help, menu);
        mDoneItem = menu.findItem(R.id.menu_done);
        
        // If we have our ViewPager all set, update the "Done"
        // button based on its current position.
        if (mPager != null) {
        	setDoneVisible(mPager.getCurrentItem());
        }
        
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        	case R.id.menu_done:
        	case android.R.id.home: {
            	super.onBackPressed();
            	return true;
            }
        }
        
        return super.onOptionsItemSelected(item);
    }
	
	// Super-simple ViewHolder to store references as needed.
	private static final class ViewHolder {
		final TextView mText;

		public ViewHolder(View view) {
			mText = (TextView) view.findViewById(R.id.help_text);
		}
	}
    
    /**
     * {@link FragmentPagerAdapter} for {@link HelpFragment}s, to create an efficient,
     * paginated help interface.
     */
    private static final class HelpFragmentAdapter extends RecyclingPagerAdapter {
    
    	private final ArrayList<Entry<CharSequence, Integer>> mItems;
    	private final Context mContext;
		
		public HelpFragmentAdapter(Context mContext) {
			this.mContext = mContext;
			final Map<CharSequence, Integer> mMappedItems = new LinkedHashMap<CharSequence, Integer>();
			
			final Resources res = mContext.getResources();
			final CharSequence[] mDescriptions = res.getTextArray(0);//R.array.help_descriptions);
			final TypedArray mImages = res.obtainTypedArray(0);//R.array.help_images);

            // Add items to our HashMap.
            if (mDescriptions.length == mImages.length()) {
                for (int i = 0; i < mDescriptions.length; ++i) {
                    mMappedItems.put(mDescriptions[i], mImages.getResourceId(i, 0));
                }
            }
			
			mImages.recycle();
			mItems = (new ArrayList<Entry<CharSequence, Integer>>(mMappedItems.entrySet()));
		}
		
		@Override
		public View getView(int position, View view, ViewGroup container) {
			
			ViewHolder mHolder;
			if (view != null) {
				mHolder = (ViewHolder) view.getTag();
			} else {
				view = makeLayout();
				mHolder = new ViewHolder(view);
				view.setTag(mHolder);
			}
			
			// Get the key and value from our LinkedHashMap.
			final Entry<CharSequence, Integer> mEntry = mItems.get(position);
			final CharSequence mMessage = mEntry.getKey();
			final int mImageRes = mEntry.getValue();

			// Set the values from our ViewHolder.
			mHolder.mText.setText(((mMessage instanceof String) ? Html.fromHtml((String) mMessage) : mMessage));
			if (mImageRes != R.id.help_text && mImageRes != 0) {
				mHolder.mText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, mImageRes);
			}

			return view;
		}
		
		/**
		 * Generates our layout in-code. Only called once, then
		 * we'll be sure to recycle these {@link View}s.
		 */
		public final View makeLayout() {
		
			// Layout Parameters.
            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(dm);
            final int[] mWindowDims = new int[] { dm.widthPixels, dm.heightPixels };
			final int mWindowWidth = mWindowDims[0],
					  mMaxWidth = mContext.getResources().getDimensionPixelSize(R.dimen.max_menu_width);
			final int gutter = mContext.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
			final FrameLayout.LayoutParams mParams = new FrameLayout.LayoutParams(
				((mWindowWidth > mMaxWidth) ? mMaxWidth : android.view.ViewGroup.LayoutParams.MATCH_PARENT),
				android.view.ViewGroup.LayoutParams.MATCH_PARENT);
			final RelativeLayout.LayoutParams mTextParams = new RelativeLayout.LayoutParams(
				android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			mTextParams.addRule(RelativeLayout.CENTER_IN_PARENT);
			mParams.gravity = Gravity.CENTER;
			
			// Main text and image.
			final TextView text = new TextView(mContext);
            text.setTextColor(Color.DKGRAY);
			text.setId(R.id.help_text);
			text.setLayoutParams(mTextParams);
			text.setGravity(Gravity.CENTER_HORIZONTAL);
			text.setMovementMethod(LinkMovementMethod.getInstance());
			text.setLinksClickable(true);
			final int mTextSize = mContext.getResources()
				.getDimensionPixelSize(R.dimen.help_text_size);
			text.setTextSize(mTextSize);
			
			text.setCompoundDrawablePadding((gutter/2));
			text.setPadding(gutter, gutter, gutter, gutter);
	
			// Allow the View to Scroll vertically if necessary.
			final ScrollView scroll = new ScrollView(mContext);
			scroll.setLayoutParams(mParams);
			scroll.setFillViewport(true);
			scroll.setSmoothScrollingEnabled(true);
			scroll.setVerticalScrollBarEnabled(false);
			final RelativeLayout layout = new RelativeLayout(mContext);
			mParams.topMargin = mParams.bottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin);
            mParams.leftMargin = mParams.rightMargin = mContext.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
			layout.setLayoutParams(mParams);
			layout.addView(text);
			scroll.addView(layout);
			
			return scroll;
		}
	
		@Override
		public int getCount() {
			return mItems.size();
		}
    }
}