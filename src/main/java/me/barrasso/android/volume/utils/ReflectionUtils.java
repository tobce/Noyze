package me.barrasso.android.volume.utils;

import java.lang.reflect.Method;

import android.os.IInterface;
import android.os.IBinder;
import android.util.Log;

import me.barrasso.android.volume.LogUtils;

/**
 * Utility class mostly for interfacing with low-level or hidden APIs.
 */
public final class ReflectionUtils {
	
	// Cached system Context and ServiceManager.
	private static IInterface sServiceManager;
	private static IBinder sContext;
	private static Object wManagerGlobal;

	/** @return A global, system {@link IBinder} object. */
	public static IBinder getContext() {
		if (sContext != null)
            return sContext;

		try {
			Class<?> mBinder = Class.forName("com.android.internal.os.BinderInternal");
			if (mBinder == null) return null;
			Method mMethod = mBinder.getDeclaredMethod("getContextObject");
			if (mMethod == null) return null;
			mMethod.setAccessible(true);
			sContext =  (IBinder) mMethod.invoke(null);
		} catch (Throwable e) {
            LogUtils.LOGE("Reflection", "Error obtaining global Context.", e);
        }
		
		return sContext;
	}

    /** @return A WindowManagerGlobal instance. */
    public static Object getWindowManagerGlobal() {
        if (wManagerGlobal != null)
            return wManagerGlobal;

        try {
            Class<?> mWindowManagerGlobal = Class.forName("android.view.WindowManagerGlobal");
            if (null == mWindowManagerGlobal) return null;
            Method mMethod = mWindowManagerGlobal.getDeclaredMethod("getInstance");
            if (null == mMethod) return null;
            mMethod.setAccessible(true);
            wManagerGlobal = mMethod.invoke(null);
        } catch (Throwable t) {
            LogUtils.LOGE("ReflectionUtils", "Error accessing WindowManagerGlobal.", t);
        }

        return wManagerGlobal;
    }
	
	/**
	 * Obtain the IServiceManager reference for obtaining
	 * system services and such.
	 */
	public static IInterface getIServiceManager() {
        if (sServiceManager != null) {
            return sServiceManager;
        }

        // Find the service manager
        try {
        	Class<?> mManager = Class.forName("android.os.ServiceManagerNative");
        	if (mManager == null) return null;
			Method mMethod = mManager.getDeclaredMethod("asInterface", IBinder.class);
			if (mMethod == null) return null;
			mMethod.setAccessible(true);
			sServiceManager = (IInterface) mMethod.invoke(null, getContext());        	
        } catch (Throwable e) {
            LogUtils.LOGE("Reflection", "Error accessing ServiceManagerNative.", e);
        }
        
        return sServiceManager;
    }
    
    /**
     * ServiceManager.getService(String name)
     * Obtains an {@link IBinder} reference of a system service.
     */
    public static IBinder getService(String name) {
        try {
        	final IInterface mService = ReflectionUtils.getIServiceManager();
			Method mMethod = mService.getClass().getDeclaredMethod(
				"getService", new Class[] { String.class });
			
			if (mMethod == null) return null;
			mMethod.setAccessible(true);
			return (IBinder) mMethod.invoke(mService, name);        	
        } catch (Throwable e) {
            LogUtils.LOGE("Reflection", "Error accessing ServiceManager.getService().", e);
        }
        
        return null;
    }

    /**
     * Obtain the ServiceManager reference for obtaining
     * system services and such.
     */
    public static IBinder getServiceManager(String name) {
        // Find the service manager
        try {
            Class<?> mManager = Class.forName("android.os.ServiceManager");
            if (mManager == null) return null;
            Method mMethod = mManager.getMethod("getService", String.class);
            if (mMethod == null) return null;
            mMethod.setAccessible(true);
            return (IBinder) mMethod.invoke(null, name);
        } catch (Throwable e) {
            LogUtils.LOGE("Reflection", "Error getServiceManager(" + name + ")", e);
        }

        return null;
    }
    
    /**
     * Obtain an {@link IInterface} reference for communicating
     * with a system service.
     */
    public static IInterface getIInterface(String name, String serviceName) {
        try {
        	Class<?> mManager = Class.forName(serviceName);
        	if (mManager == null) return null;
        	IInterface mService = ReflectionUtils.getIServiceManager();
			Method mMethod = mManager.getMethod("asInterface", IBinder.class);
			if (mMethod == null) return null;
			mMethod.setAccessible(true);
			return (IInterface) mMethod.invoke(null, getService(name));        	
        }
        catch (Throwable e) {
            LogUtils.LOGE("Reflection", "Error accessing " + name + " IInteface.", e);
        }
        
        return null;
    }
}