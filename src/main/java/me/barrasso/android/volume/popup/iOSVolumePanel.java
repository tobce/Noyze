package me.barrasso.android.volume.popup;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.dashclock.configuration.ColorPreference;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.media.VolumePanelInfo;

import static me.barrasso.android.volume.LogUtils.LOGD;

/**
 * {@link me.barrasso.android.volume.popup.VolumePanel} meant to mimic the less-than-attractive
 * Apple iOS volume_3 panel whereby a stream icon, text, and a progress bar are shown in the
 * middle of the screen.
 */
public class iOSVolumePanel extends VolumePanel {

    public static final String TAG = iOSVolumePanel.class.getSimpleName();

    public static final VolumePanelInfo<iOSVolumePanel> VOLUME_PANEL_INFO =
            new VolumePanelInfo<iOSVolumePanel>(iOSVolumePanel.class);

    public static int[] iconForStream(StreamResources res) {
        switch (res) {
            case NotificationStream:
            case RingerStream:
                return new int[] { R.drawable.ringer, R.drawable.ringer_muted };
            default:
                return new int[] { R.drawable.volume_3, R.drawable.volume_muted };
        }
    }

    public iOSVolumePanel(PopupWindowManager pWindowManager) {
        super(pWindowManager);
    }

    protected static Drawable makeBackground(Resources res, final int color) {
        int padding = res.getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        float corners = (3 * padding) / 4;
        float[] radii = new float[] { corners, corners, corners, corners, corners, corners, corners, corners };
        ShapeDrawable rect = new ShapeDrawable(new RoundRectShape(radii, null, null));
        rect.setPadding(padding / 4, padding / 4, padding / 4, padding / 4);
        rect.getPaint().setColor(color);
        return rect;
    }

    TextView volumeText;
    ImageView icon;
    iOSProgressBar seekBar;
    ViewGroup root;
    TextView silent;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getContext();

        LayoutInflater inflater = LayoutInflater.from(context);
        Typeface helveticaBold = Typeface.createFromAsset(context.getAssets(), "fonts/HelveticaNeue-Bold.ttf");
        Typeface helvetica = Typeface.createFromAsset(context.getAssets(), "fonts/HelveticaNeue.ttf");
        root = (ViewGroup) inflater.inflate(R.layout.ios_volume_adjust, null);
        seekBar = (iOSProgressBar) root.findViewById(android.R.id.progress);
        icon = (ImageView) root.findViewById(R.id.stream_icon);
        volumeText = (TextView) root.findViewById(R.id.volume_text);
        silent = (TextView) root.findViewById(R.id.mediaSilent);
        volumeText.setTypeface(helveticaBold);
        silent.setTypeface(helvetica);
        volumeText.setPaintFlags(volumeText.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        silent.setPaintFlags(silent.getPaintFlags() | Paint.SUBPIXEL_TEXT_FLAG);

        // Set the default color & background.
        if (!has(COLOR)) color = Color.WHITE;
        if (!has(BACKGROUND)) backgroundColor = Color.BLACK;

        mLayout = root;
    }

    @Override public void setBackgroundColor(int backgroundColor) {
        super.setBackgroundColor(backgroundColor);
        Drawable background = makeBackground(getResources(), backgroundColor);
        background.setBounds(0, 0, root.getWidth(), root.getHeight());
        root.setBackground(background);
        boolean dark = ColorPreference.isColorDark(backgroundColor);
        if (!has(BACKGROUND)) {
            setTextColor((dark) ? Color.WHITE : Color.BLACK);
        }
    }

    protected void setTextColor(int color) {
        volumeText.setTextColor(color);
        silent.setTextColor(color);
    }

    @Override public void setColor(int color) {
        super.setColor(color);
        icon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        setTextColor(color);
    }

    protected void toggleSilent(int visibility) {
        switch (visibility) {
            case View.VISIBLE:
                int descRes = R.string.silent_c;
                if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) descRes = R.string.vibrate_c;
                if (mMusicActive) descRes = R.string.mute_c;
                silent.setText(descRes);
                silent.setVisibility(View.VISIBLE);
                seekBar.setVisibility(View.GONE);
                break;
            case View.GONE:
                silent.setVisibility(View.GONE);
                seekBar.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override public void onRingerModeChange(int ringerMode) {
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL)
            toggleSilent(View.VISIBLE);
    }

    protected static int normalizeVolume(int index, int max, int to) {
        return (int) Math.ceil((index * to) / max);
    }

    @Override
    public void onStreamVolumeChange(int streamType, int volume, int max) {
        // Update the icon & progress based on the volume_3 change.
        StreamResources resources = StreamResources.resourceForStreamType(streamType);
        resources.setVolume(volume);
        LOGD(TAG, "onStreamVolumeChange(" + streamType + ", " + volume + ", " + max + ")");
        int[] icons = iconForStream(resources);
        int iconRes = ((resources.getVolume() <= 0) ? icons[1] : icons[0]);
        toggleSilent((resources.getVolume() <= 0) ? View.VISIBLE : View.GONE);

        // Animate the speaker icon based on the volume level.
        if (iconRes == R.drawable.volume_3) {
            if (volume == 0 && mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                iconRes = R.drawable.volume_0;
            } else if (volume <= (max / 3)) {
                iconRes = R.drawable.volume_1;
            } else if (volume <= ((2 * max) / 3)) {
                iconRes = R.drawable.volume_2;
            } else {
                iconRes = R.drawable.volume_3;
            }
        }

        icon.setImageResource(iconRes);
        volumeText.setText(resources.getDescRes());
        int largest = mVolumeManager.getLargestMax();
        seekBar.setProgress(normalizeVolume(volume, max, largest), largest);
        show();
    }

    @Override public boolean isInteractive() { return true; }

    @Override public WindowManager.LayoutParams getWindowLayoutParams() {
        int flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE		|
                   WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH  |
                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL      |
                   WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR   |
                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN		|
                   WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED	    );
        WindowManager.LayoutParams WPARAMS = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, flags, PixelFormat.TRANSLUCENT);
        WPARAMS.windowAnimations = android.R.style.Animation_Dialog;
        WPARAMS.packageName = getContext().getPackageName();
        WPARAMS.setTitle(TAG);
        WPARAMS.gravity = (Gravity.CENTER);
        WPARAMS.screenBrightness = WPARAMS.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        return WPARAMS;
    }

}