package com.squareup.otto;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;

import me.barrasso.android.volume.LogUtils;

public class MainThreadBus extends Bus {

    private static Bus BUS;
    public synchronized static Bus get() {
        if (null == BUS)
            BUS = new MainThreadBus(ThreadEnforcer.ANY);
        return BUS;
    }

    public MainThreadBus(ThreadEnforcer enforcer) {
        super(enforcer);
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void post(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            super.post(event);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainThreadBus.super.post(event);
                }
            });
        }
    }

    public void postSafely(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                super.post(event);
            } catch (Throwable t) {
                LogUtils.LOGE("Bus", "Error in posting object.", t);
            }
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        MainThreadBus.super.post(event);
                    } catch (Throwable t) {
                        LogUtils.LOGE("Bus", "Error in posting object.", t);
                    }
                }
            });
        }
    }

    /**
     * Dispatches {@code event} to the handler in {@code wrapper}. This method is an appropriate override point for
     * subclasses that wish to make event delivery asynchronous.
     *
     * @param event event to dispatch.
     * @param wrapper wrapper that will call the handler.
     */
    @Override
    protected void dispatch(Object event, EventHandler wrapper) {
        try {
            if (wrapper.getTarget() instanceof Activity) {
                Activity target = (Activity) wrapper.getTarget();
                if (target.isFinishing() || target.isDestroyed()) return;
                try {
                    wrapper.handleEvent(event);
                } catch (Throwable e) {
                    LogUtils.LOGE("Bus", "Error dispatching event to " + target.getClass().getSimpleName(), e);
                }
            }
            wrapper.handleEvent(event);
        } catch (InvocationTargetException e) {
            throwRuntimeException(
                    "Could not dispatch event: " + event.getClass() + " to handler " + wrapper, e);
        }
    }
}