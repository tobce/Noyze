package me.barrasso.android.volume.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Property;

import java.util.Set;

/**
 * Simple helper class to deal with getting/ setting {@link android.util.Property} values
 * as well as generic helper methods for setting/ getting preferences.
 */
public final class SettingsHelper {

    private static SettingsHelper mHelper;

    public static synchronized SettingsHelper getInstance(Context context) {
        if (null == mHelper)
            mHelper = new SettingsHelper(context);
        return mHelper;
    }

    private final SharedPreferences mPreferences;

    private SettingsHelper(Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public SharedPreferences getSharedPreferences() { return mPreferences; }

    private SharedPreferences.Editor edit() {
        return mPreferences.edit();
    }

    public void registerOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    /** @return The preference name for a given property. */
    public <T, E> String getName(Class<T> clazz, Property<T, E> property) {
        return clazz.getSimpleName() + '_' + property.getName();
    }

    public <T, E> int getIntProperty(Class<T> clazz, Property<T, E> property, int defVal)
            throws ClassCastException, NumberFormatException {
        return Integer.parseInt(get(getName(clazz, property), String.valueOf(defVal)), 10);
    }

    /**
     * Sets the value for a given {@link android.util.Property}. Uses
     * {@link android.content.SharedPreferences.Editor#apply()} to store asynchronously.
     * @return True if the value was successfully applied, false if else.
     * @throws ClassCastException If a type error occurred between SP and Property.
     */
    @SuppressWarnings("unchecked")
    public <T, E> boolean setProperty(Class<T> clazz, Property<T, E> property, E val)
            throws ClassCastException {
        Class<E> type = property.getType();
        String name = getName(clazz, property);
        SharedPreferences.Editor editor = edit();

        // Handle all types supported by SharedPreferences.
        if (type.equals(Integer.TYPE) || type.equals(Integer.class))
            editor.putInt(name, (Integer) val);
        else if (type.equals(String.class) || type.equals(CharSequence.class))
            editor.putString(name, val.toString());
        else if (type.equals(Boolean.TYPE) || type.equals(Boolean.class))
            editor.putBoolean(name, (Boolean) val);
        else if (type.equals(Long.TYPE) || type.equals(Long.class))
            editor.putLong(name, (Long) val);
        else if (type.equals(Float.TYPE) || type.equals(Float.class))
            editor.putFloat(name, (Float) val);
        else if (type.getClass().isAssignableFrom(Set.class))
            editor.putStringSet(name, (Set<String>) val);
        else
            editor = null;

        if (null == editor) return false;
        editor.apply();
        return true;
    }

    /** @see android.content.SharedPreferences#contains(String) */
    public <T, V> boolean hasProperty(Class<T> clazz, Property<T, V> property) {
        String name = getName(clazz, property);
        return mPreferences.contains(name);
    }

    /**
     * Retrieves the value stored in {@link android.content.SharedPreferences} for a
     * given {@link android.util.Property} associated with a VolumePanel.
     * @return The given value, {@code defVal} if none was set, or null is the
     * value could not be retrieved.
     * @throws ClassCastException If a type error occurred between SP and Property.
     */
    @SuppressWarnings("unchecked")
    public <T, E> E getProperty(Class<T> clazz, Property<T, E> property, E defVal)
            throws ClassCastException {
        Class<E> type = property.getType();
        String name = getName(clazz, property);

        // Handle all types supported by SharedPreferences.
        if (type.equals(Integer.TYPE) || type.equals(Integer.class))
            return (E) Integer.valueOf(mPreferences.getInt(name, (Integer) defVal));
        else if (type.equals(String.class) || type.equals(CharSequence.class))
            return (E) mPreferences.getString(name, ((defVal == null) ? (String) defVal : defVal.toString()));
        else if (type.equals(Boolean.TYPE) || type.equals(Boolean.class))
            return (E) Boolean.valueOf(mPreferences.getBoolean(name, (Boolean) defVal));
        else if (type.equals(Long.TYPE) || type.equals(Long.class))
            return (E) Long.valueOf(mPreferences.getLong(name, (Long) defVal));
        else if (type.equals(Float.TYPE) || type.equals(Float.class))
            return (E) Float.valueOf(mPreferences.getFloat(name, (Float) defVal));
        else if (type.getClass().isAssignableFrom(Set.class))
            return (E) mPreferences.getStringSet(name, (Set<String>) defVal);

        return defVal;
    }

    /** Generic get method, default value cannot be null! */
    @SuppressWarnings("unchecked")
    public final <T> T get(final String name, T defValue) {
        if (TextUtils.isEmpty(name) || null == defValue) return null;

        if (defValue instanceof Boolean)
            return (T) Boolean.valueOf(mPreferences.getBoolean(name, (Boolean) defValue));
        else if (defValue instanceof String)
            return (T) String.valueOf(mPreferences.getString(name, (String) defValue));
        else if (defValue instanceof Float)
            return (T) Float.valueOf(mPreferences.getFloat(name, (Float) defValue));
        else if (defValue instanceof Integer)
            return (T) Integer.valueOf(mPreferences.getInt(name, (Integer) defValue));
        else if (defValue instanceof Long)
            return (T) Long.valueOf(mPreferences.getLong(name, (Long) defValue));
        else if (defValue.getClass().isAssignableFrom(Set.class))
            return (T) mPreferences.getStringSet(name, (Set<String>) defValue);

        return defValue;
    }

    /** Generic set method, default value cannot be null! */
    @SuppressWarnings("unchecked")
    public final <T> boolean set(final String name, T value) {
        if (TextUtils.isEmpty(name) || null == value) return false;

        SharedPreferences.Editor editor = edit();
        if (value instanceof Boolean)
            editor.putBoolean(name, (Boolean) value);
        else if (value instanceof String)
            editor.putString(name, (String) value);
        else if (value instanceof Float)
            editor.putFloat(name, (Float) value);
        else if (value instanceof Integer)
            editor.putInt(name, (Integer) value);
        else if (value instanceof Long)
            editor.putLong(name, (Long) value);
        else if (value.getClass().isAssignableFrom(Set.class))
           editor.putStringSet(name, (Set<String>) value);
        else
            editor = null;

        if (null == editor) return false;
        editor.apply();
        return true;
    }

}