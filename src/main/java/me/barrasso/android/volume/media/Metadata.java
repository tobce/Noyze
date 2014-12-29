package me.barrasso.android.volume.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataEditor;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Property;
import static android.media.MediaMetadataRetriever.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import me.barrasso.android.volume.utils.Utils;
import static me.barrasso.android.volume.LogUtils.LOGE;

/**
 * Proxy to {@link android.media.MediaMetadataEditor} to allow for
 * easier access and storage of media metadata information.
 */
public final class Metadata {

    public static final String TAG = Metadata.class.getSimpleName();

    public static class PlaybackEvent {
        public PlaybackInfo mPlaybackInfo;
        public Metadata mMetadata;
    }

    public static class PlaybackPosition {
        public long position;
        public PlaybackPosition(long pos) { position = pos; }
    }

    public static class SeekCommand {
        public long position;
        public SeekCommand(long pos) { position = pos; }
    }

    // RemoteControlClient.MetadataEditor#BITMAP_KEY_ARTWORK
    public final static int _BITMAP_KEY_ARTWORK = 100;

    // Localized constant to avoid issues with SDK.
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static int BITMAP_KEY_ARTWORK() {
        return ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ?
            MediaMetadataEditor.BITMAP_KEY_ARTWORK : _BITMAP_KEY_ARTWORK);
    }

    public static final int KEY_HAS_ARTWORK = 1984; // Thanks George Orwell
    public static final int KEY_HAS_REMOTE = 2014; // @Copyright
    public static final String ARTWORK_FILE = "artwork.png"; // Default file name
    public static final int KEY_REMOTE_PACKAGE = 1776; // Independence!
    public static final int KEY_POSITION = 1994; // BEST YEAR EVER!!!

    private static final int[] METADATA_STRING_KEYS = new int[] {
        METADATA_KEY_ALBUM,  METADATA_KEY_ALBUMARTIST,
            METADATA_KEY_ARTIST, METADATA_KEY_AUTHOR,
            METADATA_KEY_COMPOSER, METADATA_KEY_GENRE,
            METADATA_KEY_TITLE, METADATA_KEY_WRITER
    };

    private static final int[] METADATA_LONG_KEYS = new int[] {
            METADATA_KEY_CD_TRACK_NUMBER, METADATA_KEY_DISC_NUMBER,
            METADATA_KEY_YEAR, METADATA_KEY_DURATION
    };

    public static final Property<Metadata, Bundle> BUNDLE = Property.of(Metadata.class, Bundle.class, "bundle");

    private final Bundle bundle;
    
    public Metadata(MediaMetadataEditor editor) {
        bundle = new Bundle();
        bundle.putBoolean(TAG, true); // Leave our mark!
        initFromMetadata(editor);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected void initFromMetadata(MediaMetadataEditor editor) {

        // KitKat and above supports retrieval of album art.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setArtwork(editor.getBitmap(BITMAP_KEY_ARTWORK(), null));
        }

        for (final int intKey : METADATA_STRING_KEYS) {
            String ret = editor.getString(intKey, null);
            if (!TextUtils.isEmpty(ret)) {
                putString(intKey, ret);
            }
        }

        for (final int intKey : METADATA_LONG_KEYS) {
            long ret = editor.getLong(intKey, Long.MIN_VALUE);
            if (ret != Long.MIN_VALUE) {
                putLong(intKey, ret);
            }
        }
    }

    public Metadata(Bundle iBundle){
        this.bundle = iBundle;
    }

    /** Constructor for partial implementation of metadata information. */
    public Metadata() {
        bundle = new Bundle();
        bundle.putBoolean(TAG, true); // Leave our mark!
    }

    public Bundle getBundle() { return bundle; }

    /**
     * Like {@link #getBundle()}, but does not contain a Bitmap to avoid
     * the chances of a {@link android.os.TransactionTooLargeException}.
     * @return A {@link android.os.Bundle} of primitives and strings.
     */
    public Bundle getLightweightBundle() {
        boolean hasArtwork = hasArtwork();
        if (hasArtwork)
            remove(BITMAP_KEY_ARTWORK());
        putBoolean(KEY_HAS_ARTWORK, true);
        return bundle;
    }

    /**
     * Read the stored artwork from disk.
     * @see #ARTWORK_FILE
     * @return The stored artwork, or null if none was found.
     */
    public Bitmap getArtwork(Context context) {
        if (hasArtwork() && bundle.containsKey(File.class.getSimpleName())) {
            File artFile = new File(bundle.getString(File.class.getSimpleName()));
            if (!artFile.exists()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inPreferQualityOverSpeed = false;
            return BitmapFactory.decodeFile(artFile.getAbsolutePath(), options);
        }

        return null;
    }

    /**
     * Write the stored artwork to disk.
     * @see #ARTWORK_FILE
     * @return True if the artwork was written to disk successfully.
     */
    public boolean writeArtwork(Context context) {
        OutputStream fos = null;
        try {
            if (hasArtwork()) {
                File dir = context.getCacheDir();
                File artFile = new File(dir, ARTWORK_FILE);
                fos = new BufferedOutputStream(new FileOutputStream(artFile));
                Bitmap artwork = getArtwork();
                if (null != artwork) {
                    artwork.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    artwork.recycle();
                    putBoolean(KEY_HAS_ARTWORK, true);
                    bundle.putString(File.class.getSimpleName(), artFile.getAbsolutePath());
                    return true;
                }
            }
        } catch (FileNotFoundException fne) {
            LOGE(TAG, "Error creating " + ARTWORK_FILE, fne);
        } finally {
            try {
                if (null != fos) {
                    fos.close();
                }
            } catch (IOException ioe) {
                LOGE(TAG, "Error closing " + FileOutputStream.class.getSimpleName(), ioe);
            }
        }
        return false;
    }

    // Convenience methods for extracting info from metadata.
    public String getAlbum() { return getString(METADATA_KEY_ALBUM, null); }
    public String getArtist() { return getString(METADATA_KEY_ARTIST, null); }
    public String getTitle() { return getString(METADATA_KEY_TITLE, null); }
    public long getDuration() { return getLong(METADATA_KEY_DURATION, 0); }

    public void setAlbum(String album) { putString(METADATA_KEY_ALBUM, album); }
    public void setArtist(String artist) { putString(METADATA_KEY_ARTIST, artist); }
    public void setTitle(String title) { putString(METADATA_KEY_TITLE, title); }
    public void setDuration(long duration) { putLong(METADATA_KEY_DURATION, duration); }

    public void setPlayState(boolean playing) { bundle.putBoolean("playstate", playing); }
    public boolean isPlaying() { return bundle.getBoolean("playstate", false); }
    public boolean hasPlayState() { return bundle.containsKey("playstate"); }
    public void hasRemote(boolean hazIt) { putBoolean(KEY_HAS_REMOTE, hazIt); }
    public boolean hasRemote() { return containsKey(KEY_HAS_REMOTE); }
    public void setRemotePackage(String remotePackage) { putString(KEY_REMOTE_PACKAGE, remotePackage); }
    public String getRemotePackage() { return getString(KEY_REMOTE_PACKAGE, null); }

    /** @return True if this metadata only has the current play state. */
    public boolean isPlayState() {
        return (null != bundle.keySet()) && (bundle.keySet().size() == 1) && hasPlayState();
    }

    public boolean hasArtwork() {
        return containsKey(BITMAP_KEY_ARTWORK()) ||
               containsKey(KEY_HAS_ARTWORK);
    }

    public Bitmap getArtwork() {
        final int key = BITMAP_KEY_ARTWORK();
        if (!containsKey(key)) return null;
        try {
            return (Bitmap) getParcelable(key);
        } catch (ClassCastException cce) {
            LOGE(TAG, "Artwork could not be obtained, not a Bitmap.", cce);
            return null;
        }
    }

    public void setArtwork(Bitmap artwork) {
        putParcelable(BITMAP_KEY_ARTWORK(), artwork);
    }

    public void clearArtwork() {
        // Recycle the old artwork if it exists.
        // Bitmap artwork = getArtwork();
        // if (null != artwork) {
            // artwork.recycle();
        // }
        remove(BITMAP_KEY_ARTWORK());
    }

    public void recycle() {
        clearArtwork();
        bundle.clear();
    }

    @Override
    public String toString() {
        return TAG + '@' + Utils.bundle2string(bundle);
    }

    /** @see android.os.Bundle#remove(String) */
    public void remove(int key) {
        bundle.remove(String.valueOf(key));
    }

    /** @see android.os.Bundle#containsKey(String) */
    public boolean containsKey(int key) {
        return bundle.containsKey(String.valueOf(key));
    }

    /** @see android.os.Bundle#putBoolean(String, boolean) */
    public void putBoolean(int key, boolean value) {
        bundle.putBoolean(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putByte(String, byte) */
    public void putByte(int key, byte value) {
        bundle.putByte(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putChar(String, char) */
    public void putChar(int key, char value) {
        bundle.putChar(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putShort(String, short) */
    public void putShort(int key, short value) {
        bundle.putShort(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putInt(String, int) */
    public void putInt(int key, int value) {
        bundle.putInt(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putLong(String, long) */
    public void putLong(int key, long value) {
        bundle.putLong(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putFloat(String, float) */
    public void putFloat(int key, float value) {
        bundle.putFloat(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putDouble(String, double) */
    public void putDouble(int key, double value) {
        bundle.putDouble(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putString(String, String) */
    public void putString(int key, String value) {
        bundle.putString(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putCharSequence(String, CharSequence) */
    public void putCharSequence(int key, CharSequence value) {
        bundle.putCharSequence(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#putParcelable(String, Parcelable) */
    public void putParcelable(int key, Parcelable value) {
        bundle.putParcelable(String.valueOf(key), value);
    }

    /** @see android.os.Bundle#getBoolean(String, boolean) */
    public boolean getBoolean(int key, boolean defaultValue) {
        return bundle.getBoolean(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getByte(String, byte) */
    public byte getByte(int key, byte defaultValue) {
        return bundle.getByte(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getChar(String, char) */
    public char getChar(int key, char defaultValue) {
        return bundle.getChar(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getShort(String, short) */
    public short getShort(int key, short defaultValue) {
        return bundle.getShort(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getInt(String, int) */
    public int getInt(int key, int defaultValue) {
        return bundle.getInt(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getLong(String, long) */
    public long getLong(int key, long defaultValue) {
        return bundle.getLong(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getFloat(String, float) */
    public float getFloat(int key, float defaultValue) {
        return bundle.getFloat(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getDouble(String, double) */
    public double getDouble(int key, double defaultValue) {
        return bundle.getDouble(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getString(String, String) */
    public String getString(int key, String defaultValue) {
        return bundle.getString(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getCharSequence(String, CharSequence) */
    public CharSequence getCharSequence(int key, CharSequence defaultValue) {
        return bundle.getCharSequence(String.valueOf(key), defaultValue);
    }

    /** @see android.os.Bundle#getBundle(String) */
    public Bundle getBundle(int key) {
        return bundle.getBundle(String.valueOf(key));
    }

    /** @see android.os.Bundle#getParcelable(String) */
    public Parcelable getParcelable(int key) {
        return bundle.getParcelable(String.valueOf(key));
    }
}