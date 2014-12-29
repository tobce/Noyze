package me.barrasso.android.volume.utils;

import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.RemoteControlClient;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.KeyEvent;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.media.VolumePanelInfo;
import me.barrasso.android.volume.popup.BlackberryVolumePanel;
import me.barrasso.android.volume.popup.CircleVolumePanel;
import me.barrasso.android.volume.popup.HeadsUpVolumePanel;
import me.barrasso.android.volume.popup.InvisibleVolumePanel;
import me.barrasso.android.volume.popup.OppoVolumePanel;
import me.barrasso.android.volume.popup.ParanoidVolumePanel;
import me.barrasso.android.volume.popup.StatusBarPlusVolumePanel;
import me.barrasso.android.volume.popup.StatusBarVolumePanel;
import me.barrasso.android.volume.popup.UberVolumePanel;
import me.barrasso.android.volume.popup.VolumeBarPanel;
import me.barrasso.android.volume.popup.VolumePanel;
import me.barrasso.android.volume.popup.WPVolumePanel;
import me.barrasso.android.volume.popup.iOSVolumePanel;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.HashSet;

/**
 * Constants and related methods to make referencing data more convenient.
 */
public final class Constants {

    /** True if we play by Google's FUCKED UP rules (they won't even give a reason)! */
    public static final boolean AGGRESSIVE_COPYRIGHT = true;
	
	private static Set<VolumePanelInfo> VOLUME_PANELS() {
		// Add all VolumePanel subclasses here for them to be referenced.
		// NOTE: once order is set, it should remain so to prevent issues
		// with XML/ resource arrays.
        final Set<VolumePanelInfo> VOLUME_PANELS = new HashSet<>();
		VOLUME_PANELS.add(InvisibleVolumePanel.VOLUME_PANEL_INFO);
		VOLUME_PANELS.add(StatusBarVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(HeadsUpVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(VolumeBarPanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(ParanoidVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(UberVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(StatusBarPlusVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(OppoVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(BlackberryVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(CircleVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(WPVolumePanel.VOLUME_PANEL_INFO);
        VOLUME_PANELS.add(iOSVolumePanel.VOLUME_PANEL_INFO);
        return VOLUME_PANELS;
	}
				 
	/** @return A {@link Set} of all {@link VolumePanelInfo}. */
	public static Set<VolumePanelInfo> getVolumePanelInfoSet() {
		return VOLUME_PANELS();
	}

    /** @return The info for a given VolumePanel subclass name. */
    @SuppressWarnings("unchecked")
    public static VolumePanelInfo<? extends VolumePanel> getInfoForName(String name) {
        Set<VolumePanelInfo> infos = VOLUME_PANELS();
        for (VolumePanelInfo<? extends VolumePanel> info : infos)
            if (info.prefName.equals(name)) return info;
        return StatusBarVolumePanel.VOLUME_PANEL_INFO;
    }
	
	/** @return The info for a given VolumePanel subclass. */
    @SuppressWarnings("unchecked")
	public static VolumePanelInfo getInfoForClass(Class<? extends VolumePanel> pClass) {
        Set<VolumePanelInfo> infos = VOLUME_PANELS();
        for (VolumePanelInfo<? extends VolumePanel> info : infos)
            if (info.clazz.equals(pClass)) return info;
        return null;
	}
        
    /**
     * {@link android.content.SharedPreferences} key for a String that
     * corresponds to the full class name of the current volume panel.
     */
    public static final String PREF_VOLUME_PANEL = VolumePanel.class.getSimpleName();

    /**
     * True if the user opts to display a Notification to enter foreground.
     */
    public static final String PREF_FOREGROUND = "service_foreground";

    public static final String PREF_REPORTING = "anonymous_reporting";
    public static final String PREF_CHANGELOG = "changelog";
    public static final String PREF_DISABLED_BUTTONS = "VolumeService_disabledButtons";
    public static final String PREF_BLACKLIST = "VolumeService_blacklist";

    /**
     * Preference key for the "Send debug log" item.
     */
    public static final String PREF_DEBUG_LOG = "debug_log";

    public static final int RINGER_MODE_DEFAULT = 2;
    public static final int RINGER_MODE_SILENT = AudioManager.RINGER_MODE_SILENT; // 0

    @Deprecated public static final int RINGER_MODE_VIBRATE = AudioManager.RINGER_MODE_VIBRATE; // 1
    @Deprecated public static final int RINGER_MODE_RING = 3;
    @Deprecated public static final int RINGER_MODE_RING_VIBRATE = 4;

    // Volume key events; nothing more, nothing less.
    public static final int[] VOLUME_KEY_EVENTS = new int[] {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE
    };

    public static final String MIUI_SERVICE_NAME = "miui.os.servicemanager";
    public static final String MIUI_SERVICE_DESCRIPTOR = "android.os.IMiuiServiceManager";

    // Constants for AlarmManager and knowing when a system alarm is set
    public static final String ALARM_SET_EXTRA = "alarmSet";
    private static final String CHANGED_FIELD = "ACTION_ALARM_CHANGED";
    private static final String ACTION_ALARM_CHANGED_DEFAULT = "android.intent.action.ALARM_CHANGED";
    public static final String ACTION_ALARM_CHANGED;

    static {
        String mAction = ACTION_ALARM_CHANGED_DEFAULT;
        try {
            final Field mField = Intent.class.getDeclaredField(CHANGED_FIELD);
            if (mField != null) {
                mField.setAccessible(true);
                final String mActionValue = (String) mField.get(null);
                if (mActionValue != null) mAction = mActionValue;
            }
        } catch (Throwable t) {
            mAction = ACTION_ALARM_CHANGED_DEFAULT;
        }

        ACTION_ALARM_CHANGED = mAction;
    }

    // Package name for Sony Xperia theme manager
    public static final String SEMC_UXP = "com.sonyericsson.uxp";

    // ========== AudioManager ==========
    
    /**
     * @hide Broadcast intent when the volume for a particular stream type changes.
     * Includes the stream, the new volume and previous volumes
     *
     * @see #EXTRA_VOLUME_STREAM_TYPE
     * @see #EXTRA_VOLUME_STREAM_VALUE
     * @see #EXTRA_PREV_VOLUME_STREAM_VALUE
     */
    public static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

    /**
     * @hide Broadcast intent when the master volume changes.
     * Includes the new volume
     *
     * @see #EXTRA_MASTER_VOLUME_VALUE
     * @see #EXTRA_PREV_MASTER_VOLUME_VALUE
     */
    public static final String MASTER_VOLUME_CHANGED_ACTION =
            "android.media.MASTER_VOLUME_CHANGED_ACTION";

    /**
     * @hide Broadcast intent when the master mute state changes.
     * Includes the the new volume
     *
     * @see #EXTRA_MASTER_VOLUME_MUTED
     */
    public static final String MASTER_MUTE_CHANGED_ACTION =
            "android.media.MASTER_MUTE_CHANGED_ACTION";

    /**
     * @hide The stream type for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    /**
     * @hide The volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_VALUE =
        "android.media.EXTRA_VOLUME_STREAM_VALUE";

    /**
     * @hide The previous volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
        "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    /**
     * @hide The new master volume value for the master volume changed intent.
     * Value is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_MASTER_VOLUME_VALUE";

    /**
     * @hide The previous master volume value for the master volume changed intent.
     * Value is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_PREV_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_PREV_MASTER_VOLUME_VALUE";

    /**
     * @hide The new master volume mute state for the master mute changed intent.
     * Value is boolean
     */
    public static final String EXTRA_MASTER_VOLUME_MUTED =
            "android.media.EXTRA_MASTER_VOLUME_MUTED";

    /**
     * Sticky broadcast intent action indicating that the bluetoooth SCO audio
     * connection state has changed. The intent contains on extra {@link #EXTRA_SCO_AUDIO_STATE}
     * indicating the new state which is either {@link #SCO_AUDIO_STATE_DISCONNECTED}
     * or {@link #SCO_AUDIO_STATE_CONNECTED}
     *
     * @deprecated Use  {@link #ACTION_SCO_AUDIO_STATE_UPDATED} instead
     */
    public static final String ACTION_SCO_AUDIO_STATE_CHANGED =
            "android.media.SCO_AUDIO_STATE_CHANGED";

    /**
     * Sticky broadcast intent action indicating that the bluetoooth SCO audio
     * connection state has been updated.
     * <p>This intent has two extras:
     * <ul>
     *   <li> {@link #EXTRA_SCO_AUDIO_STATE} - The new SCO audio state. </li>
     *   <li> {@link #EXTRA_SCO_AUDIO_PREVIOUS_STATE}- The previous SCO audio state. </li>
     * </ul>
     * <p> EXTRA_SCO_AUDIO_STATE or EXTRA_SCO_AUDIO_PREVIOUS_STATE can be any of:
     * <ul>
     *   <li> {@link #SCO_AUDIO_STATE_DISCONNECTED}, </li>
     *   <li> {@link #SCO_AUDIO_STATE_CONNECTING} or </li>
     *   <li> {@link #SCO_AUDIO_STATE_CONNECTED}, </li>
     * </ul>
     */
    public static final String ACTION_SCO_AUDIO_STATE_UPDATED =
            "android.media.ACTION_SCO_AUDIO_STATE_UPDATED";

    /**
     * Extra for intent {@link #ACTION_SCO_AUDIO_STATE_CHANGED} or
     * {@link #ACTION_SCO_AUDIO_STATE_UPDATED} containing the new bluetooth SCO connection state.
     */
    public static final String EXTRA_SCO_AUDIO_STATE =
            "android.media.extra.SCO_AUDIO_STATE";

    /**
     * Extra for intent {@link #ACTION_SCO_AUDIO_STATE_UPDATED} containing the previous
     * bluetooth SCO connection state.
     */
    public static final String EXTRA_SCO_AUDIO_PREVIOUS_STATE =
            "android.media.extra.SCO_AUDIO_PREVIOUS_STATE";

    /**
     * Value for extra EXTRA_SCO_AUDIO_STATE or EXTRA_SCO_AUDIO_PREVIOUS_STATE
     * indicating that the SCO audio channel is not established
     */
    public static final int SCO_AUDIO_STATE_DISCONNECTED = 0;
    /**
     * Value for extra {@link #EXTRA_SCO_AUDIO_STATE} or {@link #EXTRA_SCO_AUDIO_PREVIOUS_STATE}
     * indicating that the SCO audio channel is established
     */
    public static final int SCO_AUDIO_STATE_CONNECTED = 1;
    /**
     * Value for extra EXTRA_SCO_AUDIO_STATE or EXTRA_SCO_AUDIO_PREVIOUS_STATE
     * indicating that the SCO audio channel is being established
     */
    public static final int SCO_AUDIO_STATE_CONNECTING = 2;
    /**
     * Value for extra EXTRA_SCO_AUDIO_STATE indicating that
     * there was an error trying to obtain the state
     */
    public static final int SCO_AUDIO_STATE_ERROR = -1;

    // Found in ParanoidAndroid... maybe elsewhere?
    public static final String ACTION_VOLUMEPANEL_SHOWN = "android.view.volumepanel.SHOWN";
    public static final String ACTION_VOLUMEPANEL_HIDDEN = "android.view.volumepanel.HIDDEN";

    // Oppo (probably not others)
    private static final String ACTION_MEDIA_VOLUME_MODE_CHANGED = "action_media_volume_mode_changed";
    private static final String ACTION_SKIN_CHANGED = "android.intent.action.SKIN_CHANGED";
    private static final String ACTION_SYSTEM_VOLUME_MODE_CHANGED = "action_system_volume_mode_changed";

    /**
     * Bluetooth Headset volume. This is used internally, changing this value will
     * not change the volume. See AudioManager.
     */
    public static final String VOLUME_BLUETOOTH_SCO = "volume_bluetooth_sco";

    /**
     * Master volume (float in the range 0.0f to 1.0f).
     * @hide
     */
    public static final String VOLUME_MASTER = "volume_master";

    /**
     * Master volume mute (int 1 = mute, 0 = not muted).
     *
     * @hide
     */
    public static final String VOLUME_MASTER_MUTE = "volume_master_mute";

    /**
     * Whether the notifications should use the ring volume (value of 1) or
     * a separate notification volume (value of 0). In most cases, users
     * will have this enabled so the notification and ringer volumes will be
     * the same. However, power users can disable this and use the separate
     * notification volume control.
     * <p>
     * Note: This is a one-off setting that will be removed in the future
     * when there is profile support. For this reason, it is kept hidden
     * from the public APIs.
     *
     * @hide
     * @deprecated
     */
    @Deprecated
    public static final String NOTIFICATIONS_USE_RING_VOLUME =
            "notifications_use_ring_volume";

    /**
     * Volume Adjust Sounds Enable, This is the noise made when using volume hard buttons
     * Defaults to 1 - sounds enabled
     * @hide
     */
    public static final String VOLUME_ADJUST_SOUNDS_ENABLED = "volume_adjust_sounds_enabled";

    /**
     * Boolean value whether to link ringtone and notification volumes
     *
     * @hide
     */
    public static final String VOLUME_LINK_NOTIFICATION = "volume_link_notification";

    /**
     * Indicates to VolumePanel that the volume slider should be disabled as user
     * cannot change the stream volume
     * @hide
     */
    public static final int FLAG_FIXED_VOLUME = 1 << 5; // android.media.AudioManager

    // From Android L Preview
    public static final String SYSTEMUI_VOLUME_CONTROLLER = "systemui_volume_controller";
    public static final String ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS";

    // Zen Mode from Settings.Global
    public static final String ZEN_MODE = "zen_mode";
    public static final int ZENMODE_ALL = 0;
    public static final int ZENMODE_PRIORITY = 1;
    public static final int ZENMODE_NONE = 2;

    public static final int ZEN_MODE_OFF = 0;
    public static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;
    public static final int ZEN_MODE_NO_INTERRUPTIONS = 2;

    public int getZenModeListenerInterruptionFilter(int mZenMode) {
        switch (mZenMode) {
            case ZEN_MODE_OFF:
                return NotificationListenerService.INTERRUPTION_FILTER_ALL;
            case ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                return NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
            case ZEN_MODE_NO_INTERRUPTIONS:
                return NotificationListenerService.INTERRUPTION_FILTER_NONE;
            default:
                return NotificationListenerService.INTERRUPTION_FILTER_NONE;
        }
    }

    private static int zenModeFromListenerInterruptionFilter(int listenerInterruptionFilter) {
        switch (listenerInterruptionFilter) {
            case NotificationListenerService.INTERRUPTION_FILTER_ALL:
                return ZEN_MODE_OFF;
            case NotificationListenerService.INTERRUPTION_FILTER_PRIORITY:
                return ZEN_MODE_IMPORTANT_INTERRUPTIONS;
            case NotificationListenerService.INTERRUPTION_FILTER_NONE:
                return ZEN_MODE_NO_INTERRUPTIONS;
            default:
                return ZEN_MODE_OFF;
        }
    }

    public static String zenModeToString(int mode) {
        if (mode == ZEN_MODE_IMPORTANT_INTERRUPTIONS) return "ZEN_MODE_IMPORTANT_INTERRUPTIONS";
        if (mode == ZEN_MODE_NO_INTERRUPTIONS) return "ZEN_MODE_NO_INTERRUPTIONS";
        return "ZEN_MODE_OFF";
    }

    /**
     * Persisted safe headphone volume management state by AudioService
     * @hide
     */
    public static final String AUDIO_SAFE_VOLUME_STATE = "audio_safe_volume_state"; // Settings.Global

    /**
     * Safe headset volume warning option (found on CyanogenMod ROMS)
     * @hide
     */
    public static final String SAFE_HEADSET_VOLUME = "safe_headset_volume";

    // mSafeMediaVolumeState indicates whether the media volume is limited over headphones.
    // It is SAFE_MEDIA_VOLUME_NOT_CONFIGURED at boot time until a network service is connected
    // or the configure time is elapsed. It is then set to SAFE_MEDIA_VOLUME_ACTIVE or
    // SAFE_MEDIA_VOLUME_DISABLED according to country option. If not SAFE_MEDIA_VOLUME_DISABLED, it
    // can be set to SAFE_MEDIA_VOLUME_INACTIVE by calling AudioService.disableSafeMediaVolume()
    // (when user opts out).
    public static final int SAFE_MEDIA_VOLUME_NOT_CONFIGURED = 0; // From AudioService
    public static final int SAFE_MEDIA_VOLUME_DISABLED = 1;
    public static final int SAFE_MEDIA_VOLUME_INACTIVE = 2;
    public static final int SAFE_MEDIA_VOLUME_ACTIVE = 3;

    // HTC-specific action when the device is about to turn off.
    /** @see {@link android.content.Intent#ACTION_SHUTDOWN} */
    public static final String ACTION_QUICKBOOT_OFF = "android.intent.action.QUICKBOOT_POWEROFF";

    /**
     * Persistent store for the system default media button event receiver.
     */
    private static String MEDIA_BUTTON_RECEIVER = null;

    public static String getMediaButtonReceiver() {
        try {
            Field field = Settings.System.class.getDeclaredField("MEDIA_BUTTON_RECEIVER");
            if (null != field) {
                field.setAccessible(true);
                String mbr = (String) field.get(null);
                MEDIA_BUTTON_RECEIVER = mbr;
                return MEDIA_BUTTON_RECEIVER;
            }
        } catch (Throwable t) {
            LogUtils.LOGE("Constants", "getMediaButtonReceiver()", t);
        }
        MEDIA_BUTTON_RECEIVER = "media_button_receiver";
        return MEDIA_BUTTON_RECEIVER;
    }

    private static String ENABLED_NOTIFICATION_LISTENERS = null;

    public static String getEnabledNotificationListeners() {
        try {
            Field field = Settings.Secure.class.getDeclaredField("ENABLED_NOTIFICATION_LISTENERS");
            if (null != field) {
                field.setAccessible(true);
                String mbr = (String) field.get(null);
                ENABLED_NOTIFICATION_LISTENERS = mbr;
                return ENABLED_NOTIFICATION_LISTENERS;
            }
        } catch (Throwable t) {
            LogUtils.LOGE("Constants", "getEnabledNotificationListeners()", t);
        }
        ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
        return ENABLED_NOTIFICATION_LISTENERS;
    }

    public static final String KEY_VIBRATE = "vibrate_when_ringing"; // Settings.System
    public static final String KEY_RING_VOLUME = "ring_volume"; // Settings.System

    /** Intents used to represent media events. */
    public static IntentFilter MEDIA_ACTION_FILTER() {
        final IntentFilter MEDIA_ACTION_FILTER = new IntentFilter();
        MEDIA_ACTION_FILTER.addAction("com.android.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.android.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.android.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.android.music.queuechanged");
        MEDIA_ACTION_FILTER.addAction("com.android.music.musicservicecommand.togglepause");
        MEDIA_ACTION_FILTER.addAction("com.android.music.musicservicecommand.pause");
        MEDIA_ACTION_FILTER.addAction("com.android.music.musicservicecommand.previous");
        MEDIA_ACTION_FILTER.addAction("com.android.music.musicservicecommand.next");
        MEDIA_ACTION_FILTER.addAction("com.android.music.musicservicecommand");
        MEDIA_ACTION_FILTER.addAction("com.android.music.togglepause");
        MEDIA_ACTION_FILTER.addAction("com.android.music.pause");
        MEDIA_ACTION_FILTER.addAction("com.android.music.previous");
        MEDIA_ACTION_FILTER.addAction("com.android.music.next");
        MEDIA_ACTION_FILTER.addAction("com.android.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.htc.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.htc.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.android.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("fm.last.android.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.sec.android.app.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.amazon.mp3.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.amazon.mp3.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.miui.player.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.miui.player.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.miui.player.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.real.IMP.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.real.IMP.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.real.IMP.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.real.RealPlayer.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.real.RealPlayer.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.real.RealPlayer.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.rdio.android.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.rdio.android.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.andrew.apollo.metachanged");
        MEDIA_ACTION_FILTER.addAction("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
        MEDIA_ACTION_FILTER.addAction("com.adam.aslfms.notify.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("gonemad.dashclock.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("gonemad.dashclock.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("gonemad.dashclock.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.mog.android.action.PLAY_OR_PAUSE_ACTION");
        MEDIA_ACTION_FILTER.addAction("com.mog.android.action.ANNOUNCE_PLAYBACK_INFO");
        MEDIA_ACTION_FILTER.addAction("com.musixmatch.android.lyrify.service.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.musixmatch.android.lyrify.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.musixmatch.android.lyrify.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playbackcontrol.ACTION_PLAYBACK_PLAY");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playbackcontrol.ACTION_PLAYBACK_PAUSE");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playbackcontrol.ACTION_TRACK_STARTED");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playbackcontrol.ACTION_PAUSED");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.TRACK_COMPLETED");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.sonyericsson.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.MusicPlayer.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.MusicPlayer.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.samsung.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.samsung.sec.android.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.MusicPlayer.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.samsung.MusicPlayer.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.samsung.MusicPlayer.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.spotify.mobile.android.playbackstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.spotify.mobile.android.metadatachanged");
        MEDIA_ACTION_FILTER.addAction("com.nullsoft.winamp.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.nullsoft.winamp.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.nullsoft.winamp.playstatechanged");
        // MEDIA_ACTION_FILTER.addAction("app.odesanmi.and.wpmusic.SERVICECLOSE");
        // MEDIA_ACTION_FILTER.addAction("app.odesanmi.and.wpmusic.SERVICECHANGE");
        // MEDIA_ACTION_FILTER.addAction("app.odesanmi.and.wpmusic.togglepause");
        MEDIA_ACTION_FILTER.addAction("com.doubleTwist.androidPlayer.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.doubleTwist.androidPlayer.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.doubleTwist.androidPlayer.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerprotrial.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerprotrial.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerprotrial.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerpro.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerpro.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.tbig.playerpro.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.AnotherMusicPlayer.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.AnotherMusicPlayer.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.AnotherMusicPlayer.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.jrtstudio.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.lge.music.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("com.lge.music.metachanged");
        MEDIA_ACTION_FILTER.addAction("com.lge.music.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("com.lge.music.endofplayback");
        MEDIA_ACTION_FILTER.addAction("com.rhapsody.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("org.iii.romulus.meridian.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("org.iii.romulus.meridian.metachanged");
        MEDIA_ACTION_FILTER.addAction("org.iii.romulus.meridian.playbackcomplete");
        MEDIA_ACTION_FILTER.addAction("org.abrantix.rockon.rockonnggl.playstatechanged");
        MEDIA_ACTION_FILTER.addAction("org.abrantix.rockon.rockonnggl.metachanged");
        MEDIA_ACTION_FILTER.addAction("org.abrantix.rockon.rockonnggl.playbackcomplete");
        return MEDIA_ACTION_FILTER;
    }

    public static boolean isRemoteControlPlaying(final int state) {
        switch (state) {
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
            case RemoteControlClient.PLAYSTATE_BUFFERING:
            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
            case RemoteControlClient.PLAYSTATE_PLAYING:
            case RemoteControlClient.PLAYSTATE_REWINDING:
                return true;
        }

        return false;
    }

    public static int adjustIndex(final int index, final int max, final int direction) {
        switch (direction) {
            case AudioManager.ADJUST_LOWER:
                return Math.max(index - 1, 0);
            case AudioManager.ADJUST_RAISE:
                return Math.min(index + 1, max);
            case AudioManager.ADJUST_SAME:
                return index;
            default:
                return 0;
        }
    }

    /*
     * AudioPolicyService methods
     */

    /* modes for setPhoneState, must match AudioSystem.h audio_mode */
    public static final int MODE_INVALID            = -2;
    public static final int MODE_CURRENT            = -1;
    public static final int MODE_NORMAL             = 0;
    public static final int MODE_RINGTONE           = 1;
    public static final int MODE_IN_CALL            = 2;
    public static final int MODE_IN_COMMUNICATION   = 3;
    public static final int NUM_MODES               = 4;

    //
    // audio device definitions: must be kept in sync with values in system/core/audio.h
    //

    // reserved bits
    public static final int DEVICE_BIT_IN = 0x80000000;
    public static final int DEVICE_BIT_DEFAULT = 0x40000000;
    // output devices, be sure to update AudioManager.java also
    public static final int DEVICE_OUT_EARPIECE = 0x1;
    public static final int DEVICE_OUT_SPEAKER = 0x2;
    public static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
    public static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
    public static final int DEVICE_OUT_BLUETOOTH_SCO = 0x10;
    public static final int DEVICE_OUT_BLUETOOTH_SCO_HEADSET = 0x20;
    public static final int DEVICE_OUT_BLUETOOTH_SCO_CARKIT = 0x40;
    public static final int DEVICE_OUT_BLUETOOTH_A2DP = 0x80;
    public static final int DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100;
    public static final int DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER = 0x200;
    public static final int DEVICE_OUT_AUX_DIGITAL = 0x400;
    public static final int DEVICE_OUT_ANLG_DOCK_HEADSET = 0x800;
    public static final int DEVICE_OUT_DGTL_DOCK_HEADSET = 0x1000;
    public static final int DEVICE_OUT_USB_ACCESSORY = 0x2000;
    public static final int DEVICE_OUT_USB_DEVICE = 0x4000;
    public static final int DEVICE_OUT_REMOTE_SUBMIX = 0x8000;

    public static final int DEVICE_OUT_DEFAULT = DEVICE_BIT_DEFAULT;

    public static final int DEVICE_OUT_ALL = (DEVICE_OUT_EARPIECE |
                                              DEVICE_OUT_SPEAKER |
                                              DEVICE_OUT_WIRED_HEADSET |
                                              DEVICE_OUT_WIRED_HEADPHONE |
                                              DEVICE_OUT_BLUETOOTH_SCO |
                                              DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                              DEVICE_OUT_BLUETOOTH_SCO_CARKIT |
                                              DEVICE_OUT_BLUETOOTH_A2DP |
                                              DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                              DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER |
                                              DEVICE_OUT_AUX_DIGITAL |
                                              DEVICE_OUT_ANLG_DOCK_HEADSET |
                                              DEVICE_OUT_DGTL_DOCK_HEADSET |
                                              DEVICE_OUT_USB_ACCESSORY |
                                              DEVICE_OUT_USB_DEVICE |
                                              DEVICE_OUT_REMOTE_SUBMIX |
                                              DEVICE_OUT_DEFAULT);
    public static final int DEVICE_OUT_ALL_A2DP = (DEVICE_OUT_BLUETOOTH_A2DP |
                                                   DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                                   DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER);
    public static final int DEVICE_OUT_ALL_SCO = (DEVICE_OUT_BLUETOOTH_SCO |
                                                  DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                                  DEVICE_OUT_BLUETOOTH_SCO_CARKIT);
    public static final int DEVICE_OUT_ALL_USB = (DEVICE_OUT_USB_ACCESSORY |
                                                  DEVICE_OUT_USB_DEVICE);

    // input devices
    public static final int DEVICE_IN_COMMUNICATION = DEVICE_BIT_IN | 0x1;
    public static final int DEVICE_IN_AMBIENT = DEVICE_BIT_IN | 0x2;
    public static final int DEVICE_IN_BUILTIN_MIC = DEVICE_BIT_IN | 0x4;
    public static final int DEVICE_IN_BLUETOOTH_SCO_HEADSET = DEVICE_BIT_IN | 0x8;
    public static final int DEVICE_IN_WIRED_HEADSET = DEVICE_BIT_IN | 0x10;
    public static final int DEVICE_IN_AUX_DIGITAL = DEVICE_BIT_IN | 0x20;
    public static final int DEVICE_IN_VOICE_CALL = DEVICE_BIT_IN | 0x40;
    public static final int DEVICE_IN_BACK_MIC = DEVICE_BIT_IN | 0x80;
    public static final int DEVICE_IN_REMOTE_SUBMIX = DEVICE_BIT_IN | 0x100;
    public static final int DEVICE_IN_ANLG_DOCK_HEADSET = DEVICE_BIT_IN | 0x200;
    public static final int DEVICE_IN_DGTL_DOCK_HEADSET = DEVICE_BIT_IN | 0x400;
    public static final int DEVICE_IN_USB_ACCESSORY = DEVICE_BIT_IN | 0x800;
    public static final int DEVICE_IN_USB_DEVICE = DEVICE_BIT_IN | 0x1000;
    public static final int DEVICE_IN_DEFAULT = DEVICE_BIT_IN | DEVICE_BIT_DEFAULT;

    public static final int DEVICE_IN_ALL = (DEVICE_IN_COMMUNICATION |
                                             DEVICE_IN_AMBIENT |
                                             DEVICE_IN_BUILTIN_MIC |
                                             DEVICE_IN_BLUETOOTH_SCO_HEADSET |
                                             DEVICE_IN_WIRED_HEADSET |
                                             DEVICE_IN_AUX_DIGITAL |
                                             DEVICE_IN_VOICE_CALL |
                                             DEVICE_IN_BACK_MIC |
                                             DEVICE_IN_REMOTE_SUBMIX |
                                             DEVICE_IN_ANLG_DOCK_HEADSET |
                                             DEVICE_IN_DGTL_DOCK_HEADSET |
                                             DEVICE_IN_USB_ACCESSORY |
                                             DEVICE_IN_USB_DEVICE |
                                             DEVICE_IN_DEFAULT);
    public static final int DEVICE_IN_ALL_SCO = DEVICE_IN_BLUETOOTH_SCO_HEADSET;

    // device states, must match AudioSystem::device_connection_state
    public static final int DEVICE_STATE_UNAVAILABLE = 0;
    public static final int DEVICE_STATE_AVAILABLE = 1;
    private static final int NUM_DEVICE_STATES = 1;

    public static final String DEVICE_OUT_EARPIECE_NAME = "earpiece";
    public static final String DEVICE_OUT_SPEAKER_NAME = "speaker";
    public static final String DEVICE_OUT_WIRED_HEADSET_NAME = "headset";
    public static final String DEVICE_OUT_WIRED_HEADPHONE_NAME = "headphone";
    public static final String DEVICE_OUT_BLUETOOTH_SCO_NAME = "bt_sco";
    public static final String DEVICE_OUT_BLUETOOTH_SCO_HEADSET_NAME = "bt_sco_hs";
    public static final String DEVICE_OUT_BLUETOOTH_SCO_CARKIT_NAME = "bt_sco_carkit";
    public static final String DEVICE_OUT_BLUETOOTH_A2DP_NAME = "bt_a2dp";
    public static final String DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES_NAME = "bt_a2dp_hp";
    public static final String DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER_NAME = "bt_a2dp_spk";
    public static final String DEVICE_OUT_AUX_DIGITAL_NAME = "aux_digital";
    public static final String DEVICE_OUT_ANLG_DOCK_HEADSET_NAME = "analog_dock";
    public static final String DEVICE_OUT_DGTL_DOCK_HEADSET_NAME = "digital_dock";
    public static final String DEVICE_OUT_USB_ACCESSORY_NAME = "usb_accessory";
    public static final String DEVICE_OUT_USB_DEVICE_NAME = "usb_device";
    public static final String DEVICE_OUT_REMOTE_SUBMIX_NAME = "remote_submix";

    public static String getDeviceName(int device)
    {
        switch(device) {
            case DEVICE_OUT_EARPIECE:
                return DEVICE_OUT_EARPIECE_NAME;
            case DEVICE_OUT_SPEAKER:
                return DEVICE_OUT_SPEAKER_NAME;
            case DEVICE_OUT_WIRED_HEADSET:
                return DEVICE_OUT_WIRED_HEADSET_NAME;
            case DEVICE_OUT_WIRED_HEADPHONE:
                return DEVICE_OUT_WIRED_HEADPHONE_NAME;
            case DEVICE_OUT_BLUETOOTH_SCO:
                return DEVICE_OUT_BLUETOOTH_SCO_NAME;
            case DEVICE_OUT_BLUETOOTH_SCO_HEADSET:
                return DEVICE_OUT_BLUETOOTH_SCO_HEADSET_NAME;
            case DEVICE_OUT_BLUETOOTH_SCO_CARKIT:
                return DEVICE_OUT_BLUETOOTH_SCO_CARKIT_NAME;
            case DEVICE_OUT_BLUETOOTH_A2DP:
                return DEVICE_OUT_BLUETOOTH_A2DP_NAME;
            case DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES:
                return DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES_NAME;
            case DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER:
                return DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER_NAME;
            case DEVICE_OUT_AUX_DIGITAL:
                return DEVICE_OUT_AUX_DIGITAL_NAME;
            case DEVICE_OUT_ANLG_DOCK_HEADSET:
                return DEVICE_OUT_ANLG_DOCK_HEADSET_NAME;
            case DEVICE_OUT_DGTL_DOCK_HEADSET:
                return DEVICE_OUT_DGTL_DOCK_HEADSET_NAME;
            case DEVICE_OUT_USB_ACCESSORY:
                return DEVICE_OUT_USB_ACCESSORY_NAME;
            case DEVICE_OUT_USB_DEVICE:
                return DEVICE_OUT_USB_DEVICE_NAME;
            case DEVICE_OUT_REMOTE_SUBMIX:
                return DEVICE_OUT_REMOTE_SUBMIX_NAME;
            case DEVICE_OUT_DEFAULT:
            default:
                return "";
        }
    }

    // From DeskClock
    public static class Alarm {
        // This action triggers the AlarmReceiver as well as the AlarmKlaxon. It
        // is a public action used in the manifest for receiving Alarm broadcasts
        // from the alarm manager.
        public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
        // A public action sent by AlarmKlaxon when the alarm has stopped sounding
        // for any reason (e.g. because it has been dismissed from AlarmAlertFullScreen,
        // or killed due to an incoming phone call, etc).
        public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";
        // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
        // can snooze the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
        public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
        // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
        // can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
        public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
        // A public action sent by AlarmAlertFullScreen when a snoozed alarm was dismissed due
        // to it handling ALARM_DISMISS_ACTION cancelled
        public static final String ALARM_SNOOZE_CANCELLED = "com.android.deskclock.ALARM_SNOOZE_CANCELLED";
        // A broadcast sent every time the next alarm time is set in the system
        public static final String NEXT_ALARM_TIME_SET = "com.android.deskclock.NEXT_ALARM_TIME_SET";
        // This is a private action used by the AlarmKlaxon to update the UI to
        // show the alarm has been killed.
        public static final String ALARM_KILLED = "alarm_killed";
        // Extra in the ALARM_KILLED intent to indicate to the user how long the
        // alarm played before being killed.
        public static final String ALARM_KILLED_TIMEOUT = "alarm_killed_timeout";
        // Extra in the ALARM_KILLED intent to indicate when alarm was replaced
        public static final String ALARM_REPLACED = "alarm_replaced";
        // This string is used to indicate a silent alarm in the db.
        public static final String ALARM_ALERT_SILENT = "silent";
        // This intent is sent from the notification when the user cancels the
        // snooze alert.
        public static final String CANCEL_SNOOZE = "cancel_snooze";
        // This string is used when passing an Alarm object through an intent.
        public static final String ALARM_INTENT_EXTRA = "intent.extra.alarm";

        // This extra is the raw Alarm object data. It is used in the
        // AlarmManagerService to avoid a ClassNotFoundException when filling in
        // the Intent extras.

        public static final String ALARM_RAW_DATA = "intent.extra.alarm_raw";
        private static final String PREF_SNOOZE_IDS = "snooze_ids";
        private static final String PREF_SNOOZE_TIME = "snooze_time";
        private final static String DM12 = "E h:mm aa";
        private final static String DM24 = "E kk:mm";
        private final static String M12 = "h:mm aa";
        // Shared with DigitalClock
        final static String M24 = "kk:mm";
        final static int INVALID_ALARM_ID = -1;
    }
}