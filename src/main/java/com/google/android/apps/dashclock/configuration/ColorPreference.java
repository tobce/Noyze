/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.configuration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.colorpicker.HsvColorComparator;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SVBar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;

/**
 * A preference that allows the user to choose an application or shortcut.
 */
public class ColorPreference extends Preference {
    private int[] mColorChoices = {};
    private int mValue = 0;
    private int mItemLayoutId = R.layout.grid_item_color;
    private int mNumColumns = 5;
    private View mPreviewView;
    
    private static final int BRIGHTNESS_THRESHOLD = 150;

    public static boolean isColorDark(int color) {
        return ((30 * Color.red(color) +
                59 * Color.green(color) +
                11 * Color.blue(color)) / 100) <= BRIGHTNESS_THRESHOLD;
    }

    public ColorPreference(Context context) {
        super(context);
        initAttrs(null, 0);
    }

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs, 0);
    }

    public ColorPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(attrs, defStyle);
    }

    private void initAttrs(AttributeSet attrs, int defStyle) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.ColorPreference, defStyle, defStyle);

        try {
            mItemLayoutId = a.getResourceId(R.styleable.ColorPreference_itemLayout, mItemLayoutId);
            mNumColumns = a.getInteger(R.styleable.ColorPreference_numColumns, mNumColumns);
            int choicesResId = a.getResourceId(R.styleable.ColorPreference_choices,
                    R.array.default_color_choice_values);
            if (choicesResId > 0) {
                String[] choices = a.getResources().getStringArray(choicesResId);
                mColorChoices = new int[choices.length];
                for (int i = 0; i < choices.length; i++) {
                    mColorChoices[i] = Color.parseColor(choices[i]);
                }
            }

        } finally {
            a.recycle();
        }

        setWidgetLayoutResource(mItemLayoutId);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mPreviewView = view.findViewById(R.id.color_view);
        setColorViewValue(mPreviewView, mValue, false, false);
    }

    public void setValue(int value) {
        if (callChangeListener(value)) {
            mValue = value;
            persistInt(value);
            notifyChanged();
        }
    }

    @Override
    protected void onClick() {
        super.onClick();

        ColorDialogFragment fragment = ColorDialogFragment.newInstance();
        fragment.setPreference(this);

        Activity activity = (Activity) getContext();
        activity.getFragmentManager().beginTransaction()
                .add(fragment, getFragmentTag())
                .commit();
    }

    /*package*/ void onAdvancedClick() {
        AdvancedDialogFragment fragment = AdvancedDialogFragment.newInstance(getValue());
        fragment.setPreference(this);

        Activity activity = (Activity) getContext();
        activity.getFragmentManager().beginTransaction()
                .add(fragment, AdvancedDialogFragment.class.getSimpleName())
                .commit();
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

        Activity activity = (Activity) getContext();
        ColorDialogFragment fragment = (ColorDialogFragment) activity
                .getFragmentManager().findFragmentByTag(getFragmentTag());
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this);
        }

        AdvancedDialogFragment fragment1 = (AdvancedDialogFragment) activity
                .getFragmentManager().findFragmentByTag(AdvancedDialogFragment.class.getSimpleName());
        if (fragment1 != null) {
            // re-bind preference to fragment
            fragment1.setPreference(this);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedInt(0) : (Integer) defaultValue);
    }

    public String getFragmentTag() {
        return "color_" + getKey();
    }

    public int getValue() {
        return mValue;
    }

    public static class AdvancedDialogFragment extends DialogFragment {

        static InputFilter ALPHANUMERIC = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend) {
                if (source instanceof SpannableStringBuilder) {
                    SpannableStringBuilder sourceAsSpannableBuilder = (SpannableStringBuilder)source;
                    for (int i = end - 1; i >= start; i--) {
                        char currentChar = source.charAt(i);
                        currentChar = Character.toUpperCase(currentChar);
                        if (!Character.isLetterOrDigit(currentChar) && !Character.isSpaceChar(currentChar)) {
                            sourceAsSpannableBuilder.delete(i, i+1);
                        }
                    }
                    return source;
                } else {
                    StringBuilder filteredStringBuilder = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char currentChar = source.charAt(i);
                        currentChar = Character.toUpperCase(currentChar);
                        if (Character.isLetterOrDigit(currentChar) || Character.isSpaceChar(currentChar)) {
                            filteredStringBuilder.append(currentChar);
                        }
                    }
                    return filteredStringBuilder.toString();
                }
            }
        };

        public AdvancedDialogFragment() {
            Bundle args = getArguments();
            String key = Color.class.getSimpleName();
            if (null != args) {
                startColor = args.getInt(key, startColor);
            }
        }

        private int startColor = Color.CYAN;
        private ViewGroup mRootView;
        private ColorPreference mPreference;

        public static AdvancedDialogFragment newInstance(int initialColor) {
            AdvancedDialogFragment fragment = new AdvancedDialogFragment();
            Bundle save = new Bundle();
            save.putInt(Color.class.getSimpleName(), initialColor);
            fragment.setArguments(save);
            fragment.startColor = initialColor;
            return fragment;
        }

        public void setPreference(ColorPreference preference) {
            mPreference = preference;
        }

        /*package*/ DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int what) {
                switch (what) {
                    case DialogInterface.BUTTON_POSITIVE:
                        setValue(getValue());
                        dismiss();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        dismiss();
                        break;
                }
            }
        };

        private static boolean isHexNumber(String cadena) {
            try {
                Long.parseLong(cadena, 16);
                return true;
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        // Apply the hex color when we've been told to do so.
        /*package*/ View.OnClickListener hexApplyListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String hex = String.valueOf(hexEdit.getText());
                if (!TextUtils.isEmpty(hex)) {
                    int len = hex.length();
                    if ((len == 6 || len  == 8) && isHexNumber(hex)) {
                        LogUtils.LOGI("ColorPreference", "setColor(#" + hex + ')');
                        int color = Color.parseColor("#" + hex);
                        mColorPicker.setColor(color);
                        setValue(color);
                        dismiss();
                    }
                }
            }
        };

        /*package*/ int getValue() {
            return mColorPicker.getColor();
        }

        /*package*/ void setValue(int value) {
            mPreference.setValue(value);
        }

        private Button hexApply;
        private EditText hexEdit;
        private ColorPicker mColorPicker;
        private SVBar mSvBar;
        private OpacityBar mOpacityBar;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            mRootView = (ViewGroup) layoutInflater.inflate(R.layout.advanced_color_picker, null);
            mColorPicker = (ColorPicker) mRootView.findViewById(R.id.color_picker);
            mSvBar = (SVBar) mRootView.findViewById(R.id.svbar);
            mOpacityBar = (OpacityBar) mRootView.findViewById(R.id.opacitybar);
            mColorPicker.addSVBar(mSvBar);
            hexEdit = (EditText) mRootView.findViewById(R.id.hex);
            hexApply = (Button) mRootView.findViewById(R.id.apply);
            hexEdit.setFilters(new InputFilter[] { ALPHANUMERIC });
            hexApply.setOnClickListener(hexApplyListener);
            String hexColor = String.format("%06X", (0xFFFFFF &
                    ColorDialogFragment.getColor(startColor, 255)));
            hexEdit.setHint(hexColor);
            mColorPicker.addOpacityBar(mOpacityBar);
            mColorPicker.setOldCenterColor(startColor);
            mColorPicker.setShowOldCenterColor(true);
            return new AlertDialog.Builder(getActivity())
                    .setView(mRootView)
                    .setNegativeButton(android.R.string.cancel, clickListener)
                    .setPositiveButton(android.R.string.ok, clickListener)
                    .create();
        }
    }

    public static class ColorDialogFragment extends DialogFragment {

        private ViewGroup mRootView;
        private ColorPreference mPreference;
        private GridLayout mColorGrid;
        private SeekBar mAlphaSeekBar;

        public ColorDialogFragment() {
        }

        public static ColorDialogFragment newInstance() {
            return new ColorDialogFragment();
        }

        public void setPreference(ColorPreference preference) {
            mPreference = preference;
            repopulateItems();
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            repopulateItems();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            mRootView = (ViewGroup) layoutInflater.inflate(R.layout.dialog_colors, null);

            mAlphaSeekBar = (SeekBar) mRootView.findViewById(android.R.id.progress);
            mAlphaSeekBar.setOnSeekBarChangeListener(alphaSeekListener);
            mAlphaSeekBar.setMax(255);
            mAlphaSeekBar.setProgress(getValueAlpha(false));
            mColorGrid = (GridLayout) mRootView.findViewById(R.id.color_grid);
            mColorGrid.setColumnCount(mPreference.mNumColumns);
            repopulateItems();

            return new AlertDialog.Builder(getActivity())
                    .setView(mRootView)
                    .setNegativeButton(android.R.string.cancel, clickListener)
                    .setPositiveButton(android.R.string.ok, clickListener)
                    .create();
        }

        /*package*/ DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int what) {
                switch (what) {
                    case DialogInterface.BUTTON_POSITIVE:
                        setValue(getValue());
                        dismiss();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        dismiss();
                        break;
                }
            }
        };

        /*package*/ SeekBar.OnSeekBarChangeListener alphaSeekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updateColors();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        public int getValueAlpha() {
            return getValueAlpha(true);
        }

        public int getValueAlpha(boolean filter) {
            int value = mPreference.getValue();
            if (filter && value == 0) return 255;
            return Color.alpha(value);
        }

        public int getValueWithAlpha() {
            int color = mPreference.getValue();
            return getColor(color, getAlpha());
        }

        public static int getColor(int color, int alpha) {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        public int getAlpha() {
            return mAlphaSeekBar.getProgress();
        }

        private void repopulateItems() {
            if (mPreference == null || mColorGrid == null) {
                return;
            }

            Context context = mColorGrid.getContext();
            mColorGrid.removeAllViews();

            // Sort all of our colors by HSV so there's some kind of order.
            List<Integer> colors = new ArrayList<Integer>();
            for (int index = 0; index < mPreference.mColorChoices.length; index++)
                colors.add(mPreference.mColorChoices[index]);
            Collections.sort(colors, new HsvColorComparator());
            colors.add(Color.TRANSPARENT);

            int index = 0;
            for (final int color : colors) {
                View itemView = LayoutInflater.from(context)
                            .inflate(R.layout.grid_item_color, mColorGrid, false);

                boolean advnaced = (index == mPreference.mColorChoices.length);
                setColorViewValue(itemView.findViewById(R.id.color_view), getColor(color, getValueAlpha(false)),
                        color == getValue(), advnaced);
                itemView.setClickable(true);
                itemView.setFocusable(true);
                itemView.setTag(color);
                if (advnaced) {
                    itemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            onAdvancedClick();
                        }
                    });
                } else {
                    itemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            setValue(color);
                            dismiss();
                        }
                    });
                }

                mColorGrid.addView(itemView);
                ++index;
            }

            sizeDialog();
        }

        protected void updateColors() {
            for (int i = 0, e = mColorGrid.getChildCount(); i < e; ++i) {
                View child = mColorGrid.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer) {
                    Integer color = (Integer) tag;
                    setColorViewValue(child.findViewById(R.id.color_view), getColor(color, getAlpha()),
                            color == getValue(), (i == (e - 1)));
                }
            }
        }

        protected int getValue() {
            int color = mPreference.getValue();
            return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
        }

        void onAdvancedClick() {
            dismiss();
            mPreference.onAdvancedClick();
        }

        protected void setValue(int color) {
            final int alpha = getAlpha();
            final int value;
            if (alpha == 255) {
                value = color;
            } else if (alpha == 0) {
                value = Color.TRANSPARENT;
            } else {
                value = Color.argb(getAlpha(), Color.red(color),
                        Color.green(color), Color.blue(color));
            }
            mPreference.setValue(value);
        }

        @Override
        public void onStart() {
            super.onStart();
            sizeDialog();
        }

        private void sizeDialog() {
            if (mPreference == null || mColorGrid == null) {
                return;
            }

            Dialog dialog = getDialog();
            if (dialog == null) {
                return;
            }

            final Resources res = mRootView.getContext().getResources();
            DisplayMetrics dm = res.getDisplayMetrics();

            // Can't use Integer.MAX_VALUE here (weird issue observed otherwise on 4.2)
            mRootView.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
            int width = mRootView.getMeasuredWidth();
            int height = mRootView.getMeasuredHeight();

            int extraPadding = res.getDimensionPixelSize(R.dimen.color_grid_extra_padding);

            width += extraPadding;
            height += extraPadding;

            dialog.getWindow().setLayout(width, height);
        }
    }

    private static void setColorViewValue(View view, int color, boolean selected, boolean isAdvancedPicker) {
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            Resources res = imageView.getContext().getResources();

            if (isAdvancedPicker) {
                color = Color.TRANSPARENT;
                selected = false;
            }

            Drawable currentDrawable = imageView.getDrawable();
            GradientDrawable colorChoiceDrawable;
            if (currentDrawable != null && currentDrawable instanceof GradientDrawable) {
                // Reuse drawable
                colorChoiceDrawable = (GradientDrawable) currentDrawable;
            } else {
                colorChoiceDrawable = new GradientDrawable();
                colorChoiceDrawable.setShape(GradientDrawable.OVAL);
            }

            // Set stroke to dark version of color
            int darkenedColor = Color.rgb(
                    Color.red(color) * 192 / 256,
                    Color.green(color) * 192 / 256,
                    Color.blue(color) * 192 / 256);
            if (isAdvancedPicker) darkenedColor = Color.TRANSPARENT;

            colorChoiceDrawable.setColor(color);
            colorChoiceDrawable.setStroke((int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics()), darkenedColor);

            Drawable drawable = colorChoiceDrawable;
            if (selected) {
                drawable = new LayerDrawable(new Drawable[] {
                        colorChoiceDrawable,
                        res.getDrawable(isColorDark(color)
                                ? R.drawable.checkmark_white
                                : R.drawable.checkmark_black)
                });
            } else if (isAdvancedPicker) {
                drawable = new LayerDrawable(new Drawable[] {
                        colorChoiceDrawable,
                        res.getDrawable(R.drawable.ic_colorpicker_swatch_overflow_dark)
                });
            }

            imageView.setImageDrawable(drawable);

        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
    }
}