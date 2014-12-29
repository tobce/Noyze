package me.barrasso.android.volume.popup;

import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioRoutesInfo;
import android.media.MediaRouter;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Pair;
import android.util.Property;
import android.util.SparseArray;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.text.TextUtils;
import android.media.AudioManager;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.android.systemui.qs.GlobalSetting;
import com.squareup.otto.MainThreadBus;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_SAME;
import static android.media.AudioManager.FLAG_PLAY_SOUND;
import static android.media.AudioManager.FLAG_VIBRATE;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;
import static android.media.AudioManager.USE_DEFAULT_STREAM_TYPE;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;
import static me.barrasso.android.volume.media.VolumeMediaReceiver.*;

import me.barrasso.android.volume.VolumeAccessibilityService;
import me.barrasso.android.volume.activities.NoyzeApp;
import me.barrasso.android.volume.media.MediaProviderDelegate;
import me.barrasso.android.volume.media.VolumeMediaReceiver;
import me.barrasso.android.volume.media.compat.RemoteControlCompat;
import me.barrasso.android.volume.media.conditions.RingerNotificationLink;
import me.barrasso.android.volume.media.conditions.SystemVolume;
import me.barrasso.android.volume.utils.AppTypeMonitor;
import me.barrasso.android.volume.utils.AudioHelper;
import me.barrasso.android.volume.utils.Constants;
import me.barrasso.android.volume.ui.OnTouchClickListener;
import me.barrasso.android.volume.R;
import me.barrasso.android.volume.utils.SettingsHelper;
import me.barrasso.android.volume.media.StreamResources;
import me.barrasso.android.volume.utils.Utils;
import me.barrasso.android.volume.utils.VolumeManager;

/**
 * Handle the volume up and down keys.<br /><br />
 * Deals with AudioManager and Volume/ Media related events. Subclasses
 * should present the UI as they see fit. Comes with built-in support for
 * {@link SeekBar} widgets as well as many UI events.<br /><br />
 * Details: VolumePanel accepts input from two areas: {@link KeyEvent}s sent
 * from an {@link android.accessibilityservice.AccessibilityService}/ input stream, and a {@link BroadcastReceiver}
 * to handle various events. These monitor changes in system volume from various
 * streams, and {@link AudioManager} is used to adjust system volume as well as
 * handle media-related events.<br /><br />
 *
 * <strike>This code really should be moved elsewhere.
 *
 * Seriously, it really really should be moved elsewhere.  This is used by
 * android.media.AudioService, which actually runs in the system process, to
 * show the volume dialog when the user changes the volume.  What a mess.</strike>
 */
public abstract class VolumePanel extends PopupWindow
	implements View.OnKeyListener, OnSeekBarChangeListener {

	/** Object that contains data for each slider */
    public static final class StreamControl {
        public int streamType;
        public ViewGroup group;
        public ImageView icon;
        public SeekBar seekbarView;
        public int iconRes;
        public int iconMuteRes;
    }

    static class VolumeChangeInfo {
        public final int mStreamType;
        public final int mFromVolume;
        public final int mToVolume;
        public final long mEventTime;

        public VolumeChangeInfo(int s, int i, int j) {
            mStreamType = s;
            mFromVolume = i;
            mToVolume = j;
            mEventTime = System.currentTimeMillis();
        }
    }

    protected static final int VIBRATE_DURATION = 300;

    /** @return The value of "com.android.systemui.R$dimen#notification_panel_width", or 0 if inaccessible. */
    protected static int getNotificationPanelWidth() {
        int id = getSystemUiDimen("notification_panel_width");
        if (id <= 0) return 0;
        return Resources.getSystem().getDimensionPixelSize(id);
    }

    // Property of all VolumePanels is there auto-hide/ timeout, maybe music app.
    public static final Property<VolumePanel, String> MUSIC_APP =
            Property.of(VolumePanel.class, String.class, "musicUri");
    public static final Property<VolumePanel, Boolean> MASTER_VOLUME =
            Property.of(VolumePanel.class, Boolean.class, "oneVolume");
    public static final Property<VolumePanel, Integer> RINGER_MODE =
            Property.of(VolumePanel.class, Integer.class, "ringerMode");
    public static final Property<VolumePanel, String> ACTION_LONG_PRESS_VOLUME_UP =
            Property.of(VolumePanel.class, String.class, "longPressActionUp");
    public static final Property<VolumePanel, String> ACTION_LONG_PRESS_VOLUME_DOWN =
            Property.of(VolumePanel.class, String.class, "longPressActionDown");
    public static final Property<VolumePanel, Boolean> HIDE_FULLSCREEN =
            Property.of(VolumePanel.class, Boolean.class, "hideFullscreen");
    public static final Property<VolumePanel, Integer> DEFAULT_STREAM =
            Property.of(VolumePanel.class, Integer.class, "defaultStream");
    public static final Property<VolumePanel, Boolean> LINK_NOTIF_RINGER =
            Property.of(VolumePanel.class, Boolean.class, "linkNotifRinger");

    public static final Property<VolumePanel, Integer> COLOR =
            Property.of(VolumePanel.class, Integer.TYPE, "color");
    public static final Property<VolumePanel, Integer> BACKGROUND =
            Property.of(VolumePanel.class, Integer.TYPE, "backgroundColor");
    public static final Property<VolumePanel, Integer> TERTIARY =
            Property.of(VolumePanel.class, Integer.TYPE, "tertiaryColor");
    public static final Property<VolumePanel, Boolean> SEEK =
            Property.of(VolumePanel.class, Boolean.class, "seek");
    public static final Property<VolumePanel, Boolean> NO_LONG_PRESS =
            Property.of(VolumePanel.class, Boolean.class, "noLongPress");
    public static final Property<VolumePanel, Boolean> HIDE_CAMERA =
            Property.of(VolumePanel.class, Boolean.class, "hideCamera");
    public static final Property<VolumePanel, Boolean> STRETCH =
            Property.of(VolumePanel.class, Boolean.class, "stretch");
    public static final Property<VolumePanel, Boolean> ALWAYS_EXPANDED =
            Property.of(VolumePanel.class, Boolean.class, "alwaysExpanded");
    public static final Property<VolumePanel, Boolean> FIRST_REVEAL =
            Property.of(VolumePanel.class, Boolean.class, "firstReveal");

    public static IntentFilter VOLUME_MUSIC_EVENTS() {
	    final IntentFilter VOLUME_MUSIC_EVENTS = new IntentFilter();
		VOLUME_MUSIC_EVENTS.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
		VOLUME_MUSIC_EVENTS.addAction(Constants.VOLUME_CHANGED_ACTION);
        VOLUME_MUSIC_EVENTS.addAction(Constants.MASTER_MUTE_CHANGED_ACTION);
        VOLUME_MUSIC_EVENTS.addAction(Constants.MASTER_VOLUME_CHANGED_ACTION);
		VOLUME_MUSIC_EVENTS.addAction(Intent.ACTION_MEDIA_BUTTON);
        VOLUME_MUSIC_EVENTS.addAction(Intent.ACTION_USER_PRESENT);
        VOLUME_MUSIC_EVENTS.addAction(Constants.ACTION_VOLUMEPANEL_SHOWN);
        VOLUME_MUSIC_EVENTS.addAction(Constants.ACTION_VOLUMEPANEL_HIDDEN);
        VOLUME_MUSIC_EVENTS.addAction(Intent.ACTION_HEADSET_PLUG);
		VOLUME_MUSIC_EVENTS.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        return VOLUME_MUSIC_EVENTS;
	}

    // Pseudo stream type for master volume
    public static final int STREAM_MASTER = -100;
    // Pseudo stream type for remote volume is defined in AudioService.STREAM_REMOTE_MUSIC

    /* @hide The audio stream for phone calls when connected on bluetooth */
    public static final int DEF_STREAM_BLUETOOTH_SCO = 6;

    /** A fake stream type to match the notion of remote media playback */
    public final static int DEF_STREAM_REMOTE_MUSIC = -200;

    // Reflection-attempted stream constants with localized default values.
    public static final int STREAM_BLUETOOTH_SCO = AudioHelper.getAudioSystemFlag(
            "STREAM_BLUETOOTH_SCO", DEF_STREAM_BLUETOOTH_SCO);
    public static final int STREAM_REMOTE_MUSIC = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ?
            DEF_STREAM_REMOTE_MUSIC : AudioHelper.getAudioServiceFlag(
            "STREAM_REMOTE_MUSIC", DEF_STREAM_REMOTE_MUSIC));

    // ========== ########## ==========
    // ========== ########## ==========
	
	protected final VolumeMediaHandler mHandler;
	protected VolumeMediaReceiver mVolumeMediaReceiver;
    protected CallStateListener mCallStateListener;
    protected GlobalSetting mPriorityModeObserver;

    protected NoyzeApp app;
    protected VolumeManager mVolumeManager;
	protected AudioManager mAudioManager;
    protected AudioHelper mAudioHelper;
	protected WindowManager.LayoutParams mWindowAttributes;
	protected TelephonyManager mTelephonyManager;

    /** All the slider controls mapped by stream type */
    protected SparseArray<StreamControl> mStreamControls;

	// AudioManager volume/ media states
    protected boolean mVibrateWhenRinging;
	protected boolean mSpeakerphoneOn;
	protected boolean mMusicActive;
	protected boolean mRingIsSilent;
    protected boolean mVolumeDirty; // Needs updating?
    protected boolean seek; // Use ProgressBar as SeekBar
	protected int mRingerMode, mLastRingerMode;
    protected int mLongPressTimeout;
    protected int defaultStream = USE_DEFAULT_STREAM_TYPE; // No stream
    protected boolean fullscreen;
    protected boolean stretch;
    protected boolean mNotificationRingerLink;
    protected boolean linkNotifRinger;
    protected boolean alwaysExpanded;

    protected boolean hideCamera = true;
    protected boolean hideFullscreen;
    protected boolean oneVolume;
    protected boolean firstReveal;
    protected int ringerMode;
    protected int color = Color.WHITE;
    protected int backgroundColor = Color.BLACK;
    protected int tertiaryColor = Color.GRAY;
    protected int mCallState = TelephonyManager.CALL_STATE_IDLE;
    protected boolean noLongPress = true;
    protected VolumeChangeInfo mLastVolumeChange;

    // protected AudioFlingerProxy mAudioFlingerProxy;
    protected AppTypeMonitor mAppTypeMonitor;
    protected MediaProviderDelegate mMediaProviderDelegate;

    /*package*/ int lastStreamType = USE_DEFAULT_STREAM_TYPE;

    // Actions for long-pressing the volume up/ down buttons.
    protected String longPressActionUp;
    protected String longPressActionDown;
	
	public VolumePanel(PopupWindowManager manager) {
		super(manager);
		Context context = manager.getContext();
        app = (NoyzeApp) context.getApplicationContext();
		mStreamControls = new SparseArray<StreamControl>(StreamResources.STREAMS.length);
		mHandler = new VolumeMediaHandler(context.getMainLooper());
		mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mVolumeManager = new VolumeManager(mAudioManager);
        mAudioHelper = AudioHelper.getHelper(context, mAudioManager);
        // mAudioFlingerProxy = new AudioFlingerProxy();
        mAudioHelper.setHandler(mHandler);
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();

        if (MediaProviderDelegate.IS_V18)
            mMediaProviderDelegate = MediaProviderDelegate.getDelegate(context);

        // Register here: be SURE that the handler is not null.
        if (null == mVolumeMediaReceiver) {
            mVolumeMediaReceiver = new VolumeMediaReceiver(mHandler);
            IntentFilter events = Utils.merge(VOLUME_MUSIC_EVENTS(), Constants.MEDIA_ACTION_FILTER());
            context.registerReceiver(mVolumeMediaReceiver, events);
            mVolumeMediaReceiver.setHandler(mHandler);
        }

        // Register for events related to the call state.
        if (null == mCallStateListener) {
            mCallState = mTelephonyManager.getCallState();
            mCallStateListener = new CallStateListener(mHandler);
            mTelephonyManager.listen(mCallStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

		initState();
	}

	/**
	 * Call {@link super#onCreate} AFTER initializing all necessary
	 * {@link View}s. Here is where all event listeners are attached.
	 */
    @Override
	protected void onCreate() {
		hookIntoEvents();

        // By default, let's update cancel values.
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setCloseOnLongClick(false);
	}
	
	// Localized from android.view.VolumePanel
    // Note: Master volume isn't handled for most methods to reduce complexity.
    // AudioManager checks if Master Volume is enabled, and handles these cases
    // for us through hidden methods.
	
	protected boolean isMuted(int streamType) {
        if (streamType == STREAM_MASTER) {
            Boolean ret = mAudioHelper.boolMethod("isMasterMute", null);
            if (null != ret) return ret;
            return false;
        } else if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) &&
                   (streamType == STREAM_REMOTE_MUSIC)) {
            Integer ret = mAudioHelper.intServiceMethod("getRemoteStreamVolume", null);
            if (null != ret) return (ret <= 0);
            return false;
        } else {
            Boolean ret = mAudioHelper.boolMethod("isStreamMute", new Object[] { streamType });
            if (null != ret) return ret;
        }
        return false;
    }

    /** @return The max volume for music, taking "safe volume," into consideration. */
    protected int getMusicStreamMaxVolume() {
        return mAudioManager.getStreamMaxVolume(STREAM_MUSIC);
        /*if (mAudioHelper.isSafeMediaVolumeEnabled(getContext())) {
            final int index = getStreamVolume(STREAM_MUSIC);
            final int headsetMax = mAudioHelper.getSafeMediaVolumeIndex();
            if (headsetMax > 0) {
                if (index > headsetMax) {
                    return max;
                } else {
                    return headsetMax;
                }
            }
        }
        return max;*/
    }

    protected int getStreamMaxVolume(int streamType) {
        if (streamType == STREAM_MASTER) {
            return mVolumeManager.getSmallestMax();
        } else if (streamType == STREAM_REMOTE_MUSIC) {
            Integer ret = mAudioHelper.intIServiceMethod("getRemoteStreamMaxVolume", null);
            if (null != ret) return ret;
            streamType = STREAM_MUSIC;
        } else if (streamType == USE_DEFAULT_STREAM_TYPE) {
            return Integer.MAX_VALUE;
        }

        // NOTE: this is done to deal with "safe volume."
        if (streamType == STREAM_MUSIC)
            return getMusicStreamMaxVolume();

        return mAudioManager.getStreamMaxVolume(streamType);
    }

    protected int getStreamVolume(int streamType) {
        if (streamType == STREAM_MASTER) {
            return mVolumeManager.getManagedVolume();
        } else if (streamType == STREAM_REMOTE_MUSIC) {
            Integer ret = mAudioHelper.intIServiceMethod("getRemoteStreamVolume", null);
            if (null != ret) return ret;
            streamType = STREAM_MUSIC;
        } else if (streamType == USE_DEFAULT_STREAM_TYPE) {
            return Integer.MIN_VALUE; // Can't report to the default.
        }

        return mAudioManager.getStreamVolume(streamType);
    }
    
    /** Proxy for {@link #setStreamVolume(int, int). */
    protected void setVolume(int index) {
    	setStreamVolume(AudioManager.USE_DEFAULT_STREAM_TYPE, index);
    }

	/** Proxy for {@link AudioManager#setStreamVolume(int,int,int).<br />
	 * Handles setting the relevant flags and calling the proper method. */
    protected void setStreamVolume(int streamType, int index) {
        // NOTE: Master Volume needs to handle setting volumes differently.
        int flags = getFlags(streamType);
        if (oneVolume || streamType == STREAM_MASTER) {
            mVolumeManager.setVolumeSync(index, mVolumeManager.getSmallestMax());
            return;
        }

        if (streamType == STREAM_REMOTE_MUSIC) {
            Object ret = mAudioHelper.serviceMethod("setRemoteStreamVolume", new Object[] { index });
            if (null != ret) return;
        }

        if (streamType == Integer.MIN_VALUE) streamType = USE_DEFAULT_STREAM_TYPE;
        mAudioManager.setStreamVolume(streamType, index, flags);
    }

    protected int lastDirection;
    
    /** Proxy for {@link #adjustStreamVolume(int, int) */
    protected void adjustVolume(int direction) {
        lastDirection = direction;
    	// Same implementation of AudioManager#adjustVolume(int,int)
        LOGI("VolumePanel", "adjustVolume(" + direction + ")");
        if (oneVolume)
            mVolumeManager.adjustVolumeSync(direction);
        else {
            /*if (isWiredHeadsetOn() && mAudioHelper.isSafeMediaVolumeEnabled(getContext())) {
                final int safeIndex = mAudioHelper.getSafeMediaVolumeIndex();
                final int musicVolume = getStreamVolume(STREAM_MUSIC);
                final int max = getStreamMaxVolume(STREAM_MUSIC);
                if (musicVolume >= safeIndex) {
                    LOGI("VolumePanel", "Bypassing safe volume index.");
                    try {
                        int ret = mAudioFlingerProxy.adjustStreamVolume(STREAM_MUSIC, direction, musicVolume, max);
                        if (ret == AudioFlingerProxy.BAD_VALUE ||
                            ret == AudioFlingerProxy.CALIBRATION_ERROR) {
                            adjustStreamVolume(direction, USE_DEFAULT_STREAM_TYPE);
                        } else {
                            int index = mAudioFlingerProxy.getStreamIndex(STREAM_MUSIC);
                            LOGI("VolumePanel", "Stream index now: " + index);
                            onVolumeChanged(STREAM_MUSIC, index, max);
                        }
                    } catch (RemoteException re) {
                        LOGE("VolumePanel", "Error bypassing safe volume index.", re);
                        adjustStreamVolume(direction, USE_DEFAULT_STREAM_TYPE);
                    }
                } else {
                    adjustStreamVolume(direction, USE_DEFAULT_STREAM_TYPE);
                }
            } else {*/
                adjustStreamVolume(direction, USE_DEFAULT_STREAM_TYPE);
            //}
        }
    }
        
    /** Proxy for {@link AudioManager#adjustSuggestedStreamVolume(int,int,int).
	 * Handles setting the relevant flags and calling the proper method. */
    protected void adjustStreamVolume(int direction, int streamType) {
        if (hasDefaultStream()) streamType = defaultStream;
        if (mCallState == TelephonyManager.CALL_STATE_OFFHOOK)
            streamType = STREAM_VOICE_CALL;
        adjustSuggestedStreamVolume(direction, streamType, getFlags(streamType));
    }

    /** Proxy for {@link AudioManager#adjustSuggestedStreamVolume(int,int,int).
     * Handles setting the relevant flags and calling the proper method. */
    public void adjustSuggestedStreamVolume(int direction, int streamType, int flags) {
        if (null == mAudioManager) return;
        mAudioManager.adjustSuggestedStreamVolume(direction, streamType, flags);
    }

    protected int getFlags(int streamType) {
    	// NOTE: NEVER use AudioManager.FLAG_SHOW_UI, this will display
        // the actual Android VolumePanel and ruin the ambiance.
        if (streamType == USE_DEFAULT_STREAM_TYPE) streamType = lastStreamType;
        int flags = 0;
        boolean streamAffectedByRinger;

        try {
            // Reported in Google Play developer console.
            streamAffectedByRinger = mAudioHelper.isStreamAffectedByRingerMode(streamType);
        } catch (Throwable t) {
            LOGE("VolumePanel", "Error determining ringer mode flag.", t);
            streamAffectedByRinger = false;
        }

        LOGI("VolumePanel", "getFlags(" + VolumeManager.getStreamName(streamType) +
                "), change=" + ringerMode + ", mRingerMode=" +
                mRingerMode + ", ringerAffected=" + streamAffectedByRinger);

    	/*switch (ringerMode) {
            case Constants.RINGER_MODE_SILENT:
                if (streamAffectedByRinger)
                    return AudioManager.FLAG_ALLOW_RINGER_MODES;
                return flags;

        }*/

        // For the default, let's check the system settings.
        switch (mRingerMode) {
            case RINGER_MODE_NORMAL:
                flags =  AudioManager.FLAG_PLAY_SOUND            |
                         AudioManager.FLAG_VIBRATE;
                break;
            case RINGER_MODE_VIBRATE:
                flags = AudioManager.FLAG_VIBRATE;
                break;
        }

        // Special mode, ALWAYS play a sound!
        if (ringerMode == Constants.RINGER_MODE_RING) {
            if ((flags & AudioManager.FLAG_PLAY_SOUND) != AudioManager.FLAG_PLAY_SOUND) {
                flags |= AudioManager.FLAG_PLAY_SOUND;
            }
        } else if (ringerMode == Constants.RINGER_MODE_SILENT) {
            if ((flags & AudioManager.FLAG_PLAY_SOUND) == AudioManager.FLAG_PLAY_SOUND) {
                flags &= ~AudioManager.FLAG_PLAY_SOUND;
            }
        }

        // Handle the addition of the ringer mode based on the stream type.
        /*if (streamAffectedByRinger)
            if (flags == 0)
                flags = AudioManager.FLAG_ALLOW_RINGER_MODES;
            else
                flags |= AudioManager.FLAG_ALLOW_RINGER_MODES;*/

    	return flags;
    }

    public int getPriorityMode() { return mPriorityMode; }
    protected int mPriorityMode = Constants.ZENMODE_ALL;

    protected void onPriorityModeChanged(int priorityMode) {
        LOGI(TAG, "onPriorityModeChanged(" + priorityMode + ')');
    }

    /** @return The default {@link android.content.Intent#ACTION_MEDIA_BUTTON} receiver, or null. */
    public ComponentName getMediaButtonReceiver() {
        String receiverName = Settings.System.getString(
                getContext().getContentResolver(), Constants.getMediaButtonReceiver());
        if ((null != receiverName) && !receiverName.isEmpty())
            return ComponentName.unflattenFromString(receiverName);
        return null;
    }
    
    // end localized methods
	
	/** @return True if the ringer is silent. */
	public boolean isRingSilent() {
		return mRingIsSilent;
	}

    /** @return True if the device should vibrate while it is ringing. */
    public boolean vibrateWhenRinging() {
        return mVibrateWhenRinging;
    }

    /** @return True if the updated value is different. */
    private boolean updateVibrateWhenRinging() {
        boolean vibrateWhenRinging = (1 == Settings.System.getInt(
                getContext().getContentResolver(), Constants.KEY_VIBRATE, 0));
        boolean ret = (mVibrateWhenRinging == vibrateWhenRinging);
        mVibrateWhenRinging = vibrateWhenRinging;
        return ret;
    }
	
	/**
     * Checks whether the phone is in silent mode, with or without vibrate.
     * @return true if phone is in silent mode, with or without vibrate.
     */
	public boolean isSilentMode() {
        return (mRingerMode == RINGER_MODE_SILENT) ||
               (mRingerMode == RINGER_MODE_VIBRATE);
    }

    protected boolean registeredOtto;

	private void hookIntoEvents() {
		Context context = getContext();
        MainThreadBus.get().register(this);
        mAppTypeMonitor = new AppTypeMonitor(MediaStore.ACTION_IMAGE_CAPTURE, AlarmClock.ACTION_SET_ALARM);
        mAppTypeMonitor.register(context);
        mPriorityModeObserver = new GlobalSetting(context, mUiHandler, Constants.ZEN_MODE) {
            @Override protected void handleValueChanged(int value) {
                mPriorityMode = value;
                onPriorityModeChanged(value);
            }
        };
        mPriorityMode = mPriorityModeObserver.getValue();
        mPriorityModeObserver.setListening(true);
        registeredOtto = true;
	}

	// Initialize some basic audio states to be used onCreate and elsewhere.
	protected void initState() {
		mRingerMode = mAudioManager.getRingerMode();
        mCallState = mTelephonyManager.getCallState();
		mSpeakerphoneOn = mAudioManager.isSpeakerphoneOn();
		mMusicActive = mAudioHelper.isLocalOrRemoteMusicActive();
        RingerNotificationLink linkCheck = new RingerNotificationLink();
        mNotificationRingerLink = linkCheck.apply(mAudioManager);
        SystemVolume systemVolume = new SystemVolume();
        StreamResources.SystemStream.show(systemVolume.apply(mAudioManager));
        updateVibrateWhenRinging();
        loadSettings();
	}

    /**
     * Load necessary settings. Avoid using {@link android.util.Property#set(Object, Object)}
     * and call {@code super}. Called in {@link #onCreate()} automatically.
     * */
    public SettingsHelper loadSettings() {
        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        setMusicUri(settingsHelper.getProperty(VolumePanel.class, MUSIC_APP, musicUri)); // Music App?
        setAutoHideDuration(settingsHelper.getIntProperty(PopupWindow.class, TIMEOUT, getResources().getInteger(R.integer.volume_panel_timeout_default))); // 5 seconds default
        setRingerMode(settingsHelper.getIntProperty(VolumePanel.class, RINGER_MODE, Constants.RINGER_MODE_DEFAULT));
        setOneVolume(settingsHelper.getProperty(VolumePanel.class, MASTER_VOLUME, false));
        setLongPressActionDown(settingsHelper.getProperty(VolumePanel.class, ACTION_LONG_PRESS_VOLUME_DOWN, ""));
        setLongPressActionUp(settingsHelper.getProperty(VolumePanel.class, ACTION_LONG_PRESS_VOLUME_UP, ""));
        setColor(settingsHelper.getProperty(VolumePanel.class, COLOR, color));
        setBackgroundColor(settingsHelper.getProperty(VolumePanel.class, BACKGROUND, backgroundColor));
        setSeek(settingsHelper.getProperty(VolumePanel.class, SEEK, seek));
        setNoLongPress(settingsHelper.getProperty(VolumePanel.class, NO_LONG_PRESS, noLongPress));
        setHideFullscreen(settingsHelper.getProperty(VolumePanel.class, HIDE_FULLSCREEN, hideFullscreen));
        setHideCamera(settingsHelper.getProperty(VolumePanel.class, HIDE_CAMERA, hideCamera));
        setStretch(settingsHelper.getProperty(VolumePanel.class, STRETCH, stretch));
        setDefaultStream(settingsHelper.getIntProperty(VolumePanel.class, DEFAULT_STREAM, defaultStream));
        setLinkNotifRinger(settingsHelper.getProperty(VolumePanel.class, LINK_NOTIF_RINGER, linkNotifRinger));
        setTertiaryColor(settingsHelper.getProperty(VolumePanel.class, TERTIARY, tertiaryColor));
        setAlwaysExpanded(settingsHelper.getProperty(VolumePanel.class, ALWAYS_EXPANDED, alwaysExpanded));
        setFirstReveal(settingsHelper.getProperty(VolumePanel.class, FIRST_REVEAL, firstReveal));
        return settingsHelper;
    }

    /** @return True if the given setting has been set. */
    protected boolean has(Property<VolumePanel, ?> prop) {
        SettingsHelper settingsHelper = SettingsHelper.getInstance(getContext());
        return settingsHelper.hasProperty(VolumePanel.class, prop);
    }

    protected static final View.OnTouchListener noTouchListener = new View.OnTouchListener() {
        @Override public boolean onTouch(View view, MotionEvent motionEvent) { return true; }
    };

	@Override
	public void onDestroy() {
        Context context = getContext();
        try {
            if (null != mVolumeMediaReceiver)
                context.unregisterReceiver(mVolumeMediaReceiver);
        } catch (IllegalArgumentException iae) {
            LOGE("VolumePanel", "Could not unregister volume/ media receiver.", iae);
        }
        if (null != mAppTypeMonitor)
            mAppTypeMonitor.unregister(context);
        if (null != mPriorityModeObserver)
            mPriorityModeObserver.setListening(false);
        mPriorityModeObserver = null;
		mAudioManager = null;
        mAppTypeMonitor = null;
		mVolumeMediaReceiver = null;
        AudioHelper.freeResources();
        if (null != mAudioHelper)
            mAudioHelper.setHandler(null);
        if (null != mMediaProviderDelegate)
            mMediaProviderDelegate.destroy();
        mMediaProviderDelegate = null;
        mAudioHelper = null;
        try {
            if (registeredOtto) MainThreadBus.get().unregister(this);
        } catch (IllegalArgumentException e) {
            LOGE("VolumePanel", "Failed to unregister our VolumePanel from Otto.");
        }
		super.onDestroy();
	}

    @Override
    protected WindowManager.LayoutParams getWindowParams() {
		if (null == mWindowAttributes) {
			mWindowAttributes = getWindowLayoutParams();
			if (null != mWindowAttributes) {
                mWindowAttributes.type = ((isInteractive()) ?
                        LayoutParams.TYPE_SYSTEM_ERROR : LayoutParams.TYPE_SYSTEM_OVERLAY);
            }
		}
		return mWindowAttributes;
	}

    /** @return True if this VolumePanel responds to long press events. */
    public boolean respondsToLongPress() {
        return (!TextUtils.isEmpty(longPressActionDown) ||
                !TextUtils.isEmpty(longPressActionUp));
    }

    /** @return True if {@link me.barrasso.android.volume.MediaControllerService} is enabled. */
    public boolean isNotificationListenerRunning() {
        return Utils.isMediaControllerRunning(getContext());
    }

    public boolean isMusicActive() {
        return mMusicActive;
    }

    public String getLongPressActionUp() { return longPressActionUp; }
    public String getLongPressActionDown() { return longPressActionDown; }
    public void setLongPressActionUp(String actionUp) { longPressActionUp = actionUp; }
    public void setLongPressActionDown(String actionDown) { longPressActionDown = actionDown; }
    public boolean isStretch() { return stretch; }
    public void setStretch(boolean stretchIt) { stretch = stretchIt; }
    public void setDefaultStream(int stream) { defaultStream = stream; }
    public int getDefaultStream() { return defaultStream; }
    public boolean hasDefaultStream() { return (defaultStream >= 0); }
    public boolean isAlwaysExpanded() { return alwaysExpanded; }
    public void setAlwaysExpanded(boolean expand) { alwaysExpanded = expand; }

    protected int mKeyCodeDown = 0;
    protected boolean mIgnoreNextKeyUp = false;
    protected long mKeyTimeDown = 0;
	
	@Override
	public boolean onKey(View v, final int keyCode, KeyEvent event) {
        LOGI("VolumePanel", "onKey(" + keyCode + ")");

        // Don't handle ANYTHING when a call is in progress!
        if (mCallState != TelephonyManager.CALL_STATE_IDLE)
            return super.onKey(v, keyCode, event);

		switch (keyCode) {
			// Handle the DOWN + MULTIPLE action (holding down).
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            	final int adjust = ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) ?
                            	 AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER);
            	switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        // If another key was pressed while holding on to
                        // one volume key, we'll need to abort mission.
                        if (mKeyCodeDown != 0) {
                            mIgnoreNextKeyUp = true;
                            mHandler.removeMessages(MSG_VOLUME_LONG_PRESS);
                            return super.onKey(v, keyCode, event);
                        }

                        mKeyCodeDown = event.getKeyCode();
                        mKeyTimeDown = System.currentTimeMillis();
                        event.startTracking();

                        // NOTE: we'll allow long press events if we've set an action.
                        boolean callIdle = (mCallState == TelephonyManager.CALL_STATE_IDLE);
                        if (!noLongPress || hasLongPressAction(keyCode)) {
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(
                                    MSG_VOLUME_LONG_PRESS, event), ((callIdle && hasLongPressAction(keyCode)) ?
                                    mLongPressTimeout : mLongPressTimeout / 2));
                        }
                        break;
            		case KeyEvent.ACTION_UP:
            		case KeyEvent.ACTION_MULTIPLE:
                        boolean hasLongPress = mHandler.hasMessages(MSG_VOLUME_LONG_PRESS);
                        mHandler.removeMessages(MSG_VOLUME_LONG_PRESS);
                        boolean ignoreNextKeyUp = mIgnoreNextKeyUp;
                        mIgnoreNextKeyUp = false;
                        mKeyCodeDown = 0;

                        // We've been told to let this one go.
                        if (ignoreNextKeyUp || event.isCanceled()) {
                            mKeyTimeDown = 0;
                            return true;
                        }

                        if ((hasLongPress || noLongPress) && (System.currentTimeMillis() -
                                mKeyTimeDown) < mLongPressTimeout) {
                            mVolumeDirty = true;
                            mKeyTimeDown = 0;
                            if (!firstReveal || (firstReveal && isShowing())) {
                                adjustVolume(adjust);
                                show();
                            } else {
                                reveal();
                            }
                        }
            			break;
            	}
                break;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                switch (event.getAction()) {
                    case KeyEvent.ACTION_UP:
                        boolean mute = isMuted(STREAM_RING);
                        mAudioManager.setStreamMute(STREAM_RING, !mute);
                        mAudioManager.setStreamMute(STREAM_NOTIFICATION, !mute);
                        mVolumeDirty = true;
                        show();
                        break;
                }
                break;
        }

        return super.onKey(v, keyCode, event);
	}

    @Override
    public void closeSystemDialogs(String reason) {
        LOGI("VolumePanel", "closeSystemDialogs(" + reason + ')');
        if (VolumePanel.class.getSimpleName().equals(reason) ||
            getClass().getSimpleName().equals(reason) ||
            getName().equals(reason)) return;
        super.closeSystemDialogs(reason);
    }

    /** A Volume {@link android.view.KeyEvent} has been long pressed. */
    public void onVolumeLongPress(KeyEvent event) {
        if (null == event) return;

        // Set the action based on the KeyEvent.
        String longPressAction = null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                longPressAction = longPressActionUp;
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                longPressAction = longPressActionDown;
                break;
        }

        // If the event was volume up/ down, handle it. If the user is currently
        // in the middle of a call, only handle volume up/ down events.
        LOGI("VolumePanel", "onVolumeLongPress(" + event.getKeyCode() + ") action=" + longPressAction);
        boolean callIdle = (mCallState == TelephonyManager.CALL_STATE_IDLE);
        if (!TextUtils.isEmpty(longPressAction) && callIdle) {
            try {
                Intent action = Intent.parseUri(longPressAction, Intent.URI_INTENT_SCHEME);
                startActivity(action);
            } catch (URISyntaxException e) {
                LOGE("VolumePanel", "Error parsing long press action as an Intent.", e);
            }
        } else {
            // If we don't have a long press event, use this timeout as
            // a key timeout for volume manipulation.
            final int adjust = ((event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) ?
                    AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER);
            int flags = getFlags(lastStreamType);
            flags &= ~FLAG_PLAY_SOUND;
            flags &= ~FLAG_VIBRATE;
            // Adjust stream volume, but avoid making noises continuously.
            int stream = lastStreamType;
            if (hasDefaultStream()) stream = defaultStream;
            boolean ringerAffected = mAudioHelper.isStreamAffectedByRingerMode(stream);

            // Ringer mode transition for long-press actions
            /*if (ringerAffected) {
                int volume = getStreamVolume(stream);
                // If we're already at silent, stop listening.
                if ((adjust == ADJUST_LOWER) && (volume == 0) &&
                        (mRingerMode == AudioManager.RINGER_MODE_SILENT)) {
                    return;
                } else if (adjust == ADJUST_RAISE && volume == 0) {
                    int nextRinger = Utils.nextRingerMode(adjust, mRingerMode);
                    mAudioManager.setRingerMode(nextRinger);
                }
            }*/

            LOGI("VolumePanel", "[stream=" + stream + ", lastStream=" + lastStreamType +
                ", ringerAffected=" + ringerAffected + ']');

            adjustSuggestedStreamVolume(adjust, stream, flags);
            mVolumeDirty = true; // This is needed to show our volume change!
            mIgnoreNextKeyUp = false; // This is key to avoid infinite loops!
            if (!noLongPress || hasLongPressAction(event.getKeyCode())) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        MSG_VOLUME_LONG_PRESS, event), (mLongPressTimeout / 3));
            }
            show();
        }
    }

    public boolean hasLongPressAction() {
        return !(TextUtils.isEmpty(longPressActionDown) ||
                 TextUtils.isEmpty(longPressActionUp));
    }

    public boolean hasLongPressAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return !TextUtils.isEmpty(longPressActionDown);
            case KeyEvent.KEYCODE_VOLUME_UP:
                return !TextUtils.isEmpty(longPressActionUp);
        }
        return false;
    }

    /** {@link Handler} for audio/ media event handling. */
	/*package*/ final class VolumeMediaHandler extends Handler {
		public VolumeMediaHandler(Looper loopah) { super(loopah); }
		
		// All calls will be handles on the main looper (UI Thread).
        @SuppressWarnings("unchecked")
		@Override
        public void handleMessage(Message msg) {
            LOGI("VolumePanel", "handleMessage(" + VolumeMediaReceiver.getMsgName(msg.what) + ')');
            switch (msg.what) {
            	// obj: int[3], type, vol, prevVol
            	case MSG_VOLUME_CHANGED:
                    int[] vals = (int[]) msg.obj;
                    // If we just got another event in an unreasonably short period of time,
                    // close the system panel and don't handle this event.
                    final VolumeChangeInfo lastChangeInfo = mLastVolumeChange;
                    mLastVolumeChange = new VolumeChangeInfo(vals[0], vals[1], vals[2]);
                    if (null != lastChangeInfo && vals[1] == vals[2]) {
                        if ((System.currentTimeMillis() - lastChangeInfo.mEventTime) < 100) {
                            mAudioHelper.closeSystemDialogs(getContext(),
                                    VolumePanel.this.getClass().getSimpleName());
                            removeMessages(MSG_VOLUME_CHANGED);
                            return;
                        }
                    }
                    if (mVolumeDirty) {
                        onVolumeChanged(vals[0], vals[1], vals[2]);
                        mVolumeDirty = false;
                    }
            		break;
            	// arg1: ringer mode
            	case MSG_RINGER_MODE_CHANGED:
                    mLastRingerMode = mRingerMode;
                    mRingerMode = msg.arg1;
            		onRingerModeChange(mRingerMode);
            		break;
            	// arg1: stream type
            	// arg2: mute mode (boolean 1/0)
            	case MSG_MUTE_CHANGED:
                    onMuteChanged(msg.arg1, (msg.arg2 == 1));
            		break;
            	// arg1: speakerphone (0 == off, else on)
            	case MSG_SPEAKERPHONE_CHANGED:
            		onSpeakerphoneChange(!(msg.arg1 == 0));
            		break;
            	// arg1: vibrate type
            	// arg2: vibrate settings
            	case MSG_VIBRATE_MODE_CHANGED:
            		onVibrateModeChange(msg.arg1, msg.arg2);
            		break;
            	// obj: KeyEvent?
            	case MSG_MEDIA_BUTTON_EVENT:
            		break;
                // obj: KeyEvent
                case MSG_VOLUME_LONG_PRESS:
                    KeyEvent event = (KeyEvent) msg.obj;
                    if (noLongPress && !hasLongPressAction(event.getKeyCode())) return;
                    mIgnoreNextKeyUp = true;
                    onVolumeLongPress(event);
                    break;
                // obj: Pair<Metadata, PlaybackInfo>
                case MSG_PLAY_STATE_CHANGED:
                    // Only accept events if the delegate isn't active.
                    if (null == mMediaProviderDelegate || !mMediaProviderDelegate.isClientActive()) {
                        Pair<MediaMetadataCompat, PlaybackStateCompat> pair =
                                (Pair<MediaMetadataCompat, PlaybackStateCompat>) msg.obj;
                        onPlayStateChanged(pair);
                    }
                    break;
                // arg1: keycode
                case MSG_DISPATCH_KEYEVENT:
                    if (Utils.isMediaKeyCode(msg.arg1))
                        dispatchMediaKeyEvent(msg.arg1);
                    break;
                // arg1: call state
                case MSG_CALL_STATE_CHANGED:
                    mCallState = msg.arg1;
                    onCallStateChange(mCallState);
                    break;
                // obj: audio routes info
                case MSG_AUDIO_ROUTES_CHANGED:
                    AudioRoutesInfo info = (AudioRoutesInfo) msg.obj;
                    onAudioRoutesChanged(info);
                    break;
                case MSG_HEADSET_PLUG:
                    int state = msg.arg1;
                    onHeadsetPlug(state);
                    break;
                case MSG_USER_PRESENT:
                    locked = false;
                    onLockChange();
                    break;
                case MSG_ALARM_CHANGED:
                    onAlarmChanged();
                    break;
            }
        }
	}

    public static void attachPlaybackListeners(ViewGroup layout, View.OnClickListener listener) {
        attachPlaybackListeners(layout, listener, null);
    }

    public static void attachPlaybackListeners(ViewGroup layout,
                                               View.OnClickListener listener, View.OnLongClickListener longListener) {
        // To avoid strange issues with View.OnClick not being invoked, we'll proxy the call
        // to OnTouchListener and test for a click based on distance & time.
        Set<View> views = new HashSet<View>();
        views.add(layout.findViewById(R.id.media_previous));
        views.add(layout.findViewById(R.id.media_play_pause));
        views.add(layout.findViewById(R.id.media_next));
        OnTouchClickListener mListener = new OnTouchClickListener(listener, longListener);
        for (View view : views)
            if (null != view)
                view.setOnTouchListener(mListener);
    }

    protected static void setVisibilityBasedOnFlag(View view, long flags, long flag) {
        if ((flags & flag) != 0) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

	/**
	 * {@link View.OnClickListener} for media buttons.
	 * @see {@link R.id#media_previous}
	 * @see {@link R.id#media_play_pause}
	 * @see {@link R.id#media_next}
	 */
	/*package*/ final class MediaButtonClickListener implements View.OnClickListener {

        public MediaButtonClickListener() { }

		@Override
		public void onClick(View v) {
			LOGD("VolumePanel", "onClick(" + v.getId() + ')');
            Integer keyCode = null;
            switch (v.getId()) {
				case R.id.media_previous:
                    keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
					break;
				case R.id.media_play_pause:
                    keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
					break;
				case R.id.media_next:
                    keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
					break;
                default:
                    return;
			}

            onUserInteraction();
            Message.obtain(mHandler, MSG_DISPATCH_KEYEVENT, keyCode, 0).sendToTarget();
		}
	}

    /** Sends a media KeyEvent to the system. */
    public void dispatchMediaKeyEvent(final int keyCode) {
        LOGI("VolumePanel", "dispatchMediaKeyEvent(" + keyCode + ')');

        // NOTE: Avoid using the RemoteController methods for dispatching KeyEvents
        // this opens the media app and ruins the ambiance.
        mAudioHelper.dispatchMediaKeyEvent(getContext(), keyCode);
        onUserInteraction();
        onDispatchMediaKeyEvent(keyCode);
    }

    /** Same as {@link #show()}, but meant when volume key's aren't pressed. */
    public void reveal() {
        if (!isEnabled()) return;
        if (isShowing()) {
            onUserInteraction();
            return;
        }
        mVolumeDirty = true;
        if (hasDefaultStream()) {
            mAudioManager.adjustStreamVolume(defaultStream, ADJUST_SAME, 0);
        } else {
            mAudioManager.adjustVolume(ADJUST_SAME, 0);
        }
        show();
    }

    @Override
    public void show() {
        if (!isEnabled() || (mCallState != TelephonyManager.CALL_STATE_IDLE)) return;
        // If we've been told to hide, we'll do it.
        if (null != mLastVolumeChange && mLastVolumeChange.mStreamType ==
                STREAM_MUSIC && hideFullscreen && fullscreen) {
            LOGI("VolumePanel", "Not showing panel, hiding for fullscreen media.");
            return;
        }

        // Only show the panel if the screen is on.
        if (null != pWindowManager && pWindowManager.isScreenOn()) {
            if (null != mMediaProviderDelegate)
                mMediaProviderDelegate.acquire(getWindowWidth(), getWindowHeight());
            super.show();
            // NOTE: snapshots can be taken here, each time the panel is shown.
            // snapshot();
        }
    }

    @Override
    public void hide() {
        if (null != mMediaProviderDelegate)
            mMediaProviderDelegate.relinquish(false);
        if (null != mAudioHelper)
            mAudioHelper.forceVolumeControlStream(-1);
        super.hide();
    }

    protected boolean locked;

    // The lock screen/ keyguard state has changed.
    protected void onLockChange() { }
    public boolean isLocked() { return locked; }

    @Override
    public void screen(boolean on) {
        super.screen(on);
        if (!on) {
            locked = true;
            onLockChange();
        }
        if (null != mVolumeMediaReceiver)
            mVolumeMediaReceiver.setScreen(on);
        // NOTE: always give up keyguard lock when the screen turns off!
        if (!on && null != mKeyguardLock) {
            mKeyguardLock.reenableKeyguard();
            mKeyguardLock = null;
        }
        if (null != mHandler) mHandler.removeMessages(MSG_VOLUME_LONG_PRESS);
        mCallState = mTelephonyManager.getCallState();
        checkCallState();
    }

	/** @return This class' {@link ComponentName} for identification. */
	public ComponentName getComponentName() {
		return new ComponentName(pWindowManager.getContext().getPackageName(), getClass().getName());
	}

    /** Sets the color of this VolumePanel */
    public void setColor(final int mColor) { color = mColor; }

    /** @return The color of this VolumePanel. */
    public int getColor() { return color; }

    /** Sets the background color of this VolumePanel */
    public void setBackgroundColor(final int mColor) { backgroundColor = mColor; }

    /** @return The background color of this VolumePanel. */
    public int getBackgroundColor() { return backgroundColor; }

    /** Sets the tertiary color of this VolumePanel */
    public void setTertiaryColor(final int mColor) { tertiaryColor = mColor; }

    /** @return The tertiary color of this VolumePanel. */
    public int getTertiaryColor() { return tertiaryColor; }

    protected String musicUri;

    public String getMusicUri() { return musicUri; }
    public void setMusicUri(String uri) { musicUri = uri; }
    /*package*/ void abandonMusicUri() {
        MUSIC_APP.set(this, null);
    }

    public void setSeek(final boolean shouldSeek) { seek = shouldSeek; }
    public boolean isSeek() { return seek; }

    public boolean isOneVolume() { return oneVolume; }
    public void setOneVolume(final boolean master) {
        oneVolume = master;
        // When we create this panel, sync all volumes from the get-go.sss
        if (oneVolume) {
            mVolumeManager.adjustVolumeSync(AudioManager.ADJUST_SAME);
        }
    }
    public boolean isNoLongPress() { return noLongPress; }
    public void setNoLongPress(boolean mNoLongPress) {
        noLongPress = mNoLongPress;
        mIgnoreNextKeyUp = false;
        if (null != mHandler)
            mHandler.removeMessages(MSG_VOLUME_LONG_PRESS);
    }

    public void setHideCamera(boolean camera) { hideCamera = camera; }
    public boolean getHideCamera() { return hideCamera; }

    public void setHideFullscreen(boolean hideFS) { hideFullscreen = hideFS; }
    public boolean getHideFullscreen() { return hideFullscreen; }

    public void setFirstReveal(boolean reveal) { firstReveal = reveal; }
    public boolean getFirstReveal() { return firstReveal; }

    public int getRingerMode() { return ringerMode; }
    public void setRingerMode(final int mode) { ringerMode = mode; }

    public void setLinkNotifRinger(boolean link) { linkNotifRinger = link; }
    public boolean isLinkNotifRinger() { return linkNotifRinger; }

    protected String musicPackageName;

    /*package*/ boolean launchMusicApp() {
        // First, see if we've got a package name for the music app.
        // This is set generally from RemoteController.
        if (!TextUtils.isEmpty(musicPackageName)) {
            PackageManager mPM = getContext().getPackageManager();
            Intent launch = mPM.getLaunchIntentForPackage(musicPackageName);
            if (null != launch) {
                boolean success = startActivity(launch);
                if (success) return true;
                musicPackageName = null;
            }
        }

        // If the user has a preference, try it. Otherwise, fall to default.
        if (!TextUtils.isEmpty(musicUri)) {
            try {
                Intent music = Intent.parseUri(musicUri, Intent.URI_INTENT_SCHEME);
                boolean success = startActivity(music);
                if (success) return true;
                abandonMusicUri();
            } catch (URISyntaxException e) {
                LOGE("VolumePanel", "Error parsing music app URI.", e);
            }
        }

        Intent music = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
        return startActivity(music);
    }

    private KeyguardManager.KeyguardLock mKeyguardLock;

    /** Start an activity. Returns true if successful. */
    @SuppressWarnings("deprecation")
    protected boolean startActivity(Intent intent) {
        Context context = getContext();
        if (null == context || null == intent) return false;

        // Disable the Keyguard if necessary.
        KeyguardManager mKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (mKM.isKeyguardLocked() && !mKM.isKeyguardSecure()) {
            mKeyguardLock = mKM.newKeyguardLock(getName());
            mKeyguardLock.disableKeyguard();
        }

        try {
            // Necessary because we're in a background Service!
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return true;
            }
        } catch (ActivityNotFoundException anfe) {
            LOGE("VolumePanel", "Error launching Intent: " + intent.toString(), anfe);
        } catch (SecurityException se) {
            LOGE("VolumePanel", "Error launching Intent: " + intent.toString(), se);
            Toast.makeText(context, R.string.permission_error, Toast.LENGTH_SHORT).show();
        }

        return false;
    }

	// Localized from android.view.VolumePanel

    protected void onVolumeChanged(int streamType, int index, int prevIndex) {
        onVolumeChanged(streamType, index, prevIndex, false);
    }

    // Notified when the volume for a given stream changes.
    @SuppressWarnings("deprecation")
	protected void onVolumeChanged(int streamType, int index, int prevIndex, boolean ringerModeChange) {
        if (!mAudioHelper.isVoiceCapable() && (streamType == STREAM_RING)) {
            streamType = STREAM_NOTIFICATION;
        }

        mRingIsSilent = false;
        lastStreamType = streamType;
        // get max volume for progress bar
        int max = getStreamMaxVolume(streamType);

		LOGI("VolumePanel", "onVolumeChanged(" + VolumeManager.getStreamName(streamType) + "), index: "
                + index + ", max: " + max + ", prev: " + prevIndex + ", ringerModeChange: " + ringerModeChange);

        boolean headphonesActive = false;

        // TRACK: the volume change event.
        switch (streamType) {
            case AudioManager.STREAM_NOTIFICATION:
            case AudioManager.STREAM_RING:
                try {
                    Uri ringuri = RingtoneManager.getActualDefaultRingtoneUri(
                            getContext(), ((streamType == AudioManager.STREAM_NOTIFICATION) ?
                                    RingtoneManager.TYPE_NOTIFICATION : RingtoneManager.TYPE_RINGTONE)
                    );
                    if (ringuri == null) {
                        mRingIsSilent = true;
                    }
                } catch (SecurityException se) {
                    LOGE("VolumePanel", "Error checking the current ringtone.", se);
                }

                // Device has separate ringer/ notification, but wants them linked.
                // Make sure the volume actually changed, otherwise we'll end up in a loop.
                if (!ringerModeChange && prevIndex != index) {
                    if (linkNotifRinger && !mNotificationRingerLink) {
                        mVolumeDirty = false;
                        int newStream = ((streamType == AudioManager.STREAM_RING) ?
                                AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_RING);
                        LOGI("VolumePanel", "Linking notif-ringer volume: " +
                                VolumeManager.getStreamName(newStream) + ", index: " + index);
                        setStreamVolume(newStream, index);
                    }
                }

                // If we've hit volume down again, let's go silent.
                if (!ringerModeChange && prevIndex == index && index == 0 && lastDirection == ADJUST_LOWER) {
                    if (!Utils.HasChronicallyStupidWayToSetPriorityModeWhenItShouldBeSilentMode()) {
                        LOGI("VolumePanel", "Volume at 0, setting ringer mode (" + mRingerMode + ')');
                        if (mAudioHelper.hasVibrator()) {
                            if (mRingerMode == RINGER_MODE_VIBRATE)
                                mAudioManager.setRingerMode(RINGER_MODE_SILENT);
                            else if (mRingerMode == RINGER_MODE_NORMAL)
                                mAudioManager.setRingerMode(RINGER_MODE_VIBRATE);
                        } else {
                            if (mRingerMode == RINGER_MODE_NORMAL)
                                mAudioManager.setRingerMode(RINGER_MODE_SILENT);
                        }
                    }
                }
                break;
            case AudioManager.STREAM_MUSIC: {
                // Special case for when Bluetooth is active for music
                int devBit = (Constants.DEVICE_OUT_BLUETOOTH_A2DP |
                              Constants.DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                              Constants.DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER);
                int headBit = (Constants.DEVICE_OUT_WIRED_HEADSET |
                               Constants.DEVICE_OUT_WIRED_HEADPHONE);
                Integer ret = mAudioHelper.intMethod("getDevicesForStream", new Object[] { STREAM_MUSIC });
                if (mAudioManager.isBluetoothA2dpOn() || (null != ret && (ret & devBit) != 0)) {
                    setMusicIcon(MusicMode.BLUETOOTH);
                } else if (mAudioManager.isWiredHeadsetOn() || (null != ret && (ret & headBit) != 0)) {
                    setMusicIcon(MusicMode.HEADSET);
                    headphonesActive = true;
                } else {
                    setMusicIcon(MusicMode.DEFAULT);
                }
                break;
            }
            case AudioManager.STREAM_ALARM:

                break;
        }

        // Avoid issues with constant expression in switch-case statements.
        if (streamType == STREAM_BLUETOOTH_SCO ||
            streamType == AudioManager.STREAM_VOICE_CALL) {
                // For in-call voice call volume, there is no inaudible volume.
                // Rescale the UI control so the progress bar doesn't go all
                // the way to zero and don't show the mute icon.
                index = Math.min(++index, max);
        }

        // In "safe volume" mode, display a different icon.
        if (index >= mAudioHelper.getSafeMediaVolumeIndex() && headphonesActive &&
            mAudioHelper.isSafeMediaVolumeEnabled(getContext())) {
            setMusicIcon(MusicMode.SAFE_VOLUME);
        }

        // Map out stream volume for calibration.
        // mAudioFlingerProxy.mapStreamIndex(streamType, index);
        
        // Inform the UI of the change, but if we're managing the volume
        // as a "Master Volume," we'll need to handle it specially.
        mVolumeManager.setVolume(streamType, index);

        if (!isShowing() && null != mAudioHelper) {
            int stream = (streamType == STREAM_REMOTE_MUSIC) ? -1 : streamType;
            // when the stream is for remote playback, use -1 to reset the stream type evaluation
            mAudioHelper.forceVolumeControlStream(stream);
        }

        if (oneVolume) {
            // TODO: Sync all volumes when the screen is off. At present, the only
            // stream that informs us of a change when the screen is off is the music stream.
            if (!pWindowManager.isScreenOn() && streamType == STREAM_MUSIC) {
                mVolumeManager.syncToStream(STREAM_MUSIC);
            }
            LOGD("VolumePanel", mVolumeManager.toString());
            onStreamVolumeChange(STREAM_MASTER, mVolumeManager.getManagedVolume(),
                    mVolumeManager.getManagedMaxVolume());
        } else {
            LOGD("VolumePanel", mVolumeManager.toString());
            onStreamVolumeChange(streamType, index, max);
        }
    }

    protected static enum MusicMode {
        DEFAULT(R.drawable.ic_audio_vol, R.drawable.ic_audio_vol_mute, R.string.volume_icon_description_media),
        BLUETOOTH(R.drawable.ic_audio_bt, R.drawable.ic_audio_bt_mute, R.string.volume_icon_description_bluetooth),
        HEADSET(R.drawable.ic_action_headphones, R.drawable.ic_action_headphones_mute, R.string.volume_icon_description_media),
        SAFE_VOLUME(R.drawable.ic_dialog_alert, R.drawable.ic_dialog_alert, R.string.safe_volume);

        public int iconResId;
        public int iconMuteResId;
        public int descResId;

        private MusicMode(int iconResId, int iconMuteResId, int descResId) {
            this.iconResId = iconResId;
            this.iconMuteResId = iconMuteResId;
            this.descResId = descResId;
        }
    }

    /*
     * Switch between icons because Bluetooth music is same as music volume, but with different icons.
     */
    protected void setMusicIcon(MusicMode mode) {
        LOGI("VolumePanel", "setMusicIcon(" + mode.name() + ')');
        if (mode.iconResId > 0) StreamResources.MediaStream.setIconRes(mode.iconResId);
        if (mode.iconMuteResId > 0) StreamResources.MediaStream.setIconMuteRes(mode.iconMuteResId);
        if (mode.descResId > 0) StreamResources.MediaStream.setDescRes(mode.descResId);
    }

    /** @see android.media.IAudioRoutesObserver */
    public void onAudioRoutesChanged(AudioRoutesInfo info) {
        LOGI("VolumePanel", "onAudioRoutesChanged(" + info.toString() + ')');
    }

    //  ========== Media Router ==========

    protected CharSequence getStreamName(StreamResources resources) {
        return getContext().getString(resources.getDescRes());
    }

    // Simple PhoneStateListener to handle when a call begins and ends.
    private static class CallStateListener extends PhoneStateListener {
        private final Handler mHandler;
        public CallStateListener(Handler handler) { mHandler = handler; }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Message.obtain(mHandler, MSG_CALL_STATE_CHANGED, state).sendToTarget();
        }
    }

    // Used to detect when a remote audio stream is attached.
    @SuppressWarnings("unused")
    private static class MediaRouteListener extends MediaRouter.SimpleCallback {

        protected int mCurrentRouteType = MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE;
        protected MediaRouter.RouteInfo mCurrentRouteInfo;

        public MediaRouteListener() { super(); }
        public MediaRouter.RouteInfo getCurrentRoute() { return mCurrentRouteInfo; }

        /*package*/ void refreshRoute() {
            if (null == mCurrentRouteInfo) return;
            LOGI("MediaRouteListener", "refreshRoute(name=" +
                    mCurrentRouteInfo.getName() + ", type=" +
                    mCurrentRouteInfo.getPlaybackType() + ", stream=" +
                    mCurrentRouteInfo.getPlaybackStream() + ")");
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
            LOGD("MediaRouteListener", "onRouteSelected(" + type + ", " + info.getName() + ")");
            mCurrentRouteInfo = info;
            mCurrentRouteType = type;
            refreshRoute();
        }
        @Override
        public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
            LOGD("MediaRouteListener", "onRouteUnselected(" + type + ", " + info.getName() + ")");
            mCurrentRouteInfo = null;
            mCurrentRouteType = type;
            refreshRoute();
        }
        @Override
        public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo info) {
            LOGD("MediaRouteListener", "onRouteVolumeChanged(" + info.getName() + ")");
            mCurrentRouteInfo = info;
            refreshRoute();
        }
    }

	//	========== SeekBar ==========
	
	/**
	 * Notification that the progress level has changed. Clients can use the fromUser parameter
	 * to distinguish user-initiated changes from those that occurred programmatically.
	 */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        LOGD("VolumePanel", "onProgressChanged(" + progress + ")");
		final Object tag = seekBar.getTag();
        if (fromUser && tag instanceof StreamResources) {
            StreamResources sr = (StreamResources) tag;
            if (getStreamVolume(sr.getStreamType()) != progress) {
                setStreamVolume(sr.getStreamType(), progress);
                mVolumeDirty = true;
            }
        }
        onUserInteraction();
	}

	/**
	 * Notification that the user has started a touch gesture. Clients may want to use this
	 * to disable advancing the seekbar. 
	 */
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) { onUserInteraction(); }
	
	/**
	 * Notification that the user has finished a touch gesture. Clients may want to use this
	 * to re-enable advancing the seekbar. 
	 */
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		final Object tag = seekBar.getTag();
        if (tag instanceof StreamResources) {
            StreamResources sr = (StreamResources) tag;
            // because remote volume updates are asynchronous, AudioService might have received
            // a new remote volume value since the finger adjusted the slider. So when the
            // progress of the slider isn't being tracked anymore, adjust the slider to the last
            // "published" remote volume value, so the UI reflects the actual volume.
            if (sr.getStreamType() == STREAM_REMOTE_MUSIC) {
                seekBar.setProgress(getStreamVolume(STREAM_REMOTE_MUSIC));
            }
        }
        onUserInteraction();
	}

    /** Saves a snapshot of this VolumePanel. */
    @SuppressWarnings("unused")
    protected void snapshot() {
        // NOTE: this is performed synchronously. Aside from having no use in a production
        // app, it's just dang slow. NEVER call this unless it's for taking screenshots.
        Bitmap screen = Utils.loadBitmapFromViewCache(peekDecor());
        File dir = getContext().getCacheDir();
        File artFile = new File(dir, getName() + ".png");
        OutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(artFile));
            screen.compress(Bitmap.CompressFormat.PNG, 100, fos);
            screen.recycle();
        } catch (FileNotFoundException fnf) {
            LOGE("VolumePanel", "Cannot find " + artFile.getAbsolutePath(), fnf);
        } finally {
            try {
                if (null != fos)
                    fos.close();
            } catch (IOException ioe) {
                LOGE("VolumePanel", "Error closing snapshot OutputStream.", ioe);
            }
        }
    }
	
	//	========== ABSTRACT METHODS ==========

	/*
	 * @param ringerMode The current ringtone mode, one of {@link AudioManager#RINGER_MODE_NORMAL},
     *         {@link AudioManager#RINGER_MODE_SILENT}, or {@link AudioManager#RINGER_MODE_VIBRATE}.
     */
	public void onRingerModeChange(int ringerMode) {
        switch (ringerMode) {
            case RINGER_MODE_VIBRATE:
                StreamResources.RingerStream.setIconMuteRes(R.drawable.ic_audio_ring_notif_vibrate);
                StreamResources.NotificationStream.setIconMuteRes(R.drawable.ic_audio_ring_notif_vibrate);
                // NOTE: do a little vibrate when the ringer mode has been reached.
                if (isEnabled()) mAudioHelper.vibrate(VIBRATE_DURATION); // From VolumePanel
                break;
            case RINGER_MODE_SILENT:
            default:
                StreamResources.RingerStream.setIconMuteRes(R.drawable.ic_audio_phone_mute);
                StreamResources.NotificationStream.setIconMuteRes(R.drawable.ic_audio_notification_mute_am);
                break;
        }

        // Make sure we update immediately.
        if (null != mLastVolumeChange && isEnabled()) {
            onVolumeChanged(mLastVolumeChange.mStreamType,
                    mLastVolumeChange.mToVolume, mLastVolumeChange.mFromVolume, true);
        }
    }
	
	/** The speakerphone mode has changed (on or off). */
	public void onSpeakerphoneChange(boolean on) { }

    // NOTE: This is often handled as the MASTER mute has been set.
    /** The mute for a given volume stream has changed. */
    public void onMuteChanged(int streamType, boolean mute) { }
	
	/**
	 * The vibrate mode has changed for a given stream/ type.
	 * @see {@link AudioManager#EXTRA_VIBRATE_TYPE}
	 * @see {@link AudioManager#EXTRA_VIBRATE_SETTING}
	 */
	public void onVibrateModeChange(int vibrateType, int vibrateSetting) { }

    /** @see {@link android.telephony.TelephonyManager#getCallState()} */
    public int getCallState() {
        return mCallState;
    }

    // When the user gets a call while the screen was off, disable.
    protected void checkCallState() {
        LOGI("VolumePanel", "checkCallState()");
        if (mCallState != TelephonyManager.CALL_STATE_IDLE) {
            setEnabled(false);
        }
    }

    /**
     * Proxy for {@link android.telephony.PhoneStateListener}, to be notified when
     * the device's call state has changed.
     * @see #mCallState
     */
    public void onCallStateChange(int callState) {
        LOGI("VolumePanel", "onCallStateChange(" + callState + ')');
        mCallState = callState;
        mHandler.removeMessages(MSG_VOLUME_LONG_PRESS);

        // TRACK: when the user starts & ends a phone call.
        // When the user ends a call, let's take over volume again.
        if (callState == TelephonyManager.CALL_STATE_IDLE) {
            setEnabled(true);
        } else {
            setEnabled(false);
            hide();
        }
    }

	/**
     * The volume for a given stream has changed. Will not be called
     * if a client directly requests the change in volume.
     *
     * @param streamType The stream whose volume index should be set.
     * @param volume The volume index to set.
     * @param max The largest valid value for this stream's audio.
     */
	abstract void onStreamVolumeChange(int streamType, int volume, int max);
	
	/**
	 * @return True if the {@link VolumePanel} is interactive with the
	 * user (i.e. wants to accept touch events).
	 */
	abstract boolean isInteractive();

    /** @return True if this VolumePanel supports media playback. */
    public boolean supportsMediaPlayback() { return false; }

    protected Pair<MediaMetadataCompat, PlaybackStateCompat> mMediaInfo;

    /**
     * Notified when the play state has changed. Unfortunately, without a standardized API this
     * may or may not be called (with or without all fields), and may only work for certain
     * apps/ music players.
     */
    public void onPlayStateChanged(Pair<MediaMetadataCompat, PlaybackStateCompat> mediaInfo) {
        LOGI("VolumePanel", "onPlayStateChanged()");

        // If the event was just an update to the play state, handle accordingly.
        mMusicActive = RemoteControlCompat.isPlaying(mediaInfo.second);
        LOGI("VolumePanel", "isPlayState(playing=" + mMusicActive + ")");

        // Update the play state if we've been given one.
        if (null != mAudioHelper && null == mediaInfo.second)
            mMusicActive = mAudioHelper.isLocalOrRemoteMusicActive();

        // Update the music package name based on RemoteController/ magic.
        if (null != mediaInfo.first)
            musicPackageName = mediaInfo.first.getString(RemoteControlCompat.METADATA_KEY_PACKAGE);

        // TRACK: when the user starts and ends playing a song.
        mMediaInfo = mediaInfo;
    }

    protected void onHeadsetPlug(int state) {
        LOGI("VolumePanel", "onHeadsetPlug(" + state + ')');
    }

    protected String mCurrentPackageName;
    protected String mCurrentActivityClass;

    @Override
    public void setEnabled(boolean enable) {
        LOGI("VolumePanel", "setEnabled(" + enable + ")");
        super.setEnabled(enable);
    }

    public void enable() {
        setEnabled(!mAppTypeMonitor.doesPackageRespondToAny(mCurrentPackageName));
        checkCallState();
    }

    public void onTopAppChanged(VolumeAccessibilityService.TopApp app) {
        LOGI("VolumePanel", "onTopChanged(" + app.mCurrentPackage + '/' + app.mCurrentActivityClass + ')');
        mCurrentActivityClass = app.mCurrentActivityClass;
        mCurrentPackageName = app.mCurrentPackage;

        {
            // CASE: Snapchat is special because it's popular, but they don't use Intents.
            if ("com.snapchat.android".equals(mCurrentPackageName) &&
                "com.snapchat.android.LandingPageActivity".equals(mCurrentActivityClass)) {
                setEnabled(false);
                return;
            }

            // If the top app is a camera or alarm app, disable this volume panel.
            setEnabled(!mAppTypeMonitor.doesPackageRespondToAny(mCurrentPackageName));
        }
    }

    protected long mNextAlarmTimeMillis;

    protected void onAlarmChanged() {
        String nextAlarm = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        LOGI("VolumePanel", "onAlarmChanged(" + nextAlarm + ')');

        DateFormat format = new SimpleDateFormat("EEE hh:mm aa", Locale.US);
        try {
            Date date = format.parse(nextAlarm);
            mNextAlarmTimeMillis = date.getTime();
        } catch (ParseException e) {
            mNextAlarmTimeMillis = 0;
            LOGE("VolumePanel", "Error parsing next alarm from Settings.System", e);
        }
    }

    /**
     * @param minutes The number of minutes to check the time difference.
     * @return True if the alarm is within that range of time.
     */
    /*protected boolean isWithinMinutesOfNextAlarm(final int minutes) {
        if (mNextAlarmTimeMillis == 0) return false;
        long curTime = System.currentTimeMillis();
        long diff = Math.abs(mNextAlarmTimeMillis - curTime);
        long minute = (1000 * 60); // 60s * 1000ms
        return (diff < (minutes * minute));
    }*/

    public void onFullscreenChange(boolean fullscreen) {
        LOGI("VolumePanel", "onFullscreenChange(" + fullscreen + ')');
        this.fullscreen = fullscreen;
        if (isShowing() && fullscreen && hideFullscreen) hide();
    }

	/** @return The {@link WindowManager.LayoutParams} for displaying this volume panel. */
	abstract WindowManager.LayoutParams getWindowLayoutParams();

    /** Notified when {@link android.media.AudioManager#dispatchMediaKeyEvent(android.view.KeyEvent)} is invoked. */
    public void onDispatchMediaKeyEvent(int keyCode) { }

}