package me.barrasso.android.volume.ui.transition;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.ViewGroup;

/** No-op */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class TransitionJB extends TransitionCompat {

    public TransitionJB() { }

    @Override public void beginDelayedTransition(ViewGroup container) { }
    @Override public void beginDelayedTransition(ViewGroup container, Object transition) { }
    @Override public void putTransition(int key, Object value) { }
    @Override public void beginDelayedTransition(ViewGroup sceneRoot, int key) { }
    @Override public Object getAudioTransition() { return null; }

}