package me.barrasso.android.volume.ui.transition;

import android.annotation.TargetApi;
import android.os.Build;
import android.transition.ChangeBounds;
import android.transition.ChangeText;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.SparseArray;
import android.view.ViewGroup;

import me.barrasso.android.volume.ui.AcceleratedTransitionListener;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class TransitionKitKat extends TransitionCompat {

    private final SparseArray<Transition> mTransitions = new SparseArray<Transition>();

    public TransitionKitKat() {
        putTransition(KEY_AUDIO_TRANSITION, getAudioTransition());
    }

    @Override public void beginDelayedTransition(ViewGroup sceneRoot) {
        TransitionManager.beginDelayedTransition(sceneRoot);
    }

    @Override public void beginDelayedTransition(ViewGroup sceneRoot, int key) {
        beginDelayedTransition(sceneRoot, mTransitions.get(key));
    }

    @Override public void beginDelayedTransition(ViewGroup sceneRoot, Object transition) {
        if (transition instanceof Transition) {
            Transition trans = (Transition) transition;
            trans.addListener(AcceleratedTransitionListener.get());
            TransitionManager.beginDelayedTransition(sceneRoot, trans);
        }
    }

    @Override public void putTransition(int key, Object value) {
        if (value instanceof Transition) {
            mTransitions.put(key, (Transition) value);
        }
    }

    private static final int TRANSITION_DURATION = 200;

    @Override public Object getAudioTransition() {
        final ChangeText tc = new ChangeText();
        tc.setChangeBehavior(ChangeText.CHANGE_BEHAVIOR_OUT_IN);
        final TransitionSet inner = new TransitionSet();
        inner.addTransition(tc).addTransition(new ChangeBounds());
        final TransitionSet tg = new TransitionSet();
        tg.addTransition(new Fade(Fade.OUT)).addTransition(inner).
                addTransition(new Fade(Fade.IN));
        tg.setOrdering(TransitionSet.ORDERING_SEQUENTIAL);
        tg.setDuration(TRANSITION_DURATION);
        return tg;
    }

}