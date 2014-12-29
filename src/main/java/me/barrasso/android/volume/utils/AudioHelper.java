package me.barrasso.android.volume.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.AudioRoutesInfo;
import android.media.IAudioRoutesObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IInterface;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import me.barrasso.android.volume.media.VolumeMediaReceiver;
import me.barrasso.android.volume.popup.PopupWindowManager;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGE;
import static me.barrasso.android.volume.LogUtils.LOGI;

public final class AudioHelper {

    public static final String TAG = AudioHelper.class.getSimpleName();

    private static Method mDispatchMediaKeyEvent;
    private static IInterface sService;

    public static IInterface getService() {
        if (null == sService)
            sService = ReflectionUtils.getIInterface(Context.AUDIO_SERVICE, "android.media.IAudioService$Stub");
        return sService;
    }

    public static void freeResources() {
        mMethodMap.clear();
    }

    public static class DefaultHashMap<K,V> extends HashMap<K,V> {
        protected V defaultValue;
        public DefaultHashMap(V defaultValue) {
            this.defaultValue = defaultValue;
        }
        @Override
        public V get(Object k) {
            return containsKey(k) ? super.get(k) : defaultValue;
        }
    }

    // Cache Reflection calls to optimize frequent calls.
    private static WeakHashMap<String, Method> mMethodMap = new WeakHashMap<String, Method>();
    private static Map<String, Boolean> mMethodSuccessMap = new DefaultHashMap<String, Boolean>(true);

    /** @return The name used to uniquely store and look up a cached method. */
    private static String methodMapName(Class<?> clazz, String methodName) {
        return clazz.getName() + '#' + methodName;
    }

    private static boolean shouldTryMethod(String methodMapName) {
        return mMethodSuccessMap.get(methodMapName);
    }

    /**
     * Information available from AudioService about the current routes.
     * @hide
     */
    public static final int MAIN_SPEAKER = 0;
    public static final int MAIN_HEADSET = 1<<0;
    public static final int MAIN_HEADPHONES = 1<<1;
    public static final int MAIN_DOCK_SPEAKERS = 1<<2;
    public static final int MAIN_HDMI = 1<<3;

    /** @return an android.media.AudioRoutesInfo Object upon successful registration. */
    public static AudioRoutesInfo startWatchingRoutes(IAudioRoutesObserver observer) {
        IInterface service = getService();
        if (null == service) return null;
        try {
            Method watch = service.getClass().getDeclaredMethod("startWatchingRoutes",
                    IAudioRoutesObserver.class);
            if (null != watch) {
                watch.setAccessible(true);
                // Info the listener immediately of the current route.
                AudioRoutesInfo info = (AudioRoutesInfo) watch.invoke(service, observer);
                observer.dispatchAudioRoutesChanged(info);
                return info;
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking android.media.IAudioService#startWatchingRoutes", t);
        }
        return null;
    }

    protected static AudioRoutesObserver mAudioRoutesObserver;
    protected static synchronized AudioRoutesObserver getAudioRoutesObserver() {
        if (null == mAudioRoutesObserver)
            mAudioRoutesObserver = new AudioRoutesObserver();
        return mAudioRoutesObserver;
    }

    protected static class AudioRoutesObserver extends IAudioRoutesObserver.Stub {
        @Override public void dispatchAudioRoutesChanged(AudioRoutesInfo info) {
            if (null != mHelper) mHelper.dispatchAudioRoutesChanged(info);
        }
    }

    /** @return The value for a static integer flag, or null. */
    public static Integer _getInternalResourceIdentifier(final String name, final String type) {
        try {
            Class<?> clazz = Class.forName("com.android.internal.R$" + type);
            if (null != clazz) {
                Field aFlag = clazz.getField(name);
                if (null != aFlag) {
                    aFlag.setAccessible(true);
                    Integer ret = (Integer) aFlag.get(null);
                    LOGI(TAG, clazz.getName() + '#' + name + '=' + String.valueOf(ret));
                    return ret;
                }
            }
        } catch (Throwable e) {
            LOGE(TAG, "Error retrieving " + "com.android.internal.R$" + type + "#" + name + " flag.", e);
        }
        return null;
    }

    public static boolean _isVoiceCapable() {
        Integer id = _getInternalResourceIdentifier("config_voice_capable", "bool");
        return (null == id) || Resources.getSystem().getBoolean(id);
    }

    public static boolean _useFixedVolume() {
        Integer id = _getInternalResourceIdentifier("config_useFixedVolume", "bool");
        return (null != id) && Resources.getSystem().getBoolean(id);
    }

    public static boolean _useMasterVolume() {
        Integer id = _getInternalResourceIdentifier("config_useMasterVolume", "bool");
        return (null != id) && Resources.getSystem().getBoolean(id);
    }

    public static boolean _safeVolumeEnabled() {
        Integer id = _getInternalResourceIdentifier("config_safe_media_volume_enabled", "bool");
        return (null != id) && Resources.getSystem().getBoolean(id);
    }

    public static int _safeVolumeIndex() {
        // The default safe volume index read here will be replaced by the actual value when
        // the mcc is read by onConfigureSafeVolume()
        Integer id = _getInternalResourceIdentifier("config_safe_media_volume_index", "integer");
        return (null == id) ? -1 : Resources.getSystem().getInteger(id);
    }

    public static int _cameraSoundForced() {
        Integer id = _getInternalResourceIdentifier("config_camera_sound_forced", "bool");
        return (null == id) ? -1 : Resources.getSystem().getInteger(id);
    }

    public static boolean _dispatchMediaKeyEvent(KeyEvent event) {
        try {
            IInterface service = getService();
            if (null == mDispatchMediaKeyEvent)
                mDispatchMediaKeyEvent = service.getClass().getDeclaredMethod(
                        "dispatchMediaKeyEvent", KeyEvent.class);
            if (null != mDispatchMediaKeyEvent) {
                mDispatchMediaKeyEvent.setAccessible(true);
                mDispatchMediaKeyEvent.invoke(service, event);
                return true;
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error dispatchMediaKeyEvent()", t);
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void dispatchMediaKeyEvent(AudioManager manager, KeyEvent event) {
        manager.dispatchMediaKeyEvent(event);
    }

    public static boolean _dispatchMediaKeyEvent(AudioManager mManager, int keyCode) {
        long eventtime = SystemClock.uptimeMillis();
        KeyEvent keyDown = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent keyUp = KeyEvent.changeAction(keyDown, KeyEvent.ACTION_UP);

        // KitKat this API is public, so use it!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            dispatchMediaKeyEvent(mManager, keyUp);
            dispatchMediaKeyEvent(mManager, keyDown);
            return true;
        }

        // Otherwise try dispatching using Reflection.
        return (_dispatchMediaKeyEvent(keyDown) && _dispatchMediaKeyEvent(keyUp));
    }

    private static Boolean IS_HTC = null;

    /**
     * @return True if the device was made by HTC, has HTC Sense, etc.
     */
    public static boolean isHTC(Context context) {
        if (null != IS_HTC) return IS_HTC;
        IS_HTC = _isHTC(context);
        return IS_HTC;
    }

    private static boolean _isHTC(Context context) {
        // CHECK: Build prop to see if HTC is there.
        if (Build.MANUFACTURER.contains("HTC")) return true;
        // CHECK: available features, like HTC sense.
        FeatureInfo[] features = context.getPackageManager().getSystemAvailableFeatures();
        for (FeatureInfo feature : features) {
            if (!TextUtils.isEmpty(feature.name) &&
                feature.name.startsWith("com.htc")) {
                return true;
            }
        }
        // CHECK: the HTC Sense launcher package.
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = pm.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : list) {
            if (info.activityInfo != null) {
                if ("com.htc.launcher.Launcher".equals(info.activityInfo.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * android.media.IAudioService#dispatchMediaKeyEvent(KeyEvent), except if this
     * method fails for any reason, fall back on broadcasting an event.
     */
    public boolean dispatchMediaKeyEvent(Context mContext, int keyCode) {
        // We'll try the public API for API 19+, then the reflected API, then
        // finally we'll resort to broadcasting the action ourselves!
        if (isHTC(mContext) || !_dispatchMediaKeyEvent(mManager, keyCode)) {
            long eventtime = SystemClock.uptimeMillis();
            KeyEvent keyDown = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent keyUp = KeyEvent.changeAction(keyDown, KeyEvent.ACTION_UP);
            Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyDown);
            mContext.sendOrderedBroadcast(keyIntent, null);
            keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyUp);
            mContext.sendOrderedBroadcast(keyIntent, null);
            return false;
        }
        return true;
    }

    /** Use to send {@link android.content.Intent#ACTION_MEDIA_BUTTON} to this application. */
    public static void dispatchMediaKeyEventSelf(Context mContext, int keyCode) {
        long eventtime = SystemClock.uptimeMillis();
        KeyEvent keyDown = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent keyUp = KeyEvent.changeAction(keyDown, KeyEvent.ACTION_UP);
        Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        keyIntent.setPackage(mContext.getPackageName());
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyDown);
        mContext.sendOrderedBroadcast(keyIntent, null);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, keyUp);
        mContext.sendOrderedBroadcast(keyIntent, null);
    }

    /** @return The value for a static integer flag, or null. */
    public static Integer _getStaticFlag(final String clazzName, final String flagName) {
        try {
            Class<?> clazz = Class.forName(clazzName);
            if (null != clazz) {
                Field aFlag = clazz.getField(flagName);
                if (null != aFlag) {
                    aFlag.setAccessible(true);
                    Integer ret = (Integer) aFlag.get(null);
                    LOGD(TAG, clazzName + '#' + flagName + '=' + String.valueOf(ret));
                    return ret;
                }
            }
        } catch (Throwable e) { LOGE(TAG, "Error retrieving " + clazzName + " flag.", e); }

        return null;
    }

    public static int getAudioServiceFlag(String flagName, int defVal) {
        Integer ret = _getStaticFlag("android.media.AudioService", flagName);
        return ((ret == null) ? defVal : ret);
    }

    public static int getAudioSystemFlag(String flagName, int defVal) {
        Integer ret = _getStaticFlag("android.media.AudioSystem", flagName);
        return ((ret == null) ? defVal : ret);
    }

    public static int getAudioManagerFlag(String flagName, int defVal) {
        Integer ret = _getStaticFlag(AudioManager.class.getName(), flagName);
        return ((ret == null) ? defVal : ret);
    }

    public static Class<?>[] fromObjects(Object[] objs) {
        if (null == objs) return null;
        Class<?>[] clazz = new Class<?>[objs.length];
        for (int i = 0; i < clazz.length; ++i)
            clazz[i] = simplifyClass(objs[i].getClass());
        return clazz;
    }

    public static Class<?> simplifyClass(Class<?> clazz) {
        if (Integer.class.equals(clazz))
            return Integer.TYPE;
        else if (Boolean.class.equals(clazz))
            return Boolean.TYPE;
        else if (Long.class.equals(clazz))
            return Long.TYPE;
        else if (Double.class.equals(clazz))
            return Double.TYPE;
        else if (Byte.class.equals(clazz))
            return Byte.TYPE;
        else if (Short.class.equals(clazz))
            return Short.TYPE;
        else if (Float.class.equals(clazz))
            return Float.TYPE;
        return clazz;
    }

    public static Integer _intMethod(AudioManager manager, String methodName, Object[] vals) {
        return (Integer) _audioMethod(manager, methodName, vals);
    }

    public static Boolean _boolMethod(AudioManager manager, String methodName, Object[] vals) {
        return (Boolean) _audioMethod(manager, methodName, vals);
    }

    public static Object _audioMethod(AudioManager manager, String methodName, Object[] vals) {
        String methodMapName = methodMapName(AudioManager.class, methodName);
        try {
            if (!shouldTryMethod(methodMapName)) return null;
            boolean hadMethod = mMethodMap.containsKey(methodMapName);
            Method bMethod = (hadMethod) ? mMethodMap.get(methodMapName) :
                    AudioManager.class.getDeclaredMethod(methodName, fromObjects(vals));
            if (null != bMethod) {
                bMethod.setAccessible(true);
                if (!hadMethod) mMethodMap.put(methodMapName, bMethod);
                Object ret = bMethod.invoke(manager, vals);
                if (null != ret) {
                    mMethodSuccessMap.put(methodMapName, true);
                    return ret;
                }
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking AudioManager#" + methodName, t);
            mMethodSuccessMap.put(methodMapName, false);
            return null;
        }
        return new Object();
    }

    public static Boolean _boolServiceMethod(AudioManager manager, String methodName, Object[] vals) {
        return (Boolean) _audioServiceMethod(manager, methodName, vals);
    }

    public static Integer _intServiceMethod(AudioManager manager, String methodName, Object[] vals) {
        return (Integer) _audioServiceMethod(manager, methodName, vals);
    }

    /**
     * Analog for AudioSystem.isStreamActive(int, int), used to determine if a stream is active.
     */
    public static Boolean isStreamActive(int stream, int inPastMs) {
        String clazz = "android.media.AudioSystem";
        String methodMapName = clazz + "#isStreamActive";
        try {
            if (!shouldTryMethod(methodMapName)) return null;
            boolean hadMethod = mMethodMap.containsKey(methodMapName);
            Method mMethod = null;
            if (hadMethod) mMethod = mMethodMap.get(methodMapName);
            else {
                Class<?> aSystem = Class.forName(clazz);
                mMethod = aSystem.getDeclaredMethod("isStreamActive", Integer.TYPE, Integer.TYPE);
            }
            if (null != mMethod) {
                mMethod.setAccessible(true);
                if (!hadMethod) mMethodMap.put(methodMapName, mMethod);
                Object ret = mMethod.invoke(null, stream, inPastMs);
                mMethodSuccessMap.put(methodMapName, null != ret);
                return (Boolean) ret;
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking AudioSystem#isStreamActive(int, int)", t);
            mMethodSuccessMap.put(methodMapName, false);
        }
        return null;
    }

    /**
     * @return Null if an error occurred, else it returns an empty {@link java.lang.Object} if the
     * method succeed but has no return (void) value, or the value returned.
     */
    public static Object _audioServiceMethod(AudioManager manager, String methodName, Object[] vals) {
        try {
            IInterface service = _getAudioService(manager);
            if (null != service) {
                String methodMapName = methodMapName(service.getClass(), methodName);
                if (!shouldTryMethod(methodMapName)) return null;
                boolean hadMethod = mMethodMap.containsKey(methodMapName);
                Method bMethod = (hadMethod) ? mMethodMap.get(methodMapName) :
                        service.getClass().getDeclaredMethod(methodName, fromObjects(vals));
                if (null != bMethod) {
                    bMethod.setAccessible(true);
                    if (!hadMethod) mMethodMap.put(methodMapName, bMethod);
                    Object ret = bMethod.invoke(service, vals);
                    mMethodSuccessMap.put(methodMapName, null != ret);
                    if (null != ret) return ret;
                }
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking AudioService#" + methodName, t);
            return null;
        }
        return new Object();
    }

    public static Object _iaudioServiceMethod(String methodName, Object[] vals) {
        try {
            IInterface service = getService();
            if (null != service) {
                String methodMapName = methodMapName(service.getClass(), methodName);
                if (!shouldTryMethod(methodMapName)) return null;
                boolean hadMethod = mMethodMap.containsKey(methodMapName);
                Method bMethod = (hadMethod) ? mMethodMap.get(methodMapName) :
                        service.getClass().getDeclaredMethod(methodName, fromObjects(vals));
                if (null != bMethod) {
                    bMethod.setAccessible(true);
                    if (!hadMethod) mMethodMap.put(methodMapName, bMethod);
                    Object ret = bMethod.invoke(service, vals);
                    mMethodSuccessMap.put(methodMapName, null != ret);
                    if (null != ret) return ret;
                }
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking AudioService#" + methodName, t);
            return null;
        }
        return new Object();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String _stringRemoteMethod(Object controller, String methodName, Object[] vals) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null; // Not supported
        try {
            Object ret = _remoteControllerMethod(controller, methodName, vals);
            if (null == ret) return null;
            if (ret instanceof String)
                return (String) ret;
        } catch (ClassCastException cce) {
            LOGE(TAG, "Error casting to String RemoteController#" + methodName, cce);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static Object _remoteControllerMethod(Object controller, String methodName, Object[] vals) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null; // Not supported
        String methodMapName = "android.media.RemoteController" + '#'  +methodName;
        try {
            if (!shouldTryMethod(methodMapName)) return null;
            boolean hadMethod = mMethodMap.containsKey(methodMapName);
            Class<?> rContClass = Class.forName("android.media.RemoteController");
            Method bMethod = (hadMethod) ? mMethodMap.get(methodMapName) :
                    rContClass.getDeclaredMethod(methodName, fromObjects(vals));
            if (null != bMethod && controller.getClass().equals(rContClass)) {
                bMethod.setAccessible(true);
                if (!hadMethod) mMethodMap.put(methodMapName, bMethod);
                Object ret = bMethod.invoke(controller, vals);
                if (null != ret) {
                    mMethodSuccessMap.put(methodMapName, true);
                    return ret;
                }
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error invoking RemoteController#" + methodName, t);
            mMethodSuccessMap.put(methodMapName, false);
            return null;
        }
        return new Object();
    }

    public static IInterface _getAudioService(AudioManager manager) {
        String methodMapName = methodMapName(AudioManager.class, "getService");
        try {
            boolean hadMethod = mMethodMap.containsKey(methodMapName);
            if (!shouldTryMethod(methodMapName)) return null;
            Method bMethod = (hadMethod) ? mMethodMap.get(methodMapName) :
                    AudioManager.class.getDeclaredMethod("getService");
            if (null != bMethod) {
                bMethod.setAccessible(true);
                if (!hadMethod) mMethodMap.put(methodMapName, bMethod);
                mMethodSuccessMap.put(methodMapName, true);
                return (IInterface) bMethod.invoke(null);
            }
        } catch (Throwable t) {
            LOGE(TAG, "Error retrieving AudioService", t);
            mMethodSuccessMap.put(methodMapName, false);
        }
        return null;
    }

    // mSafeMediaVolumeIndex is the cached value of config_safe_media_volume_index property
    private int mSafeMediaVolumeIndex;
    private boolean mSafeMediaVolumeEnabled;

    // mSafeMediaVolumeDevices lists the devices for which safe media volume is enforced,
    private static final int mSafeMediaVolumeDevices = Constants.DEVICE_OUT_WIRED_HEADSET |
                                                       Constants.DEVICE_OUT_WIRED_HEADPHONE;

    /** @return True if safe volume won't have an effect on this volume change event. */
    public boolean checkSafeMediaVolume(int streamType, int index, int device) {
        if ((streamType == AudioManager.STREAM_MUSIC) &&
                ((device & mSafeMediaVolumeDevices) != 0) &&
                ((index - mSafeMediaVolumeIndex) == 1)) {
            return false;
        }
        return true;
    }

    /** @return True if safe media volume is enabled. */
    public boolean isSafeMediaVolumeEnabled(Context context) {
        // NOTE: We check system-wide setting, CyanogenMod override, then finally the state.
        if (!mSafeMediaVolumeEnabled) return false;
        Boolean cmOverride = isCyanogenModSafeVolumeEnabled(context);
        if (null != cmOverride) return cmOverride;
        final int state = Settings.Global.getInt(context.getContentResolver(),
                Constants.AUDIO_SAFE_VOLUME_STATE,
                Constants.SAFE_MEDIA_VOLUME_NOT_CONFIGURED);
        LOGI(TAG, Constants.AUDIO_SAFE_VOLUME_STATE + '=' + state);
        return (state == Constants.SAFE_MEDIA_VOLUME_ACTIVE);
    }

    private Boolean isCyanogenModSafeVolumeEnabled(Context context) {
        // This is a CyanogenMod-specific modification found as far as KitKat (perhaps further).
        // Android "L" Preview introduced IAudioService#disableSafeMediaVolume() as a public method,
        // but it is guarded by the STATUS_BAR_SERVICE permission.
        try {
            final int enabled = Settings.System.getInt(context.getContentResolver(),
                    Constants.SAFE_HEADSET_VOLUME);
            return (enabled == 1);
        } catch (Settings.SettingNotFoundException sne) {
            LOGE(TAG, "Could not find Setting.System#" + Constants.SAFE_HEADSET_VOLUME);
            return null;
        }
    }

    private boolean mUseMasterVolume;
    private boolean mUseFixedVolume;
    private boolean mHasVibrator;
    private boolean mVoiceCapable;
    protected int mRingerModeAffectedStreams;

    private Handler mHandler;
    private AudioManager mManager;
    private Vibrator vibrator;
    protected AudioRoutesInfo mAudioRoutesInfo;

    private static AudioHelper mHelper;
    public static synchronized AudioHelper getHelper(Context context, AudioManager manager) {
        if (null == mHelper)
            mHelper = new AudioHelper(context, manager);
        // Register to watch for changes in AudioRoutesInfo.
        startWatchingRoutes(getAudioRoutesObserver());
        return mHelper;
    }

    public AudioHelper(Context context, AudioManager manager) {
        if (null == manager)
            manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mManager = manager;
        mVoiceCapable = _isVoiceCapable();
        mUseFixedVolume = _useFixedVolume();
        mUseMasterVolume = _useMasterVolume();
        mSafeMediaVolumeIndex = _safeVolumeIndex();
        mSafeMediaVolumeEnabled = _safeVolumeEnabled();
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = (null != vibrator && vibrator.hasVibrator());
        updateRingerModeAffectedStreams(context);
    }

    public void vibrate(long milliseconds) {
        if (mHasVibrator) vibrator.vibrate(milliseconds);
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
        dispatchAudioRoutesChanged(mAudioRoutesInfo);
    }

    protected void dispatchAudioRoutesChanged(AudioRoutesInfo info) {
        mAudioRoutesInfo = info;
        if (null != mHandler) {
            Message.obtain(mHandler, VolumeMediaReceiver.MSG_AUDIO_ROUTES_CHANGED, info).sendToTarget();
        }
    }

    public boolean isVoiceCapable() { return mVoiceCapable; }
    public boolean hasVibrator() { return mHasVibrator; }
    public boolean useFixedVolume() { return mUseFixedVolume; }
    public boolean useMasterVolume() { return mUseMasterVolume; }
    public int getSafeMediaVolumeIndex() { return mSafeMediaVolumeIndex; }

    public boolean isLocalOrRemoteMusicActive() {
        Boolean isActive;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            isActive = (boolMethod("isLocalOrRemoteMusicActive", null)); // KitKat+
        } else {
            isActive = (boolMethod("isMusicActiveRemotely", null)); // JellyBean MR2, Lollipop
        }
        if (null == isActive) isActive = mManager.isMusicActive();
        return isActive;
    }

    protected Method wmCsd;
    public void closeSystemDialogs(Context context, String reason) {
        LOGI(TAG, "closeSystemDialogs(" + reason + ')');
        IInterface wm = PopupWindowManager.getIWindowManager();
        try {
            if (null == wmCsd)
                wmCsd = wm.getClass().getDeclaredMethod("closeSystemDialogs", String.class);
            if (null != wmCsd) {
                wmCsd.setAccessible(true);
                wmCsd.invoke(wm, reason);
                return;
            }
        } catch (Throwable t) {
            LOGE(TAG, "Could not invoke IWindowManager#closeSystemDialogs");
        }

        // Backup is to send the intent ourselves.
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intent.putExtra("reason", reason);
        context.sendBroadcast(intent);
    }

    public void setWiredDeviceConnectionState(int device, int state, String name) {
        _iaudioServiceMethod("setWiredDeviceConnectionState", new Object[] { device, state, name });
    }

    /**
     * forces the stream controlled by hard volume keys
     * specifying streamType == -1 releases control to the
     * logic.
     *
     * @hide
     */
    public void forceVolumeControlStream(final int streamType) {
        _audioMethod(mManager, "forceVolumeControlStream", new Integer[] { streamType });
    }

    public Boolean boolMethod(String methodName, Object[] vals) {
        return _boolMethod(mManager, methodName, vals);
    }

    public Integer intMethod(String methodName, Object[] vals) {
        return _intMethod(mManager, methodName, vals);
    }

    public Object audioMethod(String methodName, Object[] vals) {
        return _audioMethod(mManager, methodName, vals);
    }

    public Object serviceMethod(String methodName, Object[] vals) {
        return _audioServiceMethod(mManager, methodName, vals);
    }

    public Integer intServiceMethod(String methodName, Object[] vals) {
        return _intServiceMethod(mManager, methodName, vals);
    }

    public Integer intIServiceMethod(String methodName, Object[] vals) {
        return (Integer) _iaudioServiceMethod(methodName, vals);
    }

    public Boolean boolServiceMethod(String methodName, Object[] vals) {
        return _boolServiceMethod(mManager, methodName, vals);
    }

    // NOTE: this method was added on Android "L" Preview, so it's best to use it.
    private Boolean _isStreamAffectedByRingerMode(int streamType) {
        Object res = _iaudioServiceMethod("isStreamAffectedByRingerMode", new Integer[] { streamType });
        if (null != res && res instanceof Boolean) return (Boolean) res;
        return null;
    }

    public boolean isStreamAffectedByRingerMode(int streamType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Boolean res = _isStreamAffectedByRingerMode(streamType);
            if (null != res) return res;
        }
        return (mRingerModeAffectedStreams & (1 << streamType)) != 0;
    }

    boolean updateRingerModeAffectedStreams(Context context) {
        int ringerModeAffectedStreams;

        // make sure settings for ringer mode are consistent with device type: non voice capable
        // devices (tablets) include media stream in silent mode whereas phones don't.
        ringerModeAffectedStreams = Settings.System.getInt(context.getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                ((1 << AudioManager.STREAM_RING)|(1 << AudioManager.STREAM_NOTIFICATION)|
                 (1 << AudioManager.STREAM_SYSTEM)));

        // ringtone, notification and system streams are always affected by ringer mode
        ringerModeAffectedStreams |= (1 << AudioManager.STREAM_RING)|
                (1 << AudioManager.STREAM_NOTIFICATION)|
                (1 << AudioManager.STREAM_SYSTEM);

        if (mVoiceCapable) {
            ringerModeAffectedStreams &= ~(1 << AudioManager.STREAM_MUSIC);
        } else {
            ringerModeAffectedStreams |= (1 << AudioManager.STREAM_MUSIC);
        }

        if (ringerModeAffectedStreams != mRingerModeAffectedStreams) {
            mRingerModeAffectedStreams = ringerModeAffectedStreams;
            return true;
        }
        return false;
    }
}