package me.barrasso.android.volume.ui;

import android.annotation.TargetApi;
import android.os.Build;
import android.transition.Transition;
import android.view.View;

import java.util.List;

/**
 * Simple class to disable hardware acceleration after an animation.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class AcceleratedTransitionListener implements Transition.TransitionListener {

    private static AcceleratedTransitionListener listener;
    public synchronized static AcceleratedTransitionListener get() {
        if (null == listener)
            listener = new AcceleratedTransitionListener();
        return listener;
    }

    public static void setLayerType(List<View> views, int layerType) {
        for (View view : views)
            view.setLayerType(layerType, null);
    }

    public static void setTransientState(List<View> views, boolean hasState) {
        for (View view : views)
            view.setHasTransientState(hasState);
    }

    @Override
    public void onTransitionStart(Transition transition) {
        setLayerType(transition.getTargets(), View.LAYER_TYPE_HARDWARE);
        setTransientState(transition.getTargets(), true);
    }

    @Override
    public void onTransitionEnd(Transition transition) {
        setLayerType(transition.getTargets(), View.LAYER_TYPE_NONE);
        setTransientState(transition.getTargets(), false);
    }

    @Override
    public void onTransitionCancel(Transition transition) {
        onTransitionEnd(transition);
    }

    @Override
    public void onTransitionPause(Transition transition) {
        onTransitionEnd(transition);
    }

    @Override
    public void onTransitionResume(Transition transition) {
        onTransitionStart(transition);
    }


}
