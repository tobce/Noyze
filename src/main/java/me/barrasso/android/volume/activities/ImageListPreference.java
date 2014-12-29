package me.barrasso.android.volume.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;

/**
 * {@link android.preference.ListPreference} but with the the following additions:
 * <br />
 * <ul>
 *     <li>It supports an images (centered, top) that's full width.</li>
 *     <li>It supports a "pro" banner to inform the user of an IAP.</li>
 * </ul>
 */
public class ImageListPreference extends ListPreference implements View.OnClickListener {

    protected int mLayout;
    protected int[] mImageResources;

    public ImageListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ImageListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ImageListPreference, defStyle, 0);
        try {
            Resources res = getContext().getResources();
            int images = a.getResourceId(R.styleable.ImageListPreference_pics, 0);
            if (images <= 0) throw new IllegalArgumentException(getClass().getSimpleName() + " must set `pics` attribute.");

            TypedArray imageRes = res.obtainTypedArray(images);
            int[] imageResIds = new int[imageRes.length()];
            for (int i = 0; i < imageResIds.length; ++i)
                imageResIds[i] = imageRes.getResourceId(i, 0);
            imageRes.recycle();
            mImageResources = imageResIds;

            int layout = a.getResourceId(R.styleable.ImageListPreference_listItemLayout, 0);
            if (layout <= 0) throw new IllegalArgumentException(getClass().getSimpleName() + " must set `layout` attribute.");
            mLayout = layout;
        } finally {
            a.recycle();
        }
    }

    /**
     * @return The list of Google Play In-App Billing SKUs.
     */
    public CharSequence[] getSkus() {
        // TODO: update this method as new themes are added (locked via IN-APP Purchases).
        CharSequence[] skus = new CharSequence[mImageResources.length];
        return skus;
    }

    protected List<Pair<CharSequence, Integer>> makePairList() {
        List<Pair<CharSequence, Integer>> list = new ArrayList<Pair<CharSequence, Integer>>();
        CharSequence[] entries = getEntries();
        if (entries.length != mImageResources.length)
            throw new IllegalArgumentException(getClass().getSimpleName() +
                    " must have an equal number of `images` and `entries`.");
        for (int i = 0; i < entries.length; ++i) {
            list.add(new Pair<CharSequence, Integer>(entries[i], mImageResources[i]));
        }
        return list;
    }

    ImageListPreferenceScreenAdapter adapter;

    @Override
    protected void onBindDialogView(View view) {
        ListView list = (ListView) view.findViewById(android.R.id.list);
        int selection = getSelection();
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setItemChecked(selection, true);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        LogUtils.LOGI(getClass().getSimpleName(), "onPrepareDialogBuilder()");
        // Accountant.getInstance(getContext()).connect();

        adapter = new ImageListPreferenceScreenAdapter(
                getContext(), mLayout, makePairList());
        adapter.setOnClickListener(this);
        adapter.setSkus(getSkus());

        int selectedEntry = getSelection();
        adapter.setSelected(selectedEntry);
        builder.setAdapter(adapter, null);
    }

    protected int getSelection() {
        int selectedEntry = 0;
        String selectedValue = getValue();
        if (TextUtils.isEmpty(selectedValue)) return 0;
        CharSequence[] entries = getEntryValues();
        for (int i = 0; i < entries.length; ++i) {
            if (selectedValue.compareTo((String) entries[i]) == 0) {
                selectedEntry = i;
                break;
            }
        }
        return selectedEntry;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        LogUtils.LOGI(getClass().getSimpleName(), "onDialogClosed(" + positiveResult + ')');
        if (null != adapter) adapter.destroy();
    }

    @Override
    public void onClick(View v) {
        Dialog mDialog = getDialog();
        mDialog.dismiss();

        ViewHolder holder = (ViewHolder) v.getTag();
        // CharSequence[] skus = getSkus();
        // CharSequence sku = skus[holder.position];
        CharSequence value = getEntryValues()[holder.position];

        //if (TextUtils.isEmpty(sku)) {
        //    LogUtils.LOGI(getClass().getSimpleName(), "setValue(" + value + ')');
            setValue(value.toString());
        /*} else {
            LogUtils.LOGI(getClass().getSimpleName(), "Item " +
                    holder.position + " has sku: " + sku);
            Accountant mAccountant = Accountant.getInstance(getContext());
            if (!mAccountant.inAppPurchasesSupported()) {
                LogUtils.LOGI(getClass().getSimpleName(), "In-app purchases not supported.");
                Crouton.showText((Activity) getContext(), R.string.inapp_error, Style.ALERT);
            } else {
                if (mAccountant.has(sku.toString())) {
                    LogUtils.LOGI(getClass().getSimpleName(), "Already owns: " + sku);
                    setValue(value.toString());
                } else {
                    LogUtils.LOGI(getClass().getSimpleName(), "Buy in-app: " + sku);
                    mAccountant.buy((Activity) getContext(), sku.toString());
                }
            }
        }*/
    }

    static class ViewHolder {
        TextView title;
        ImageView icon, banner;
        int position;

        ViewHolder(View row, int position) {
            title = (TextView) row.findViewById(android.R.id.title);
            icon = (ImageView) row.findViewById(android.R.id.icon);
            banner = (ImageView) row.findViewById(R.id.pro_ribbon);
            this.position = position;
        }
    }

    protected static class ImageListPreferenceScreenAdapter
            extends ArrayAdapter<Pair<CharSequence, Integer>> {

        private final int layout;
        private final LruCache<Integer, Drawable> cache;
        private final CharSequence[] skus;

        private View.OnClickListener listener;
        private int selected;

        public ImageListPreferenceScreenAdapter(Context context,
                                                int layout, List<Pair<CharSequence, Integer>> items) {
            super(context, layout, items);
            skus = new CharSequence[items.size()];
            cache = new LruCache<Integer, Drawable>(items.size());
            this.layout = layout;
            preload();
        }

        protected void preload() {
            Resources res = getContext().getResources();
            for (int i = 0, e = getCount(); i < e; ++i) {
                Pair<CharSequence, Integer> item = getItem(i);
                cache.put(item.second, res.getDrawable(item.second));
            }
        }

        public void setOnClickListener(View.OnClickListener listener) {
            this.listener = listener;
        }

        protected boolean[] ownedSkus;

        public void setSkus(CharSequence[] newSkus) {
            if (null == newSkus || newSkus.length != skus.length) return;
            ownedSkus = new boolean[newSkus.length];
            for (int i = 0; i < newSkus.length; ++i)
                skus[i] = newSkus[i];
        }

        public void setSelected(int selected) {
            this.selected = selected;
        }

        public void destroy() {
            cache.evictAll();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;
            ViewHolder holder;
            if (null == convertView) {
                row = View.inflate(getContext(), layout, null);
                holder = new ViewHolder(row, position);
                row.setTag(holder);
            } else {
                row = convertView;
                holder = (ViewHolder) row.getTag();
            }

            row.setOnClickListener(listener);

            Resources res = getContext().getResources();
            Pair<CharSequence, Integer> item = getItem(position);
            holder.position = position;
            holder.title.setText(item.first);
            Drawable pic = cache.get(item.second);
            if (null == pic) {
                pic = res.getDrawable(item.second);
                cache.put(item.second, pic);
            }
            holder.icon.setImageDrawable(pic);
            synchronized (skus) {
                CharSequence sku = skus[position];
                boolean hasSku = (null != sku && !TextUtils.isEmpty(sku));
                holder.banner.setVisibility((hasSku) ? View.VISIBLE : View.GONE);
                if (hasSku && ownedSkus[position]) {
                    holder.banner.setVisibility(View.GONE);
                }
            }

            if (position == selected) {
                row.setClickable(false);
                row.setFocusable(false);
            }

            return row;
        }
    }
}