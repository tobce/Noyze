package me.barrasso.android.volume;

import android.test.ActivityInstrumentationTestCase2;

import me.barrasso.android.volume.activities.ConfigurationActivity;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class me.barrasso.android.volume_3.ConfigurationActivityTest \
 * me.barrasso.android.volume_3.tests/android.test.InstrumentationTestRunner
 */
public class ConfigurationActivityTest extends ActivityInstrumentationTestCase2<ConfigurationActivity> {

    public ConfigurationActivityTest() {
        super("me.barrasso.android.volume_3", ConfigurationActivity.class);
    }

}
