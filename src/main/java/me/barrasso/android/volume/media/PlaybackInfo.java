package me.barrasso.android.volume.media;

import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import java.io.Serializable;

import static me.barrasso.android.volume.LogUtils.LOGE;

public final class PlaybackInfo implements Parcelable, Serializable, Cloneable {

    private static final int DISPLAY_TIMEOUT_MS = 5000; // 5s

    public RemoteController.MetadataEditor metadata;
    public int mState;
    public long mStateChangeTimeMs;
    public long mCurrentPosMs;
    public float mSpeed;
    public int mTransportControlFlags;
    public String mRemotePackageName = "";

    public PlaybackInfo() { }

    public PlaybackInfo(int state, long stateChangeTimeMs, long currentPosMs, float speed, int trransportControlFlags) {
        mState = state;
        mStateChangeTimeMs = stateChangeTimeMs;
        mCurrentPosMs = currentPosMs;
        mSpeed = speed;
        mTransportControlFlags = trransportControlFlags;
    }

    public PlaybackInfo(PlaybackInfo clone) {
        mState = clone.mState;
        mStateChangeTimeMs = clone.mStateChangeTimeMs;
        mCurrentPosMs = clone.mCurrentPosMs;
        mSpeed = clone.mSpeed;
        metadata = clone.metadata;
        mTransportControlFlags = clone.mTransportControlFlags;
        mRemotePackageName = clone.mRemotePackageName;
    }

    public PlaybackInfo(Parcel in) {
        mState = in.readInt();
        mStateChangeTimeMs = in.readLong();
        mCurrentPosMs = in.readLong();
        mSpeed = in.readFloat();
        mTransportControlFlags = in.readInt();
        mRemotePackageName = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mState);
        out.writeLong(mStateChangeTimeMs);
        out.writeLong(mCurrentPosMs);
        out.writeFloat(mSpeed);
        out.writeInt(mTransportControlFlags);
        out.writeString(mRemotePackageName);
    }

    // Localized from com.android.internal.policy.impl.keyguard.KeyguardTransportControlView
    public boolean wasPlayingRecently() {
        switch (mState) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
            case RemoteControlClient.PLAYSTATE_REWINDING:
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
            case RemoteControlClient.PLAYSTATE_BUFFERING:
                // actively playing or about to play
                return true;
            case RemoteControlClient.PLAYSTATE_STOPPED:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            case RemoteControlClient.PLAYSTATE_ERROR:
                return ((SystemClock.elapsedRealtime() - mStateChangeTimeMs) < DISPLAY_TIMEOUT_MS);
            default:
                LOGE("PlaybackInfo", "Unknown playback state " + mState + " in wasPlayingRecently()");
                return false;
        }
    }

    public static final Parcelable.Creator<PlaybackInfo> CREATOR
            = new Parcelable.Creator<PlaybackInfo>() {
        @Override public PlaybackInfo createFromParcel(Parcel in) {
            return new PlaybackInfo(in);
        }
        @Override public PlaybackInfo[] newArray(int size) {
            return new PlaybackInfo[size];
        }
    };

    @Override public int describeContents() { return 0; }

    @Override
    public Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException e) { /* Ignored. */ }

        if (null == clone)
            clone = new PlaybackInfo(this);
        return clone;
    }

    @Override public String toString() {
        return getClass().getSimpleName() + "@{" +
                "state=" + mState + ", " +
                "posMs=" + mCurrentPosMs + ", " +
                "cngMs=" + mStateChangeTimeMs + ", " +
                "speed=" + mSpeed + ", " +
                "transFlags=" + mTransportControlFlags + ", " +
                "package=" + mRemotePackageName + "}";
    }

    @Override public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof PlaybackInfo)) return false;
        PlaybackInfo state1 = (PlaybackInfo) obj;
        return (state1.mSpeed == mSpeed                                 &&
                state1.mState == mState                                 &&
                state1.mCurrentPosMs == mCurrentPosMs                   &&
                state1.mStateChangeTimeMs == mStateChangeTimeMs         &&
                state1.mTransportControlFlags == mTransportControlFlags &&
                ((mRemotePackageName == null) ? state1.mRemotePackageName == null : state1.mRemotePackageName.equals(mRemotePackageName)) &&
                ((metadata == null) ? (state1.metadata == null) : state1.metadata.equals(metadata)));

    }
}