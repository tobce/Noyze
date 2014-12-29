package me.barrasso.android.volume;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.app.Service;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityService;
import android.graphics.BitmapFactory;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.content.SharedPreferences;

import com.levelup.logutils.FLog;
import com.squareup.otto.Produce;

import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.popup.FullscreenPopupWindow;
import me.barrasso.android.volume.popup.PopupWindow;
import me.barrasso.android.volume.popup.PopupWindowManager;
import me.barrasso.android.volume.popup.StatusBarVolumePanel;
import me.barrasso.android.volume.popup.VolumeBarPanel;
import me.barrasso.android.volume.popup.VolumePanel;
import me.barrasso.android.volume.utils.Constants;
import com.squareup.otto.MainThreadBus;

import java.util.HashSet;
import java.util.Set;

import me.barrasso.android.volume.utils.SettingsHelper;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;
import static me.barrasso.android.volume.LogUtils.LOGV;

/**
 * {@link AccessibilityService} meant to override the default behavior of
 * the system volume buttons (up/ down), improving the interface and adding
 * functionality (multi-stream control, media playback control, etc).
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public final class VolumeAccessibilityService extends AccessibilityService
	implements SharedPreferences.OnSharedPreferenceChangeListener {

    // A note from com.android.server.accessibility.AccessibilityManagerService#notifyKeyEventLocked
    //       Now we are giving the key events to the last enabled
    //       service that can handle them Ideally, the user should
    //       make the call which service handles key events. However,
    //       only one service should handle key events to avoid user
    //       frustration when different behavior is observed from
    //       different combinations of enabled accessibility services.

	public static final String TAG = LogUtils.makeLogTag(VolumeAccessibilityService.class);

	/** @return True if {@link VolumeAccessibilityService} is running. */
	public static boolean isEnabled(Context mContext) {
		return Utils.isAccessibilityServiceEnabled(mContext, VolumeAccessibilityService.class);
	}

	// ========== Constants ==========

	// Handler messages.
	private static final int MESSAGE_START				= 0x00000001;
	private static final int MESSAGE_SHUTDOWN			= 0x00000010;
    private static final int MESSAGE_KEY_EVENT			= 0x00000100;
    private static final int MESSAGE_TOP_ACTIVITY	    = 0x00001000;
    private static final int MESSAGE_TOP_PACKAGE	    = 0x00010000;
    private static final int MESSAGE_FULLSCREEN 	    = 0x00100000;

    // com.android.internal.app.ChooserActivity
    // com.android.systemui.recent.RecentsActivity
    private static final String ANDROID = "android";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String KEYGUARD = "com.android.keyguard";
    // private static final String GOOGLE_PLAY = "com.android.vending";
    // private static final String REVIEW_ACTIVITY_1 = "com.google.android.finsky.activities.RateReviewActivity";
    // private static final String REVIEW_ACTIVITY = "com.google.android.finsky.activities.ReviewsActivity";

    // ========== Instance Variables ==========

	protected PopupWindowManager pWindowManager;
	protected VolumePanel mVolumePanel;
    protected FullscreenPopupWindow mFullscreenWindow;

	protected final AccessibilityServiceInfo mInfo = new AccessibilityServiceInfo();

    protected String mCurrentActivityName = null;
    protected String mCurrentActivityClass = null;
    protected String mCurrentActivityPackage = null;

    private String PREF_TIMEOUT;
    private String PREF_MUSIC_APP;
    private String PREF_MASTER_VOLUME;
    private String PREF_RINGER_MODE;
    private String PREF_LONG_PRESS_DOWN;
    private String PREF_LONG_PRESS_UP;
    private String PREF_FOREGROUND_COLOR;
    private String PREF_BACKGROUND_COLOR;
    private String PREF_SEEK;
    private String PREF_NO_LONG_PRESS;
    private String PREF_HIDE_FULLSCREEN;
    private String PREF_HIDE_CAMERA;
    private String PREF_BAR_HEIGHT;
    private String PREF_STRETCH;
    private String PREF_DEFAULT_STREAM;
    private String PREF_LINK_NOTIF_RING;
    private String PREF_TERTIARY_COLOR;
    private String PREF_ALWAYS_EXPAND;
    private String PREF_FIRST_REVEAL;

    private final Set<String> blacklist = new HashSet<String>();

    private int disabledButtons = KeyEvent.KEYCODE_UNKNOWN;
	private SettingsHelper mPreferences;
	private Context mContext;
	private boolean isInfrastructureInitialized = false;

	// ========== AccessibilityManager ==========

    public static class TopApp {
        public String mCurrentPackage;
        public String mCurrentActivityClass;
    }

    @Produce
    public TopApp produceTopApp() {
        TopApp topApp = new TopApp();
        topApp.mCurrentActivityClass = mCurrentActivityClass;
        topApp.mCurrentPackage = mCurrentActivityPackage;
        return topApp;
    }

	/** {@link Handler} for executing messages on the service main thread. */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
            	//	obj: KeyEvent; from onKey
                case MESSAGE_KEY_EVENT:
                    final KeyEvent event = (KeyEvent) message.obj;
                    mVolumePanel.onKey(mVolumePanel.peekDecor(), event.getKeyCode(), event);
                    MainThreadBus.get().post(event);
                    break;
                case MESSAGE_START:
                	initVolumePanel();
                	break;
                case MESSAGE_SHUTDOWN:
                	// NOTE: do something async when we start/ stop?
                	break;
                case MESSAGE_TOP_ACTIVITY:
                case MESSAGE_TOP_PACKAGE:
                    TopApp app = produceTopApp();
                    if (null != mVolumePanel)
                        mVolumePanel.onTopAppChanged(app);
                    MainThreadBus.get().post(app);
                    if (message.what == MESSAGE_TOP_PACKAGE) {
                        if (blacklist.contains(mCurrentActivityPackage)) {
                            if (null != mVolumePanel) mVolumePanel.hide();
                        }
                    }
                    break;
                // arg1: boolean/ int, 1 == true, 0 == false
                case MESSAGE_FULLSCREEN:
                    boolean fullscreen = (message.arg1 == 1);
                    if (null != mVolumePanel)
                        mVolumePanel.onFullscreenChange(fullscreen);
                    break;
            }
        }
    };

	@Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (null == event) return; // WTF?
        if (event.isPassword()) return; // Nope, don't do it!

        LOGV(TAG, "onAccessibilityEvent(" + event.toString() + ')');

        // Android SystemUI Package, has become focused.
        if (SYSTEMUI.equals(event.getPackageName()) || ANDROID.equals(event.getPackageName()) &&
           (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            // NOTE: Android 5.0 introduces a Volume Panel that takes focus. In doing so,
            // it also pretends to be the top activity (but never tells us when it goes away).
            if (blacklist.contains(mCurrentActivityPackage)) return;
            if (null != mVolumePanel) {
                // Make sure we've waited several seconds before hiding.
                if ((System.currentTimeMillis() - mLastNotificationEventTime) >
                    mVolumePanel.getAutoHideDuration()) {
                    LOGI(TAG, "Hiding panel from package: " + event.getPackageName());
                    mVolumePanel.hide();
                    mLastNotificationEventTime = 0;
                }
            }
        }

        // Get and store information about the last event, current window, etc.
        switch (event.getEventType()) {
            // Window state changes, new window, app, dialog, etc.
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                final String mOldActivityPackage = mCurrentActivityPackage;
                final String mOldActivityClass = mCurrentActivityClass;

                mCurrentActivityClass = String.valueOf(event.getClassName());
                mCurrentActivityPackage = String.valueOf(event.getPackageName());
                if (event.getText().size() > 0) {
                    mCurrentActivityName = String.valueOf(event.getText().get(0));
                } else {
                    mCurrentActivityName = null;
                }

                // Has the top package changed?
                if (!TextUtils.isEmpty(mCurrentActivityPackage) &&
                        !mCurrentActivityPackage.equals(mOldActivityPackage)) {
                    Message.obtain(mHandler, MESSAGE_TOP_PACKAGE, mCurrentActivityPackage).sendToTarget();
                }

                // Has the top activity changed?
                if (!TextUtils.isEmpty(mCurrentActivityClass) &&
                        !mCurrentActivityClass.equals(mOldActivityClass)) {
                    Message.obtain(mHandler, MESSAGE_TOP_ACTIVITY, mCurrentActivityClass).sendToTarget();
                }
                break;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (null == event) return true; // WTF??

        // Make sure we're not in the middle of a phone call.
        if (null != mVolumePanel && mVolumePanel.getCallState() != TelephonyManager.CALL_STATE_IDLE)
            return super.onKeyEvent(event);

        final int flags = event.getFlags();
        final int code = event.getKeyCode();
        final boolean system = ((flags & KeyEvent.FLAG_FROM_SYSTEM) == KeyEvent.FLAG_FROM_SYSTEM);

        // Specifically avoid software keys or "fake" hardware buttons.
        if (((flags & KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD) ||
            ((flags & KeyEvent.FLAG_VIRTUAL_HARD_KEY) == KeyEvent.FLAG_VIRTUAL_HARD_KEY)) {
            return super.onKeyEvent(event);
        } else {
            // Specifically avoid handling certain keys. We never want to interfere
            // with them or harm performance in any way.
            switch (code) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_HOME:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_POWER:
                case KeyEvent.KEYCODE_SEARCH:
                    return super.onKeyEvent(event);
            }
        }

        if (!system) return super.onKeyEvent(event);

        LOGI(TAG, "--onKeyEvent(code=" + event.getKeyCode() + ", action=" + event.getAction() +
                ", topPackage=" + mCurrentActivityPackage + ", disabledButtons=" + disabledButtons + ')');

        // Check if we're supposed to disable Noyze for a Blacklisted app.
        if (blacklist.contains(mCurrentActivityPackage)) {
            if (null != mVolumePanel) mVolumePanel.setEnabled(false);
            return super.onKeyEvent(event);
        } else {
            // NOTE: we need a "safe" way to enable the volume panel that
            // takes into consideration its previous state.
            if (null != mVolumePanel) mVolumePanel.enable();
        }

        // If we're told to disable one or more of the volume buttons, do so (returning true consumes the event).
        if (disabledButtons == code) return true;
        // NOTE: KeyEvent#KEYCODE_VOLUME_DOWN + KeyEvent#KEYCODE_VOLUME_UP == KeyEvent_KEYCODE_U
        final int upAndDown = (KeyEvent.KEYCODE_VOLUME_DOWN + KeyEvent.KEYCODE_VOLUME_UP); // 49
        final int upSquared = KeyEvent.KEYCODE_VOLUME_UP * KeyEvent.KEYCODE_VOLUME_UP; // 576
        if (disabledButtons == upAndDown && Utils.isVolumeKeyCode(upAndDown - code)) return true;
        if (disabledButtons == upSquared && mVolumePanel != null && mVolumePanel.isLocked()) return true;
        if (disabledButtons == upSquared && KEYGUARD.equals(mCurrentActivityPackage)) return true;

        // Check if the volume panel has been disabled, or shouldn't handle this event (e.g. "safe volume").
        if (null != mVolumePanel && mVolumePanel.isEnabled()) {
            if (Utils.isVolumeKeyCode(code)) {
                Message.obtain(mHandler, MESSAGE_KEY_EVENT, event).sendToTarget(); // Run asynchronously
                return true;
            }
        }

    	return super.onKeyEvent(event);
    }

   	@Override
   	protected void onServiceConnected() {
   		if (isInfrastructureInitialized) { return; }
   		super.onServiceConnected();
        LOGI(TAG, "onServiceConnected(pid=" + android.os.Process.myPid() +
                ", uid=" + Process.myUid() +
                ", tid=" + Process.myTid() + ")");

        mContext = this;
        mPreferences = SettingsHelper.getInstance(mContext);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        initServiceInfo();
        initPrefNames();
        pWindowManager = new PopupWindowManager(mContext);

        updateDisabledButtons(mPreferences.getSharedPreferences());
        updateMediaCloak();
        updateBlacklist();
        isInfrastructureInitialized = true;
        peekIntoTheNexus();
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        MainThreadBus.get().register(this);
        mHandler.sendEmptyMessage(MESSAGE_START);
   	}

    protected void updateDisabledButtons(SharedPreferences prefs) {
        String value = prefs.getString(
                Constants.PREF_DISABLED_BUTTONS, String.valueOf(disabledButtons));
        try {
            disabledButtons = Integer.parseInt(value, 10);
        } catch (NumberFormatException nfe) {
            LOGE(TAG, "Error with " + Constants.PREF_DISABLED_BUTTONS + " formatting: " + value);
            disabledButtons = KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    // Start/ stop listening for full screen changes.
    protected void updateMediaCloak() {
        boolean mediaCloak = mPreferences.getProperty(
                VolumePanel.class, VolumePanel.HIDE_FULLSCREEN, false);
        LOGI(TAG, "updateMediaCloak(" + mediaCloak + ')');

        // Add a show immediately our fullscreen test view.
        if (mediaCloak) {
            mFullscreenWindow = new FullscreenPopupWindow(pWindowManager);
            mFullscreenWindow.addOnFullscreenChangeListener(mFSListener);
            mFullscreenWindow.show();
        } else {
            if (null != mFullscreenWindow) {
                mFullscreenWindow.onDestroy();
                mFullscreenWindow = null;
            }
        }
    }

    /*package*/ FullscreenPopupWindow.OnFullscreenChangeListener mFSListener =
            new FullscreenPopupWindow.OnFullscreenChangeListener() {
                @Override
                public void onFullscreenChange(boolean fullscreen) {
                    LOGI(TAG, "onFullscreenChange(" + fullscreen + ')');
                    Message.obtain(mHandler, MESSAGE_FULLSCREEN, (fullscreen) ? 1 : 0, 0).sendToTarget();
                }
            };

    /*package*/ void initPrefNames() {
        PREF_TIMEOUT = mPreferences.getName(PopupWindow.class, PopupWindow.TIMEOUT);
        PREF_MUSIC_APP = mPreferences.getName(VolumePanel.class, VolumePanel.MUSIC_APP);
        PREF_RINGER_MODE = mPreferences.getName(VolumePanel.class, VolumePanel.RINGER_MODE);
        PREF_MASTER_VOLUME = mPreferences.getName(VolumePanel.class, VolumePanel.MASTER_VOLUME);
        PREF_LONG_PRESS_DOWN = mPreferences.getName(VolumePanel.class, VolumePanel.ACTION_LONG_PRESS_VOLUME_DOWN);
        PREF_LONG_PRESS_UP = mPreferences.getName(VolumePanel.class, VolumePanel.ACTION_LONG_PRESS_VOLUME_UP);
        PREF_FOREGROUND_COLOR = mPreferences.getName(VolumePanel.class, VolumePanel.COLOR);
        PREF_BACKGROUND_COLOR = mPreferences.getName(VolumePanel.class, VolumePanel.BACKGROUND);
        PREF_SEEK = mPreferences.getName(VolumePanel.class, VolumePanel.SEEK);
        PREF_NO_LONG_PRESS = mPreferences.getName(VolumePanel.class, VolumePanel.NO_LONG_PRESS);
        PREF_HIDE_FULLSCREEN = mPreferences.getName(VolumePanel.class, VolumePanel.HIDE_FULLSCREEN);
        PREF_HIDE_CAMERA = mPreferences.getName(VolumePanel.class, VolumePanel.HIDE_CAMERA);
        PREF_BAR_HEIGHT = mPreferences.getName(VolumeBarPanel.class, VolumeBarPanel.BAR_HEIGHT);
        PREF_STRETCH = mPreferences.getName(VolumePanel.class, VolumePanel.STRETCH);
        PREF_DEFAULT_STREAM = mPreferences.getName(VolumePanel.class, VolumePanel.DEFAULT_STREAM);
        PREF_LINK_NOTIF_RING = mPreferences.getName(VolumePanel.class, VolumePanel.LINK_NOTIF_RINGER);
        PREF_TERTIARY_COLOR = mPreferences.getName(VolumePanel.class, VolumePanel.TERTIARY);
        PREF_ALWAYS_EXPAND = mPreferences.getName(VolumePanel.class, VolumePanel.ALWAYS_EXPANDED);
        PREF_FIRST_REVEAL = mPreferences.getName(VolumePanel.class, VolumePanel.FIRST_REVEAL);
    }

    public static class VolumePanelChangeEvent {
        private final String vPanelName;
        private final boolean supportsMedia;
        public VolumePanelChangeEvent(VolumePanel vPanel) {
            vPanelName = (null == vPanel) ? null : vPanel.getClass().getSimpleName();
            supportsMedia = (null != vPanel && vPanel.supportsMediaPlayback());
        }
        public VolumePanelChangeEvent(String name, boolean media) {
            vPanelName = name;
            supportsMedia = media;
        }
        public String getName() { return vPanelName; }
        public boolean supportsMediaPlayback() { return supportsMedia; }
    }

    public VolumePanelChangeEvent produceVolumePanelChangeEvent() {
        return new VolumePanelChangeEvent(mVolumePanel);
    }

    /** Initialize the VolumePanel based on SharedPreferences values. */
   	/*package*/ void initVolumePanel() {

        // Destroy the current VolumePanel if one exists.
        if (mVolumePanel != null)
            mVolumePanel.onDestroy();
        mVolumePanel = null;

        // Check preferences for the preferred VolumePanel and set it.
        final String volumePanelName = mPreferences.get(Constants.PREF_VOLUME_PANEL, StatusBarVolumePanel.TAG);
        VolumePanelInfo volumePanelInfo = Constants.getInfoForName(volumePanelName);
        LOGI(TAG, "Initializing VolumePanel: " + volumePanelName);

        // If we've removed a theme or some similar issue, default to the status bar theme.
        if (null == volumePanelInfo) volumePanelInfo = StatusBarVolumePanel.VOLUME_PANEL_INFO;
        mVolumePanel = volumePanelInfo.getInstance(pWindowManager);
        if (null != mVolumePanel) {
            ((MainThreadBus) MainThreadBus.get()).postSafely(produceVolumePanelChangeEvent());
        }
    }

    /*package*/ void updateBlacklist() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String packs = prefs.getString(Constants.PREF_BLACKLIST, "");
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(packs);
        blacklist.clear();
        while (splitter.hasNext()) {
            blacklist.add(splitter.next());
        }
        LOGI(TAG, "updateBlacklist(" + blacklist.size() + ')');
    }

   	@Override
   	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boolean handled = true;
        if (null == mVolumePanel && !Constants.PREF_FOREGROUND.equals(key)) return;

        if (Constants.PREF_VOLUME_PANEL.equals(key)) {
            initVolumePanel();
        } else if (PREF_TIMEOUT.equals(key)) {
            try {
                mVolumePanel.setAutoHideDuration(
                        mPreferences.getIntProperty(PopupWindow.class, PopupWindow.TIMEOUT, 5000));
            } catch (NumberFormatException e) {
                LOGE(TAG, "Error parsing " + PopupWindow.TIMEOUT.getName() + '.', e);
            }
        } else if (PREF_MUSIC_APP.equals(key)) {
            mVolumePanel.setMusicUri(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.MUSIC_APP, mVolumePanel.getMusicUri()));
        } else if (PREF_MASTER_VOLUME.equals(key)) {
            mVolumePanel.setOneVolume(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.MASTER_VOLUME, mVolumePanel.isOneVolume()));
        } else if (PREF_RINGER_MODE.equals(key)) {
            mVolumePanel.setRingerMode(mPreferences.getIntProperty(
                    VolumePanel.class, VolumePanel.RINGER_MODE, mVolumePanel.getRingerMode()));
        } else if (PREF_LONG_PRESS_DOWN.equals(key)) {
            mVolumePanel.setLongPressActionDown(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.ACTION_LONG_PRESS_VOLUME_DOWN, mVolumePanel.getLongPressActionDown()));
        } else if (PREF_LONG_PRESS_UP.equals(key)) {
            mVolumePanel.setLongPressActionUp(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.ACTION_LONG_PRESS_VOLUME_UP, mVolumePanel.getLongPressActionUp()));
        } else if (PREF_FOREGROUND_COLOR.equals(key)) {
            mVolumePanel.setColor(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.COLOR, mVolumePanel.getColor()));
        } else if (PREF_BACKGROUND_COLOR.equals(key)) {
            mVolumePanel.setBackgroundColor(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.BACKGROUND, mVolumePanel.getBackgroundColor()));
        } else if (Constants.PREF_FOREGROUND.equals(key)) {
            peekIntoTheNexus();
        } else if (PREF_SEEK.equals(key)) {
            mVolumePanel.setSeek(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.SEEK, mVolumePanel.isSeek()));
        } else if (PREF_NO_LONG_PRESS.equals(key)) {
            mVolumePanel.setNoLongPress(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.NO_LONG_PRESS, mVolumePanel.isNoLongPress()));
        } else if (PREF_HIDE_FULLSCREEN.equals(key)) {
            mVolumePanel.setHideFullscreen(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.HIDE_FULLSCREEN, mVolumePanel.getHideFullscreen()));
            updateMediaCloak();
        } else if (PREF_HIDE_CAMERA.equals(key)) {
            mVolumePanel.setHideCamera(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.HIDE_CAMERA, mVolumePanel.getHideCamera()));
        } else if (PREF_BAR_HEIGHT.equals(key)) {
            // VolumeBarPanel specific setting for bar height
            if (mVolumePanel instanceof VolumeBarPanel) {
                VolumeBarPanel bar = (VolumeBarPanel) mVolumePanel;
                bar.setBarHeight(mPreferences.getIntProperty(
                        VolumeBarPanel.class, VolumeBarPanel.BAR_HEIGHT, bar.getBarHeight()));
            }
        } else if (Constants.PREF_DISABLED_BUTTONS.equals(key)) {
            updateDisabledButtons(sharedPreferences);
        } else if (PREF_STRETCH.equals(key)) {
            mVolumePanel.setStretch(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.STRETCH, mVolumePanel.isStretch()));
        } else if (PREF_DEFAULT_STREAM.equals(key)) {
            mVolumePanel.setDefaultStream(mPreferences.getIntProperty(
                    VolumePanel.class, VolumePanel.DEFAULT_STREAM, mVolumePanel.getDefaultStream()));
        } else if (Constants.PREF_BLACKLIST.equals(key)) {
            updateBlacklist();
        } else if (PREF_LINK_NOTIF_RING.equals(key)) {
            mVolumePanel.setLinkNotifRinger(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.LINK_NOTIF_RINGER, mVolumePanel.isLinkNotifRinger()));
        } else if (PREF_TERTIARY_COLOR.equals(key)) {
            mVolumePanel.setTertiaryColor(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.TERTIARY, mVolumePanel.getTertiaryColor()));
        } else if (PREF_ALWAYS_EXPAND.equals(key)) {
            mVolumePanel.setAlwaysExpanded(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.ALWAYS_EXPANDED, mVolumePanel.isAlwaysExpanded()));
        } else if (PREF_FIRST_REVEAL.equals(key)) {
            mVolumePanel.setFirstReveal(mPreferences.getProperty(
                    VolumePanel.class, VolumePanel.FIRST_REVEAL, mVolumePanel.getFirstReveal()));
        } else {
            handled = false;
        }

        LOGI(TAG, "onSharedPreferenceChanged(" + key + "), handled=" + handled);
   	}
   	
   	protected void initServiceInfo() {
   		mInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        mInfo.notificationTimeout = 100;
        
        // This is the KEY (to KeyEvents)! Sweet deal.
        mInfo.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;

   		// We'll respond with a popup (visual), and possibly a noise (audible)
   		// and/ or a vibration (haptic). No spoken feedback here!
        mInfo.feedbackType = (AccessibilityServiceInfo.FEEDBACK_VISUAL	|
        					  AccessibilityServiceInfo.FEEDBACK_AUDIBLE	|
        					  AccessibilityServiceInfo.FEEDBACK_HAPTIC	);
        
        setServiceInfo(mInfo);
   	}

    protected Notification mNotification;

    protected void enterTheNexus() {
        // One-off to create the notification and display it.
        if (null == mNotification) {
            Notification.Builder builder = new Notification.Builder(getApplicationContext());
            /*Intent home = getPackageManager().getLaunchIntentForPackage(getPackageName());
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);*/
            Intent accessibility = new Intent(getApplicationContext(), getClass());
            accessibility.putExtra("show", true);
            PendingIntent intent = PendingIntent.getService(getApplicationContext(), 0, accessibility, 0);
            builder.setSmallIcon(R.drawable.ic_notif)
                   .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_notif_large))
                   .setContentTitle(getString(R.string.app_name))
                   .setContentText(getString(R.string.tap_configure))
                   .setPriority(Notification.PRIORITY_MIN)
                   .setContentIntent(intent);
            // addPriorityActions(builder);
            mNotification = builder.build();
        }

        startForeground(mNotification.hashCode(), mNotification);
    }

    /*@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void addPriorityActions(Notification.Builder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        PendingIntent intentNone = PendingIntent.getService(getApplicationContext(), 0,
                MediaControllerService.getInterruptionFilterRequestIntent(this, NotificationListenerService.INTERRUPTION_FILTER_NONE), PendingIntent.FLAG_ONE_SHOT);
        PendingIntent intentAll = PendingIntent.getService(getApplicationContext(), 0,
                MediaControllerService.getInterruptionFilterRequestIntent(this, NotificationListenerService.INTERRUPTION_FILTER_ALL), PendingIntent.FLAG_ONE_SHOT);
        PendingIntent intentPriority = PendingIntent.getService(getApplicationContext(), 0,
                MediaControllerService.getInterruptionFilterRequestIntent(this, NotificationListenerService.INTERRUPTION_FILTER_PRIORITY), PendingIntent.FLAG_ONE_SHOT);
        builder.addAction(R.drawable.empty_pixel, getString(R.string.zen_mode_none), intentNone);
        builder.addAction(R.drawable.empty_pixel, getString(R.string.zen_mode_priority), intentPriority);
        builder.addAction(R.drawable.empty_pixel, getString(R.string.zen_mode_all), intentAll);
    }*/

    protected void peekIntoTheNexus() {
        // Make/ remove the notification based on this setting.
        boolean foreground = mPreferences.get(Constants.PREF_FOREGROUND, false);
        if (foreground) enterTheNexus();
        else            exitTheNexus();
    }

    protected void exitTheNexus() {
        stopForeground(true);
    }
    
    /** Destroy this {@link Service}. NOT reversible! */
    private void destroy() {
        FLog.clear();
    	if (isInfrastructureInitialized) {
    		if (null != mVolumePanel) mVolumePanel.onDestroy();
            if (null != mFullscreenWindow) mFullscreenWindow.onDestroy();
            if (null != pWindowManager) pWindowManager.destroy();
    		mPreferences.unregisterOnSharedPreferenceChangeListener(this);
            mHandler.sendEmptyMessage(MESSAGE_SHUTDOWN);
            MainThreadBus.get().unregister(this);
            isInfrastructureInitialized = false;
            mContext = null;
            mPreferences = null;
            pWindowManager = null;
            mVolumePanel = null;
            mFullscreenWindow = null;
        }
    }

    private long mLastNotificationEventTime;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOGI(TAG, "--onStartComment(" + intent + ", " + flags + ", " + startId + ')');
        // If this app is sending us a request, show the volume panel!
        ComponentName thisComponent = new ComponentName(getApplicationContext(), getClass());
        if (intent != null && thisComponent.equals(intent.getComponent())) {
            mLastNotificationEventTime = System.currentTimeMillis();
            if (null != mVolumePanel) {
                mVolumePanel.reveal();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onInterrupt() {
        LOGI(TAG, "--onInterrupt()");
    }
   	
   	@Override
    public boolean onUnbind(Intent intent) {
        destroy();
        LOGI(TAG, "--onUnbind()");
        return super.onUnbind(intent);
    }
    
    @Override
    public void onDestroy() {
    	destroy();
        LOGI(TAG, "--onDestroy()");
    	super.onDestroy();
    }
}