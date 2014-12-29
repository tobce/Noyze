/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.transition;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.graphics.Interpolator;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This transition tracks changes to the visibility of target views in the
 * start and end scenes and translates views in or out when they become visible
 * or non-visible. Visibility is determined by both the
 * {@link View#setVisibility(int)} state of the view as well as whether it
 * is parented in the current view hierarchy.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class ChangeTranslation extends Visibility {

    private static final String PROPNAME_TRANSLATE_X = "android:translationchange:translateX";
    private static final String PROPNAME_TRANSLATE_Y = "android:translationchange:translateY";

    private static final TimeInterpolator sAccelerator = new AccelerateInterpolator();
    private static final TimeInterpolator sDecelerator = new DecelerateInterpolator();

    private static final String[] sTransitionProperties = {
            PROPNAME_TRANSLATE_X, PROPNAME_TRANSLATE_Y
    };

    /**
     * Translation mode used in {@link #ChangeTranslation(int)} to make the transition
     * operate on targets that are appearing. Maybe be combined with
     * {@link #OUT} to translate both in and out.
     */
    public static final int IN = 0x1;

    /**
     * Translation mode used in {@link #ChangeTranslation(int)} to make the transition
     * operate on targets that are disappearing. Maybe be combined with
     * {@link #OUT} to translate both in and out.
     */
    public static final int OUT = 0x2;

    /**
     * Translation direction used in {@link #ChangeTranslation(int, int)}
     */
    public static final int X = 0x1;

    /**
     * Translation direction used in {@link #ChangeTranslation(int, int)}
     */
    public static final int Y = 0x2;

    private int mTranslationMode = OUT;
    private int mTranslationDirection = X;

    public ChangeTranslation() {
        this(IN | OUT);
    }

    public ChangeTranslation(int translationMode) {
        this(translationMode, X);
    }

    public ChangeTranslation(int translationMode, int translationDirection) {
        mTranslationMode = translationMode;
        mTranslationDirection = translationDirection;
    }

    @Override public String[] getTransitionProperties() {
        return sTransitionProperties;
    }

    /**
     * Returns the type of changing animation that will be run.
     * @return either {@link #IN}, {@link #OUT}, or both.
     */
    public int getTranslationMode() {
        return mTranslationMode;
    }

    /**
     * Returns the direction of changing animation that will be run.
     * @return either {@link #X}, {@link #Y}, or both.
     */
    public int getTranslationDirection() { return mTranslationDirection; }

    private void captureValues(TransitionValues transitionValues) {
        transitionValues.values.put(PROPNAME_TRANSLATE_X, transitionValues.view.getTranslationX());
        transitionValues.values.put(PROPNAME_TRANSLATE_Y, transitionValues.view.getTranslationX());
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        captureValues(transitionValues);
    }

    private static boolean equals(float[] one, float[] two) {
        if (one == null) return (two == null);
        for (int i = 0; i < one.length; ++i)
            if (one[i] != two[i])
                return false;
        return true;
    }

    /**
     * Utility method to handle creating and running the Animator.
     */
    private Animator createAnimation(View view, float[] startTranslation, float[] endTranslation,
                                     Animator.AnimatorListener listener, TimeInterpolator interpolator) {
        if (startTranslation == null || endTranslation == null ||
                startTranslation.length != endTranslation.length || equals(startTranslation, endTranslation)) {
            // run listener if we're noop'ing the animation, to get the end-state results now
            if (listener != null) {
                listener.onAnimationEnd(null);
            }
            return null;
        }

        final AnimatorSet anim = new AnimatorSet();
        ObjectAnimator animX = null, animY = null;

        if ((mTranslationDirection & X) != 0) {
            animX = ObjectAnimator.ofFloat(view, View.X,
                    startTranslation[0], endTranslation[0]);
        }

        if ((mTranslationDirection & Y) != 0) {
            animY = ObjectAnimator.ofFloat(view, View.Y,
                    startTranslation[1], endTranslation[1]);
        }

        if (null != animX && null == animY) anim.play(animX);
        if (null == animX && null != animY) anim.play(animY);
        if (null != animX && null != animY) anim.playTogether(animX, animY);
        if (null == animX && null == animY) return null;

        if (listener != null) {
            anim.addListener(listener);
        }

        anim.setInterpolator(interpolator);
        return anim;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot,
                             TransitionValues startValues, int startVisibility,
                             final TransitionValues endValues, int endVisibility) {
        if ((mTranslationMode & IN) != IN || endValues == null) {
            return null;
        }
        final View endView = endValues.view;
        final int startY = 2 * endView.getHeight();
        final int startX = 2 * endView.getWidth();
        final int endX = 0;
        final int endY = 0;
        return createAnimator(endView, new float[]{startX, startY}, new float[]{endX, endY}, sAccelerator);
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot,
                                TransitionValues startValues, int startVisibility,
                                TransitionValues endValues, int endVisibility) {
        if ((mTranslationMode & OUT) != OUT || endValues == null) {
            return null;
        }
        final View endView = endValues.view;
        final int endY = -2 * endView.getHeight();
        final int endX = -2 * endView.getWidth();
        final int startX = 0;
        final int startY = 0;

        return createAnimator(endView, new float[] { startX, startY }, new float[] { endX, endY }, sDecelerator);
    }

    protected Animator createAnimator(final View endView, final float[] startTranslation,
                                      final float[] endTranslation, TimeInterpolator interpolator) {
        TransitionListener transitionListener = new TransitionListener() {
            boolean mCanceled = false;
            float mPausedX, mPausedY;

            @Override public void onTransitionCancel(Transition transition) {
                jumpToEnd();
                mCanceled = true;
            }

            @Override public void onTransitionStart(Transition transition) { }

            @Override public void onTransitionEnd(Transition transition) {
                if (!mCanceled) {
                    jumpToEnd();
                }
            }

            @Override public void onTransitionPause(Transition transition) {
                mPausedX = endView.getTranslationX();
                mPausedY = endView.getTranslationY();
                jumpToEnd();
            }

            @Override public void onTransitionResume(Transition transition) {
                if ((mTranslationDirection & X) != 0)
                    endView.setTranslationX(mPausedX);
                if ((mTranslationDirection & Y) != 0)
                    endView.setTranslationY(mPausedY);
            }

            protected void jumpToEnd() {
                if ((mTranslationDirection & X) != 0)
                    endView.setTranslationX(endTranslation[0]);
                if ((mTranslationDirection & Y) != 0)
                    endView.setTranslationY(endTranslation[1]);
            }
        };
        addListener(transitionListener);
        return createAnimation(endView, startTranslation, endTranslation, null, interpolator);
    }
}