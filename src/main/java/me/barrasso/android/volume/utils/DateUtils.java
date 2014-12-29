package me.barrasso.android.volume.utils;

import android.util.Log;

import java.util.Calendar;
import java.util.Date;

import me.barrasso.android.volume.R;

/**
 * Utility class for date-related functions, mostly that related to
 * controlling which icon to use based on the proximity to a holiday.
 */
public final class DateUtils
{

    /**
     * @return An icon to use for this app, based
     * on the date (seasonal/ holiday icons)!
     */
    public static int AppIcon()
    {
        return AppIcon(null);
    }

    public static int AppIcon(Date reference)
    {
        if (IsWithinRange(ValentinesDay(), reference))
            return R.drawable.ic_launcher_love;
        else if (IsWithinRange(EasterDate(), reference))
            return R.drawable.ic_launcher_easter;
        else if (IsWithinRange(Halloween(), reference))
            return R.drawable.ic_launcher_halloween;
        else if (IsWithinRange(ThanksgivingObserved(), reference))
            return R.drawable.ic_launcher_thnx;
        else if (IsWithinRange(ChristmasDay(), reference))
            return R.drawable.ic_launcher_xmas;
        return R.drawable.ic_launcher;
    }

    /**
     * For testing the accuracy of this class. Not to be used
     * in a public build (should be calibrated beforehand).
     */
    public static void test()
    {
        Date valentines = ValentinesDay();
        Date easter = EasterDate();
        Date halloween = Halloween();
        Date thanksgiving = ThanksgivingObserved();
        Date christmas = ChristmasDay();

        Log.i("DateUtils", "Valentines: " + valentines.toString());
        Log.i("DateUtils", "Easter: " + easter.toString());
        Log.i("DateUtils", "Halloween: " + halloween.toString());
        Log.i("DateUtils", "Thanksgiving: " + thanksgiving.toString());
        Log.i("DateUtils", "Christmas: " + christmas.toString());
    }

    /**
     * @return True if the target date is within a range relevant
     * for this application (14 days before, 1 day after).
     */
    public static boolean IsWithinRange(Date target)
    {
        return IsWithinRange(target, null);
    }

    /** @see {@link #IsWithinRange(Date) } */
    public static boolean IsWithinRange(Date target, Date reference)
    {
        Calendar cal = Calendar.getInstance();
        if (null == reference)
            reference = cal.getTime();
        cal.setTime(target);
        cal.add(Calendar.DATE, 1);
        Date max = cal.getTime();
        cal.setTime(target);
        cal.add(Calendar.DATE, -14);
        Date min = cal.getTime();
        return reference.after(min) && reference.before(max);
    }

    public static Date ThanksgivingObserved()
    {
        int nX;
        int nMonth = 10; // November
        Date dtD;
        int nYear = Calendar.getInstance().get(Calendar.YEAR);
        dtD = NewDate(nYear, nMonth, 1); // November
        Calendar cal = Calendar.getInstance();
        cal.setTime(dtD);
        nX = cal.get(Calendar.DAY_OF_WEEK);
        switch(nX)
        {
            case Calendar.SUNDAY : // Sunday
            case Calendar.MONDAY : // Monday
            case Calendar.TUESDAY : // Tuesday
            case Calendar.WEDNESDAY : // Wednesday
            case Calendar.THURSDAY : // Thursday
                // This would be 26 - nX, but DAY_OF_WEEK starts at SUNDAY (1)
                return NewDate(nYear, nMonth, 27 - nX);
            case Calendar.FRIDAY : // Friday
                return NewDate(nYear, nMonth, 28);
            case Calendar.SATURDAY: // Saturday
                return NewDate(nYear, nMonth, 27);
        }
        return NewDate(nYear, nMonth, 27);
    }

    public static Date ValentinesDay()
    {
        return NewDate(Calendar.getInstance().get(Calendar.YEAR), 1, 14); // Feb, 14th
    }

    public static Date Halloween()
    {
        return NewDate(Calendar.getInstance().get(Calendar.YEAR), 9, 31); // Oct, 31st
    }

    public static Date ChristmasDay()
    {
        return NewDate(Calendar.getInstance().get(Calendar.YEAR), 11, 25); // Dec, 25th
    }

    public static Date EasterDate()
    {
        return EasterDate(Calendar.getInstance().get(Calendar.YEAR));
    }

    public static Date EasterDate(final int Y)
    {
        int a = Y % 19;
        int b = Y / 100;
        int c = Y % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int L = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * L) / 451;
        int month = (h + L - 7 * m + 114) / 31;
        int day = ((h + L - 7 * m + 114) % 31) + 1;
        return NewDate(Y, month - 1, day);
    }

    public static Date NewDate(int year, int month, int day)
    {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        return cal.getTime();
    }
}