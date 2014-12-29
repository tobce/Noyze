package me.barrasso.android.volume.media;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Pair;

import me.barrasso.android.volume.media.compat.RemoteControlCompat;
import me.barrasso.android.volume.utils.Utils;

import static me.barrasso.android.volume.LogUtils.LOGI;

/**
 * Used for "metachanged" broadcasts to get {@link Metadata}
 * information from them. Different apps have different extras they send, but this class
 * doesn't necessarily know state so it's best to be as agnostic as possible.
 */
public final class MediaEventResponder {

    public static enum PlayState {
        START("START"), RESUME("RESUME"), PAUSE("PAUSE"), COMPLETE("COMPLETE");

        private String title;
        public String getTitle() { return title; }

        PlayState(String title) {
            this.title = title;
        }
    }

    public static Pair<MediaMetadataCompat, PlaybackStateCompat> respond(Context context, Intent intent) {
        if (null == context || null == intent) return null;
        String mAction = intent.getAction();
        Bundle extras = intent.getExtras();
        if (null == extras) extras = Bundle.EMPTY; // In case we've got nothing.
        MediaMetadataCompat.Builder mBuilder = null;
        PlaybackStateCompat.Builder pBuilder = null;

        int state = PlaybackStateCompat.STATE_NONE;
        long position = 0;

        LOGI("MediaEventResponder", mAction + ", extras=" + Utils.bundle2string(intent.getExtras()));
        if (mAction.startsWith("com.amazon.mp3")) {
            mBuilder = new MediaMetadataCompat.Builder();
            pBuilder = new PlaybackStateCompat.Builder();
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, extras.getString("com.amazon.mp3.artist"));
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString("com.amazon.mp3.track"));
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, extras.getString("com.amazon.mp3.album"));
            state = (isPlaying(extras.getInt("com.amazon.mp3.playstate")) ?
                    PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_STOPPED);
        } else if (mAction.startsWith("com.sonyericsson")) {
            mBuilder = new MediaMetadataCompat.Builder();
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, extras.getString("ARTIST_NAME"));
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString("TRACK_NAME"));
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, extras.getString("ALBUM_NAME"));
        } else {
            // This is the default case, standard API check.
            mBuilder = new MediaMetadataCompat.Builder();
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, extras.getString("artist"));
            mBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, extras.getString("album"));
            if (extras.containsKey("title"))
                mBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString("title"));
            else if (extras.containsKey("track"))
                mBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString("track"));
        }

        // Try the many ways to interpret the play state.
        if (null == pBuilder) {
            pBuilder = new PlaybackStateCompat.Builder();
            String extraKey = null;
            if (extras.containsKey("playstate"))
                extraKey = "playstate";
            else if (extras.containsKey("isPlaying"))
                extraKey = "isPlaying";
            else if (extras.containsKey("playing"))
                extraKey = "playing";
            else if (extras.containsKey("state"))
                extraKey = "state";

            // We still haven't given up, check the action.
            if (TextUtils.isEmpty(extraKey)) {
                boolean bState = false;
                if (mAction.endsWith("endofplayback"))
                    bState = false;
                else if (mAction.endsWith("playbackcomplete"))
                    bState = false;
                else if (mAction.endsWith("ACTION_PLAYBACK_PAUSE")) // SEMC Legacy
                    bState = false;
                else if (mAction.endsWith("ACTION_PAUSED")) // SEMC
                    bState = false;
                else if (mAction.endsWith("ACTION_TRACK_STARTED")) // SEMC Legacy
                    bState = true;
                else if (mAction.endsWith("ACTION_PLAYBACK_PLAY")) // SEMC
                    bState = true;
                state = (bState ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_STOPPED);
            } else {
                state = (extras.getBoolean(extraKey) ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_STOPPED);
            }
        }

        // Some extras we might want to use... might.
        if (extras.containsKey("duration"))
            mBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, extras.getLong("duration"));
        if (extras.containsKey("position"))
            position = extras.getLong("position");

        // Attempt to figure out what app is playing music.
        pBuilder.setState(state, position, 1.0f);
        mBuilder.putString(RemoteControlCompat.METADATA_KEY_PACKAGE, packageForAction(mAction));

        // Workaround for Google Play Music... not the best :(
        if (extras.containsKey("previewPlayType") && extras.containsKey("supportsRating") &&
                extras.containsKey("currentContainerId"))
            mBuilder.putString(RemoteControlCompat.METADATA_KEY_PACKAGE, "com.google.android.music");

        // Workaround for Poweramp... should be pretty specific.
        if (extras.containsKey("com.maxmpz.audioplayer.source"))
            mBuilder.putString(RemoteControlCompat.METADATA_KEY_PACKAGE, "com.maxmpz.audioplayer");

        return Pair.create(mBuilder.build(), pBuilder.build());
    }

    private static boolean isPlaying(final int state) {
        if (state < 0) return false;
        final PlayState[] states = PlayState.values();
        if (state >= states.length) return false;
        switch (states[state]) {
            case START:
            case RESUME:
                return true;
        }
        return false;
    }

    public static String packageForAction(String action) {
        if (TextUtils.isEmpty(action)) return null;
        if (action.startsWith("com.android.music")) // AOSP
            return "com.android.music";
        if (action.startsWith("com.htc.music")) // HTC
            return "com.htc.music";
        if (action.startsWith("com.amazon.mp3")) // Amazon MP3
            return "com.amazon.mp3";
        if (action.startsWith("com.sonyericsson")) // WALKMAN
            return "com.sonyericsson.music";
        if (action.startsWith("app.odesanmi.and.wpmusic")) // ZPlayer
            return "app.odesanmi.and.wpmusic";
        if (action.startsWith("fm.last.android"))
            return "fm.last.android";
        if (action.startsWith("com.sec.android.app.music")) // Samsung
            return "com.sec.android.app.music";
        if (action.startsWith("com.miui.player")) // MIUI
            return "com.miui.player";
        if (action.startsWith("com.real.RealPlayer")) // RealPlayer
            return "com.real.RealPlayer";
        if (action.startsWith("com.rdio.android")) // Rdio
            return "com.rdio.android";
        if (action.startsWith("com.andrew.apollo")) // Apollo (CyanogenMod)
            return "com.andrew.apollo";
        if (action.startsWith("com.mog.android")) // MOG Mobile Music
            return "com.mog.android";
        if (action.startsWith("com.musixmatch.android")) // musiXmatch
            return "com.musixmatch.android";
        if (action.startsWith("com.doubleTwist.androidPlayer")) // DoubleTwist
            return "com.doubleTwist.androidPlayer";
        if (action.startsWith("com.samsung.sec.android.MusicPlayer")) // More Samsung
            return "com.samsung.sec.android.MusicPlayer";
        if (action.startsWith("com.samsung.music")) // Still more Samsung
            return "com.samsung.music";
        if (action.startsWith("com.spotify")) // Spotify
            return "com.spotify.music";
        if (action.startsWith("com.tbig.playerprotrial")) // PlayerPro Trial
            return "com.tbig.playerprotrial";
        if (action.startsWith("com.tbig.playerpro")) // PlayerPro
            return "com.tbig.playerpro"; // MUST GO AFTER TRIAL!!!
        if (action.startsWith("com.jrtstudio.AnotherMusicPlayer")) // Rocket Music Player
            return "com.jrtstudio.AnotherMusicPlayer";
        if (action.startsWith("com.lge.music")) // LG Music (Optimus 4X)
            return "com.lge.music";
        if (action.startsWith("com.jrtstudio.music")) // JRT Studio AOSP Music
            return "com.jrtstudio.music";
        if (action.startsWith("com.rhapsody")) // Rhapsody
            return "com.rhapsody";
        if (action.startsWith("org.iii.romulus.meridian")) // Meridian Player Transcend
            return "org.iii.romulus.meridian";
        if (action.startsWith("org.abrantix.rockon.rockonnggl")) // DoubleTwist Cubed
            return "org.abrantix.rockon.rockonnggl";
        return null; // Default
    }
}