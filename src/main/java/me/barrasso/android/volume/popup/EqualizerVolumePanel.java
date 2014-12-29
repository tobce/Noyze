package me.barrasso.android.volume.popup;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.provider.Settings;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.audiofx.OpenSLESConstants;

import java.util.Arrays;
import java.util.UUID;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.ui.Expandable;
import me.barrasso.android.volume.ui.VerticalSeekBar;
import me.barrasso.android.volume.ui.transition.TransitionCompat;

import static me.barrasso.android.volume.LogUtils.LOGI;

@SuppressWarnings("unused")
public class EqualizerVolumePanel extends VolumePanel
        implements Equalizer.OnParameterChangeListener, Spinner.OnItemSelectedListener, Expandable {

    public static final String TAG = EqualizerVolumePanel.class.getSimpleName();

    private static final int PRIORITY = 20;
    private static final int GLOBAL_AUDIO_OUTPUT = 0;

    public static final VolumePanelInfo<EqualizerVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<EqualizerVolumePanel>(EqualizerVolumePanel.class);

    protected AudioEffect.Descriptor[] descriptors;
    protected Equalizer equalizer;
    protected Virtualizer virtualizer;
    protected BassBoost bassBoost;

    protected short preset;
    protected short[] range = new short[2];
    protected short[] levels;
    protected int[] frequencies;
    protected SparseArray<String> presets;

    /**
     * @see com.android.audiofx.OpenSLESConstants#BASSBOOST_MAX_STRENGTH
     * @see com.android.audiofx.OpenSLESConstants#BASSBOOST_MIN_STRENGTH
     **/
    protected short bassStrength;

    /**
     * @see com.android.audiofx.OpenSLESConstants#VIRTUALIZER_MAX_STRENGTH
     * @see com.android.audiofx.OpenSLESConstants#VIRTUALIZER_MIN_STRENGTH
     **/
    protected short virtualizerStrength;

    public EqualizerVolumePanel(PopupWindowManager pWindowManager) { super(pWindowManager); }

    protected void startEqualizer() {
        equalizer = getEqualizer();
        bassBoost = getBassBoost();
        virtualizer = getVirtualizer();
        descriptors = AudioEffect.queryEffects();

        // Create everything we can now if we've got an equalizer.
        if (null != equalizer) {
            equalizer.setEnabled(true);
            short bands = equalizer.getNumberOfBands();
            levels = new short[bands];
            range = equalizer.getBandLevelRange();
            frequencies = new int[bands];

            // Collect the levels and frequencies of each band.
            for (short band = 0; band < bands; ++band) {
                levels[band] = equalizer.getBandLevel(band);
                frequencies[band] = equalizer.getCenterFreq(band) / 1000; // Hz
            }

            // Collect information about the pre-packaged presets.
            short numOfPresets = equalizer.getNumberOfPresets();
            preset = equalizer.getCurrentPreset();
            presets = new SparseArray<String>(numOfPresets);
            for (short preset = 0; preset < numOfPresets; ++preset) {
                presets.put(preset, equalizer.getPresetName(preset));
            }
        }

        if (null != virtualizer) {
            virtualizer.setEnabled(true);
            virtualizerStrength = virtualizer.getRoundedStrength();
        }

        if (null != bassBoost) {
            bassBoost.setEnabled(true);
            bassStrength = bassBoost.getRoundedStrength();
        }

        LOGI(TAG, dump());
    }

    // Equalizer

    private String dump() {
        String info = getClass().getSimpleName() + "@{\n";
        info += "\tbass_boost=" + isBassBostSupported() + ", \n";
        info += "\tvirtualizer=" + isVirtualizerSupported() + ", \n";
        info += "\tpresets=[" + presets.toString() + "], \n";
        info += "\tlevels=[" + Arrays.toString(levels) + "], \n";
        info += "\tfrequencies=[" + Arrays.toString(frequencies) + "], \n";
        info += "\trange=[" + Arrays.toString(range) + "], \n";
        info += "\tdescriptors=[" + Arrays.toString(getDescriptorNames()) + ']';
        info += "\n}";
        return info;
    }

    private void updateEqualizerState() {
        updateEqualizerState(equalizer);
    }

    private void updateEqualizerState(Equalizer eq) {
        if (null == eq) return;
        preset = eq.getCurrentPreset();
        for (short band = 0; band < levels.length; ++band) {
            levels[band] = eq.getBandLevel(band);
        }
    }

    /** @see android.media.audiofx.Equalizer.OnParameterChangeListener */
    protected void setParameterListener(Equalizer.OnParameterChangeListener listener) {
        if (null != equalizer) {
            equalizer.setParameterListener(listener);
        }
    }

    /** @see android.media.audiofx.Equalizer#usePreset(short) */
    protected void usePreset(final short newPreset) {
        if (null != equalizer && presets.indexOfKey(newPreset) >= 0) {
            equalizer.usePreset(newPreset);
            preset = newPreset;
        }
    }

    /** @return True if an Equalizer is supported on this device. */
    protected boolean isEqualizerSupported() {
        return (null != equalizer);
    }

    /** @return True if an Virtualizer is supported on this device. */
    protected boolean isVirtualizerSupported() {
        return (null != virtualizer && virtualizer.getStrengthSupported());
    }

    /** @return True if an BassBost is supported on this device. */
    protected boolean isBassBostSupported() {
        return (null != bassBoost && bassBoost.getStrengthSupported());
    }

    protected Equalizer getEqualizer() {
        try {
            // NOTE: this is because Equalization isn't supported on all devices.
            return new Equalizer(PRIORITY, GLOBAL_AUDIO_OUTPUT);
        } catch (IllegalArgumentException e) {
            LogUtils.LOGE("AudioFX", "Equalizer effect not supported", e);
        } catch (IllegalStateException e) {
            LogUtils.LOGE("AudioFX", "Equalizer cannot get strength supported", e);
        } catch (UnsupportedOperationException e) {
            LogUtils.LOGE("AudioFX", "Equalizer library not loaded", e);
        } catch (RuntimeException e) {
            LogUtils.LOGE("AudioFX", "Equalizer effect not found", e);
        }
        return null;
    }

    protected BassBoost getBassBoost() {
        try {
            // NOTE: this is because BassBoost isn't supported on all devices.
            return new BassBoost(PRIORITY, GLOBAL_AUDIO_OUTPUT);
        } catch (IllegalArgumentException e) {
            LogUtils.LOGE("AudioFX", "BassBoost effect not supported", e);
        } catch (IllegalStateException e) {
            LogUtils.LOGE("AudioFX", "BassBoost cannot get strength supported", e);
        } catch (UnsupportedOperationException e) {
            LogUtils.LOGE("AudioFX", "BassBoost library not loaded", e);
        } catch (RuntimeException e) {
            LogUtils.LOGE("AudioFX", "BassBoost effect not found", e);
        }
        return null;
    }

    protected Virtualizer getVirtualizer() {
        try {
            // NOTE: this is because Virtualizer isn't supported on all devices.
            return new Virtualizer(PRIORITY, GLOBAL_AUDIO_OUTPUT);
        } catch (IllegalArgumentException e) {
            LogUtils.LOGE("AudioFX", "Virtualizer effect not supported", e);
        } catch (IllegalStateException e) {
            LogUtils.LOGE("AudioFX", "Virtualizer cannot get strength supported", e);
        } catch (UnsupportedOperationException e) {
            LogUtils.LOGE("AudioFX", "Virtualizer library not loaded", e);
        } catch (RuntimeException e) {
            LogUtils.LOGE("AudioFX", "Virtualizer effect not found", e);
        }
        return null;
    }

    /** @see android.media.audiofx.AudioEffect.Descriptor */
    protected boolean hasFeature(AudioEffect.Descriptor descriptor) {
        return (Arrays.binarySearch(descriptors, descriptor) >= 0);
    }

    protected UUID[] uuids;

    /** @see android.media.audiofx.AudioEffect.Descriptor#uuid */
    protected boolean hasFeature(UUID uuid) {
        // Build of array of UUIDs to search through.
        if (null == uuids || uuids.length == 0) {
            uuids = new UUID[descriptors.length];
            int i = -1;
            for (AudioEffect.Descriptor descriptor : descriptors)
                uuids[++i] = descriptor.uuid;
        }
        return (Arrays.binarySearch(uuids, uuid) >= 0);
    }

    /** @see android.media.audiofx.AudioEffect.Descriptor#name */
    protected String[] getDescriptorNames() {
        // Build of array of UUIDs to search through.
        String[] names = new String[descriptors.length];
        int i = -1;
        for (AudioEffect.Descriptor descriptor : descriptors)
            names[++i] = descriptor.name;
        return names;
    }

    private void release(AudioEffect effect) {
        if (null != effect) {
            effect.setControlStatusListener(null);
            effect.setEnableStatusListener(null);
            if (effect instanceof Equalizer) {
                ((Equalizer) effect).setParameterListener(null);
            } else if (effect instanceof BassBoost) {
                ((BassBoost) effect).setParameterListener(null);
            } else if (effect instanceof Virtualizer) {
                ((Virtualizer) effect).setParameterListener(null);
            }
            effect.release();
        }
    }

    /** @return An array of all preset names, in order. */
    protected String[] getPresets() {
        int key = 0;
        String[] names = new String[presets.size()];
        for(int i = 0; i < names.length; ++i) {
            key = presets.keyAt(i);
            names[i] = presets.get(key);
        }
        return names;
    }

    public static int calcPercentage(int value, int range) {
        return (int) 100.0F * (value / range);
    }

    // End Equalizer

    @Override
    public void onParameterChange(Equalizer effect, int status, int id, int qualifier, int value) {
        LOGI(TAG, "onParameterChange(" + effect.getDescriptor().name +
                ", " + status + ", " + id + ", " + qualifier + ", " + value + ')');
        updateEqualizerState();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        LOGI(TAG, "onItemSelected(" + pos + ", " + id + ')');
        usePreset((short) pos);
    }

    @Override public void onNothingSelected(AdapterView<?> parent) { }

    SeekBar.OnSeekBarChangeListener levelListener = new SeekBar.OnSeekBarChangeListener() {
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            if (isVirtualizerSupported() && seekBar.getId() == R.id.virtualizer) {
                virtualizer.setStrength((short) progress);
                LOGI(virtualizer.getClass().getSimpleName(), "setStrength(" + progress + ')');
            } else if (isBassBostSupported() && seekBar.getId() == R.id.bass_boost) {
                bassBoost.setStrength((short) progress);
                LOGI(bassBoost.getClass().getSimpleName(), "setStrength(" + progress + ')');
            } else if (null != equalizer) {
                Object tag = seekBar.getTag();
                if (tag instanceof Short) {
                    short band = (Short) tag;
                    equalizer.setBandLevel(band, (short) (progress + range[0]));
                    LOGI(equalizer.getClass().getSimpleName(), "setBandLevel(" + (progress + range[0]) + ')');
                }
            }
            onUserInteraction();
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    protected ViewGroup root, eqRoot;
    protected ImageView volumeIcon;
    protected LinearLayout levelsRoot;
    protected ImageButton expandButton;
    protected SeekBar volumeSeekBar;

    protected Spinner presetSpinner;
    protected SeekBar bassSeekBar, virtualizerSeekBar;
    protected TextView bassText, virtualizerText;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        setParameterListener(this);
        root = (ViewGroup) inflater.inflate(R.layout.eq_volume_adjust, null);

        eqRoot = (ViewGroup) root.findViewById(R.id.music_panel);
        levelsRoot = (LinearLayout) root.findViewById(R.id.slider_group);
        volumeSeekBar = (SeekBar) root.findViewById(android.R.id.progress);
        volumeIcon = (ImageView) root.findViewById(R.id.stream_icon);
        expandButton = (ImageButton) root.findViewById(R.id.expand_button);

        volumeSeekBar.setOnSeekBarChangeListener(this);
        expandButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { expand(); }
        });
        volumeIcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { launchSoundSettings(); }
        });

        presetSpinner = (Spinner) root.findViewById(R.id.eq_presets);
        virtualizerSeekBar = (SeekBar) root.findViewById(R.id.virtualizer);
        bassSeekBar = (SeekBar) root.findViewById(R.id.bass_boost);
        virtualizerText = (TextView) root.findViewById(R.id.virtualizer_text);
        bassText = (TextView) root.findViewById(R.id.bass_boost_text);

        startEqualizer();

        if (isBassBostSupported()) {
            bassSeekBar.setMax(OpenSLESConstants.BASSBOOST_MAX_STRENGTH);
            bassSeekBar.setOnSeekBarChangeListener(levelListener);
        } else {
            bassSeekBar.setVisibility(View.GONE);
            bassText.setVisibility(View.GONE);
        }

        if (isVirtualizerSupported()) {
            virtualizerSeekBar.setMax(OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH);
            virtualizerSeekBar.setOnSeekBarChangeListener(levelListener);
        } else {
            virtualizerSeekBar.setVisibility(View.GONE);
            virtualizerText.setVisibility(View.GONE);
        }

        // Create our Spinner for Equalizer presets.
        if (presets != null && presets.size() > 0) {
            presetSpinner.setSelection(preset);
            ArrayAdapter<String> presetAdapter = new ArrayAdapter<String>(context,
                    android.R.layout.simple_spinner_item, getPresets());
            presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            presetSpinner.setAdapter(presetAdapter);
            presetSpinner.setOnItemSelectedListener(this);
            presetSpinner.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP)
                        onUserInteraction();
                    return view.onTouchEvent(motionEvent);
                }
            });
        } else {
            // No Presets? Guess we won't display that!
            root.findViewById(R.id.eq_presets_container).setVisibility(View.GONE);
        }

        // KitKat+ add support for default transitions.
        TransitionCompat transition = TransitionCompat.get();
        transition.beginDelayedTransition((ViewGroup) root.findViewById(R.id.slider_group));

        initEqualizer();

        mLayout = root;
    }

    protected SeekBar[] levelSeekBars;

    protected void initEqualizer() {
        if (levels == null || levels.length == 0) {
            levelsRoot.setVisibility(View.GONE);
            return;
        }

        Context context = getContext();
        levelSeekBars = new SeekBar[levels.length];
        levelsRoot.setWeightSum(levels.length);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) dpToPx(10), ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.weight = 1;
        layoutParams.gravity = (Gravity.CENTER_HORIZONTAL);
        layoutParams.setMargins(0, 10, 0, 10);

        for (short band = 0; band < levels.length; ++band) {
            levelSeekBars[band] = new VerticalSeekBar(context);
            levelSeekBars[band].setMax(range[1] - range[0]); // max - min
            levelSeekBars[band].setProgress(levels[band]);
            levelSeekBars[band].setLayoutParams(layoutParams);
            levelSeekBars[band].setTag(band);
            levelSeekBars[band].setEnabled(true);
            levelSeekBars[band].setOnSeekBarChangeListener(levelListener);
            levelsRoot.addView(levelSeekBars[band]);
        }
    }

    protected float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, getResources().getDisplayMetrics());
    }

    protected void launchSoundSettings() {
        hide();
        startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
    }

    // Expansion

    @Override public void hide() {
        super.hide();
        if (isExpanded()) collapse();
    }

    @Override
    public boolean isExpanded() {
        return (null != expandButton && expandButton.getVisibility() != View.VISIBLE);
    }

    @Override
    public void expand() {
        LOGI(TAG, "expand()");
        expandButton.setVisibility(View.GONE);
        eqRoot.setVisibility(View.VISIBLE);
        onUserInteraction();
    }

    @Override
    public void collapse() {
        LOGI(TAG, "collapse()");
        expandButton.setVisibility(View.VISIBLE);
        eqRoot.setVisibility(View.GONE);
    }

    // End Expansion

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        LOGI(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        int iconRes = ((volume <= 0) ? resources.getIconMuteRes() : resources.getIconRes());
        Drawable drawable = getResources().getDrawable(iconRes);
        drawable.mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        LayerDrawable layer = (LayerDrawable) volumeSeekBar.getProgressDrawable();
        layer.findDrawableByLayerId(android.R.id.progress).mutate().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        volumeIcon.setImageDrawable(drawable);
        volumeSeekBar.setMax(max);
        volumeSeekBar.setProgress(volume);
        volumeSeekBar.setTag(resources);
        show();
    }

    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		     |
                     WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH     |
                     WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL         |
                     WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	     );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, 0,
                getResources().getDimensionPixelSize(R.dimen.volume_panel_top),
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(TAG);
        WPARAMS.gravity = (Gravity.FILL_HORIZONTAL | Gravity.TOP);
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }

    protected void removeOnSeekBarChangeListeners(SeekBar... seekBars) {
        for (SeekBar seekBar : seekBars)
            seekBar.setOnSeekBarChangeListener(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        uuids = null;
        descriptors = null;
        removeOnSeekBarChangeListeners(bassSeekBar, virtualizerSeekBar, volumeSeekBar);
        removeOnSeekBarChangeListeners(levelSeekBars);
        presetSpinner.setOnItemSelectedListener(null);
        volumeIcon.setOnClickListener(null);
        expandButton.setOnClickListener(null);
        release(equalizer);
        release(bassBoost);
        release(virtualizer);
        bassBoost = null;
        virtualizer = null;
        equalizer = null;
    }

}