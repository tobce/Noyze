package me.barrasso.android.volume.ui.transition;

import android.os.Build;
import android.view.ViewGroup;

public abstract class TransitionCompat {

    public static final int KEY_AUDIO_TRANSITION = 1994;

    public static TransitionCompat get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            return new TransitionKitKat();
        return new TransitionJB();
    }

    abstract public void beginDelayedTransition(ViewGroup container);
    abstract public void beginDelayedTransition(ViewGroup container, Object transition);
    abstract public void putTransition(int key, Object value);
    abstract public void beginDelayedTransition(ViewGroup sceneRoot, int key);
    abstract public Object getAudioTransition();

}