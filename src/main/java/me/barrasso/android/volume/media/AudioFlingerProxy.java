package me.barrasso.android.volume.media;

import android.media.AudioManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.SparseArray;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.utils.ReflectionUtils;

/**
 * Proxy to safely access android.media.IAudioFlinger methods.
 */
public class AudioFlingerProxy {

    // Values from IAudioFlinger.cpp
    public static final int CREATE_TRACK = IBinder.FIRST_CALL_TRANSACTION; // 1
    public static final int OPEN_RECORD = CREATE_TRACK + 1; // 2
    public static final int SAMPLE_RATE = OPEN_RECORD + 1; // 3
    public static final int CHANNEL_COUNT = SAMPLE_RATE + 1; // 4
    public static final int FORMAT = CHANNEL_COUNT + 1; // 5
    public static final int FRAME_COUNT = FORMAT + 1; // 6
    public static final int LATENCY = FRAME_COUNT + 1; // 7
    public static final int SET_MASTER_VOLUME = LATENCY + 1; // 8
    public static final int SET_MASTER_MUTE = SET_MASTER_VOLUME + 1; // 9
    public static final int MASTER_VOLUME = SET_MASTER_MUTE + 1; // 10
    public static final int MASTER_MUTE = MASTER_VOLUME + 1; // 11
    public static final int SET_STREAM_VOLUME = MASTER_MUTE + 1; // 12
    public static final int SET_STREAM_MUTE = SET_STREAM_VOLUME + 1; // 13
    public static final int STREAM_VOLUME = SET_STREAM_MUTE + 1; // 14
    public static final int STREAM_MUTE = STREAM_VOLUME + 1; // 15
    public static final int SET_MODE = STREAM_MUTE + 1; // 16

    public static final int DEFAULT = -1;
    public static final int VOICE_CALL = 0;
    public static final int SYSTEM = 1;
    public static final int RING = 2;
    public static final int MUSIC = 3;
    public static final int ALARM = 4;
    public static final int NOTIFICATION = 5;
    public static final int BLUETOOTH_SCO = 6;
    public static final int ENFORCED_AUDIBLE = 7;
    public static final int DTMF = 8;
    public static final int TTS = 9;

    // Values from errno.h
    public static final int NO_ERROR = 0;
    public static final int UNKNOWN_TRANSACTION = -74; // -EBADMSG;
    public static final int BAD_VALUE = -22; // -EINVAL;
    public static final int PERMISSION_DENIED = -1; // -EPERM;

    public static final int CALIBRATION_ERROR = -64;

    private static final String FLINGER_SERVICE = "media.audio_flinger";

    private final String mInterfaceDescriptor;
    private final IBinder mAudioFlinger;

    private final SparseArray<SparseArray<Float>> mStreamStepMap = new SparseArray<SparseArray<Float>>();

    public AudioFlingerProxy() {
        mAudioFlinger = ReflectionUtils.getServiceManager(FLINGER_SERVICE);

        String mID = "";
        {
            try {
                mID = mAudioFlinger.getInterfaceDescriptor();
            } catch (RemoteException e) {
                mID = "";
                LogUtils.LOGE("AudioFlingerProxy", "Error obtained interface descriptor.", e);
            }
        }
        mInterfaceDescriptor = mID;
    }

    public boolean isCalibrated(int stream) {
        SparseArray<Float> map = mStreamStepMap.get(stream);
        return (map != null && map.size() >= 2);
    }

    public void mapStreamIndex(int stream, int index) {
        SparseArray<Float> map = mStreamStepMap.get(stream);
        if (null == map) map = new SparseArray<Float>();

        try {
            float value = getStreamVolume(stream);
            map.put(index, value);
        } catch (RemoteException re) {
            LogUtils.LOGE("AudioFlingerProxy", "Error getting stream volume_3.", re);
        }

        mStreamStepMap.put(stream, map);
        LogUtils.LOGI("AudioFlingerProxy", LogUtils.logSparseArray(mStreamStepMap));
    }

    /**
     * Set the volume_3 of a calibrated stream.
     * @see {@link #setStreamVolume(int, float)}
     * @throws RemoteException
     */
    public int adjustStreamVolume(int stream, int direction, int index, int max) throws RemoteException {
        LogUtils.LOGI("AudioFlingerProxy", "adjustStreamVolume(" + stream + ", " + direction + ")");
        if (null == mAudioFlinger || TextUtils.isEmpty(mInterfaceDescriptor) || !isCalibrated(stream)) {
            return BAD_VALUE;
        }

        float value = getStreamVolume(stream);
        float increment = getStreamIncrement(stream);

        float newValue = value;
        switch (direction) {
            case AudioManager.ADJUST_LOWER:
                newValue -= increment;
            case AudioManager.ADJUST_RAISE:
                newValue += increment;
        }
        newValue = Math.max(0, Math.min(newValue, max * increment));

        LogUtils.LOGI("AudioFlingerProxy", "adjustStreamVolume() increment = " + increment + ", newVolume = " + newValue);
        return setStreamVolume(stream, newValue);
    }

    // change this value to change volume_3 scaling
    protected static float dBPerStep = 0.5f;
    // shouldn't need to touch these
    protected static float dBConvert = -dBPerStep * 2.302585093f / 20.0f;
    protected static float dBConvertInverse = 1.0f / dBConvert;

    protected static float linearToLog(int volume) {
        if (volume == 0) return 0.0f;
        return (float) Math.exp(100 - volume * dBConvert);
    }

    protected static int logToLinear(float volume) {
        if (volume == 0.0f) return 0;
        return (int) (100 - (dBConvertInverse * Math.log(volume) + 0.5));
    }

    protected static float computeVolume(int index, int max) {
        int mIndexMin = 0;
        int volInt = (100 * (index - mIndexMin)) / (max - mIndexMin);
        return linearToLog(volInt);
    }

    protected float getStreamIncrement(int stream) throws RemoteException {
        // Figure out what the increment is.
        SparseArray<Float> map = mStreamStepMap.get(stream);
        int indexOne = map.keyAt(0);
        int indexTwo = map.keyAt(1);
        float valueOne = map.get(indexOne);
        float valueTwo = map.get(indexTwo);

        return  Math.abs(valueTwo - valueOne) /
                Math.abs(indexTwo - indexOne);
    }

    public int getStreamIndex(int stream) throws RemoteException {
        if (null == mAudioFlinger || TextUtils.isEmpty(mInterfaceDescriptor) || !isCalibrated(stream)) {
            return BAD_VALUE;
        }

        float value = getStreamVolume(stream);
        float increment = getStreamIncrement(stream);

        return Math.round(value / increment);
    }

    public int setStreamVolume(int stream, float value) throws RemoteException {
        LogUtils.LOGI("AudioFlingerProxy", "setStreamVolume(" + stream + ", " + value + ")");
        if (null == mAudioFlinger || TextUtils.isEmpty(mInterfaceDescriptor)) {
            return BAD_VALUE;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(mInterfaceDescriptor);
        data.writeInt(stream);
        data.writeFloat(value);
        mAudioFlinger.transact(SET_STREAM_VOLUME, data, reply, 0);
        return reply.readInt();
    }

    public float getStreamVolume(int stream) throws RemoteException {
        LogUtils.LOGI("AudioFlingerProxy", "getStreamVolume(" + stream + ")");
        if (null == mAudioFlinger || TextUtils.isEmpty(mInterfaceDescriptor)) {
            return BAD_VALUE;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken(mInterfaceDescriptor);
        data.writeInt(stream);
        mAudioFlinger.transact(STREAM_VOLUME, data, reply, 0);
        float ret = Float.intBitsToFloat(reply.readInt());
        LogUtils.LOGI("AudioFlingerProxy", "Stream = " + stream + ", volume_3 = " + ret);
        return ret;
    }
}