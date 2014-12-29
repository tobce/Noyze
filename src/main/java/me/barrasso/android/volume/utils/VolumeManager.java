package me.barrasso.android.volume.utils;

import android.media.AudioManager;
import android.util.Pair;
import android.util.SparseArray;

import static me.barrasso.android.volume.LogUtils.LOGD;
import static me.barrasso.android.volume.LogUtils.LOGI;

import me.barrasso.android.volume.LogUtils;
import me.barrasso.android.volume.popup.VolumePanel;

/**
 * Simple container to keep track of stream volumes. Also contains helpful
 * methods for synchronizing volumes across all streams.
 */
public final class VolumeManager {

    public static final String TAG = LogUtils.makeLogTag(VolumeManager.class);

    public static SparseArray<String> STREAM_NAMES() {
        final SparseArray<String> STREAM_NAMES = new SparseArray<String>();
        STREAM_NAMES.put(AudioManager.STREAM_VOICE_CALL, "STREAM_VOICE_CALL");
        STREAM_NAMES.put(AudioManager.STREAM_SYSTEM, "STREAM_SYSTEM");
        STREAM_NAMES.put(AudioManager.STREAM_NOTIFICATION, "STREAM_NOTIFICATION");
        STREAM_NAMES.put(AudioManager.STREAM_RING, "STREAM_RING");
        STREAM_NAMES.put(AudioManager.STREAM_DTMF, "STREAM_DTMF");
        STREAM_NAMES.put(AudioManager.STREAM_MUSIC, "STREAM_MUSIC");
        STREAM_NAMES.put(AudioManager.STREAM_ALARM, "STREAM_ALARM");
        STREAM_NAMES.put(VolumePanel.STREAM_MASTER, "STREAM_MASTER");
        STREAM_NAMES.put(VolumePanel.STREAM_BLUETOOTH_SCO, "STREAM_BLUETOOTH_SCO");
        STREAM_NAMES.put(VolumePanel.STREAM_REMOTE_MUSIC, "STREAM_REMOTE_MUSIC");
        STREAM_NAMES.put(AudioManager.USE_DEFAULT_STREAM_TYPE, "USE_DEFAULT_STREAM_TYPE");
        return STREAM_NAMES;
    }

    public static String getStreamName(final int streamType) {
        return STREAM_NAMES().get(streamType);
    }

    final static int[] STREAMS = new int[] {
            AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING, AudioManager.STREAM_DTMF, AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM
    };

    // Map of stream types to a pair (first=volume, second=max)
    private final SparseArray<Pair<Integer, Integer>> STREAM_VOLUMES = new SparseArray<Pair<Integer, Integer>>();
    private final SparseArray<Integer> STREAM_HIGHEST_VOLUMES = new SparseArray<Integer>();
    private final AudioManager audioManager;
    private final int mSmallestMax;
    private final int mLargestMax;
    private Pair<Integer, Integer> mManagedVolume;

    public VolumeManager(AudioManager audioManager) {
        this.audioManager = audioManager;
        int[] extremes = syncStreams();
        LOGI(TAG, "Volume streams [" + extremes[0] + ", " + extremes[1] + "]");
        mSmallestMax = extremes[0];
        mLargestMax = extremes[1];
    }

    /**
     * Synchronize all streams based on AudioManager.
     * @return The smallest maximum volume.
     */
    protected int[] syncStreams() {
        int[] extremes = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE }; // smallest, greatest
        for (final int stream : STREAMS) {
            Pair<Integer, Integer> volume = new Pair<Integer, Integer>(
                    audioManager.getStreamVolume(stream), audioManager.getStreamMaxVolume(stream));
            if (volume.second < extremes[0]) extremes[0] = volume.second;
            if (volume.second > extremes[1]) extremes[1] = volume.second;
            // Put ot set based on whether we already have an object.
            if (null != STREAM_VOLUMES.get(stream)) {
                STREAM_VOLUMES.setValueAt(stream, volume);
            } else {
                STREAM_VOLUMES.put(stream, volume);
            }
            Integer highest = STREAM_HIGHEST_VOLUMES.get(stream);
            if (null != highest && highest < volume.first) {
                STREAM_HIGHEST_VOLUMES.setValueAt(stream, volume.first);
            } else {
                STREAM_HIGHEST_VOLUMES.put(stream, volume.first);
            }
        }
        return extremes;
    }

    /** @return The smallest volume maximum of all streams. */
    public int getSmallestMax() {
        return mSmallestMax;
    }

    /** @return The largest volume maximum of all streams. */
    public int getLargestMax() {
        return mLargestMax;
    }

    /** Set the volume of all public AudioManager streams. */
    public void adjustVolumeSync(final int direction) {
        // Find the stream with the smallest max volume.
        LOGD(TAG, "adjustVolumeSync(" + direction + ")");
        Pair<Integer, Integer> lowestStream = null;
        for (final int stream : STREAMS) {
            Pair<Integer, Integer> streamPair = STREAM_VOLUMES.get(stream);
            if (null != streamPair) {
                if (null == lowestStream || streamPair.second < lowestStream.second)
                    lowestStream = streamPair;
                if (streamPair.second == lowestStream.second && streamPair.first < lowestStream.first)
                    lowestStream = streamPair;
            }
        }

        // Raise or lower all streams based on this one.
        if (null == lowestStream) return;
        LOGD(TAG, "Lowest stream (" + lowestStream.first + '/' + lowestStream.second + ')');
        // There's an odd exception where volumes can be set higher than their maximum. To avoid
        // this strange issue we use the smallest of the volume and and maximum.
        int newVolume = Math.min(lowestStream.first, lowestStream.second);
        switch (direction) {
            case AudioManager.ADJUST_RAISE:
                // Lower the volume but bound to max.
                newVolume = Math.min(++newVolume, lowestStream.second);
                break;
            case AudioManager.ADJUST_LOWER:
                // Lower the volume but bound to zero.
                newVolume = Math.max(--newVolume, 0);
                break;
        }

        // Don't bother adjusting the volume if there's no change
        // (and this wasn't the intent).
        if (direction != AudioManager.ADJUST_SAME && lowestStream.first == newVolume) {
            LOGD(TAG, "adjustVolumeSync(" + direction + ") ignored, no volume change.");
            return;
        }

        setVolumeSync(newVolume, lowestStream.second);
    }

    /**
     * Synchronize all volumes based on the anomalous stream. This happens
     * when the screen is off and we cannot handle volume change directly.
     * {@link #syncToStream(int)} uses the difference in a given stream from
     * the average to determine the direction of the change.
     */
    public void syncToStream(int stream) {
        LOGD(TAG, "syncToStream(" + stream + ")");
        mManagedVolume = STREAM_VOLUMES.get(stream);
        syncToManaged();
    }

    protected void syncToManaged() {
        int[] newVolumes = new int[STREAMS.length];
        boolean[] volChange = new boolean[STREAMS.length];

        // Determine and set the expected new volume for each stream.
        for (int i = 0; i < STREAMS.length; ++i) {
            Pair<Integer, Integer> streamPair = STREAM_VOLUMES.get(STREAMS[i]);
            if (null != streamPair) {
                final int newVolume = (mManagedVolume.first * streamPair.second) / mManagedVolume.second;
                volChange[i] = (newVolume != streamPair.first);
                newVolumes[i] = newVolume;
                setVolume(STREAMS[i], newVolume);
            }
        }

        // Batch all calls to change system volume AFTER we've updated our local
        // state. This prevents issues with synchronising and BroadcastReceiver.
        for (int i = 0; i < STREAMS.length; ++i) {
            if (volChange[i]) {
                audioManager.setStreamVolume(STREAMS[i], newVolumes[i], 0);
            }
        }
    }

    /** Set the volume of all public AudioManager streams. */
    public void setVolumeSync(int volume, int max) {
        LOGD(TAG, "setVolumeSync(" + volume + ", " + max + ")");
        mManagedVolume = new Pair<Integer, Integer>(volume, max);
        syncToManaged();
    }

    public int getManagedVolume() {
        return (null == mManagedVolume) ? Integer.MIN_VALUE : mManagedVolume.first;
    }

    public int getManagedMaxVolume() {
        return (null == mManagedVolume) ? Integer.MIN_VALUE : mManagedVolume.second;
    }

    /** @see android.media.AudioManager#getStreamMaxVolume(int) */
    public int getStreamMaxVolume(int streamType) {
        Pair<Integer, Integer> stream = STREAM_VOLUMES.get(streamType);
        return (null == stream) ? Integer.MIN_VALUE : stream.second;
    }

    /** @see android.media.AudioManager#getStreamVolume(int) (int) */
    public int getStreamVolume(int streamType) {
        Pair<Integer, Integer> stream = STREAM_VOLUMES.get(streamType);
        return (null == stream) ? Integer.MIN_VALUE : stream.first;
    }

    /** @return The highest volume a given stream has achieved. */
    public int getHighestVolume(int streamType) {
        return STREAM_HIGHEST_VOLUMES.get(streamType);
    }

    /** Set the highest volume a stream as achieved; used primarily to clear previous value. */
    public void setHighestVolume(int streamType, int highestVolume) {
        STREAM_HIGHEST_VOLUMES.setValueAt(streamType, highestVolume);
    }

    /** Set the volume for an individual stream. */
    public boolean setVolume(int streamType, int volume) {
        Pair<Integer, Integer> stream = STREAM_VOLUMES.get(streamType);
        if (null == stream) return false;
        STREAM_VOLUMES.setValueAt(streamType, new Pair<Integer, Integer>(volume, stream.second));
        if (STREAM_HIGHEST_VOLUMES.get(streamType) < volume) {
            STREAM_HIGHEST_VOLUMES.setValueAt(streamType, volume);
        }
        return true;
    }

    public int size() {
        return STREAM_VOLUMES.size();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName());
        builder.append("#{");
        for (final int stream : STREAMS) {
            Pair<Integer, Integer> streamPair = STREAM_VOLUMES.get(stream);
            if (null != streamPair) {
                builder.append(getStreamName(stream));
                builder.append('=');
                builder.append(streamPair.first);
                builder.append('/');
                builder.append(streamPair.second);
                builder.append(' ');
            }
        }
        builder.append("}");
        return builder.toString();
    }
}