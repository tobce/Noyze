package me.barrasso.android.volume.media;

import android.media.AudioManager;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.popup.VolumePanel;

public enum StreamResources {

	BluetoothSCOStream(VolumePanel.STREAM_BLUETOOTH_SCO,
			R.string.volume_icon_description_bluetooth,
			R.drawable.ic_audio_bt,
			R.drawable.ic_audio_bt,
			false),
	RingerStream(AudioManager.STREAM_RING,
			R.string.volume_icon_description_ringer,
			R.drawable.ic_audio_ring_notif,
			R.drawable.ic_audio_phone_mute,
			true),
	VoiceStream(AudioManager.STREAM_VOICE_CALL,
			R.string.volume_icon_description_incall,
			R.drawable.ic_audio_phone,
			R.drawable.ic_audio_phone,
			false),
	AlarmStream(AudioManager.STREAM_ALARM,
			R.string.volume_alarm,
			R.drawable.ic_audio_alarm,
			R.drawable.ic_audio_alarm_mute,
			true),
	MediaStream(AudioManager.STREAM_MUSIC,
			R.string.volume_icon_description_media,
			R.drawable.ic_audio_vol,
			R.drawable.ic_audio_vol_mute,
			true),
	NotificationStream(AudioManager.STREAM_NOTIFICATION,
			R.string.volume_icon_description_notification,
			R.drawable.ic_audio_notification,
			R.drawable.ic_audio_notification_mute,
			true),
	// for now, use media resources for master volume
	MasterStream(VolumePanel.STREAM_MASTER,
			R.string.volume_icon_description_master,
			R.drawable.ic_audio_vol,
			R.drawable.ic_audio_vol_mute,
			false),
	RemoteStream(VolumePanel.STREAM_REMOTE_MUSIC,
			R.string.volume_icon_description_remote_media,
			R.drawable.ic_media_route_on_holo_dark,
			R.drawable.ic_media_route_disabled_holo_dark,
			false),
    SystemStream(AudioManager.STREAM_SYSTEM,
            R.string.volume_icon_description_system,
            R.drawable.ic_audio_vol,
            R.drawable.ic_audio_vol_mute,
            false);// will be dynamically updated

	int streamType;
	int descRes;
	int iconRes;
	int iconMuteRes;
	// RING, VOICE_CALL & BLUETOOTH_SCO are hidden unless explicitly requested
	boolean show;
    int volume;

	StreamResources(int streamType, int descRes, int iconRes, int iconMuteRes, boolean show) {
		this.streamType = streamType;
		this.descRes = descRes;
		this.iconRes = iconRes;
		this.iconMuteRes = iconMuteRes;
		this.show = show;
	}

    public int getDescRes() { return  descRes; }
    public int getIconMuteRes() { return iconMuteRes; }
    public int getIconRes() { return iconRes; }
    public int getStreamType() { return streamType; }
    public int getVolume() { return volume; }
    public boolean show() { return show; }

    public void setVolume(final int vol) { volume = vol; }
    public void setIconRes(int res) { iconRes = res; }
    public void setIconMuteRes(int res) { iconMuteRes = res; }
    public void setDescRes(int res) { descRes = res; }
    public void show(boolean shows) { show = shows; }

    // List of stream types and their order
    public static final StreamResources[] STREAMS = {
            StreamResources.BluetoothSCOStream,
            StreamResources.RingerStream,
            StreamResources.VoiceStream,
            StreamResources.MediaStream,
            StreamResources.NotificationStream,
            StreamResources.AlarmStream,
            StreamResources.MasterStream,
            StreamResources.RemoteStream,
            StreamResources.SystemStream
    };

    /** @return A {@link me.barrasso.android.volume.media.StreamResources} for a given stream type. */
    public static StreamResources resourceForStreamType(int streamType) {
        for (StreamResources resource : STREAMS)
            if (resource.getStreamType() == streamType)
                return resource;
        return StreamResources.MediaStream;
    }
}