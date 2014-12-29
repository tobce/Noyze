package me.barrasso.android.volume.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Xfermode;
import android.widget.ImageView;

import java.lang.reflect.Method;

import me.barrasso.android.volume.LogUtils;

public final class ColorHelper
{
    private static Method xfremode;

    @SuppressWarnings("unused")
    public static void setXfermode(ImageView icon, Xfermode mode) {
        try {
            // Try to load/ cache the xfermode method.
            if (null == xfremode) {
                xfremode = ImageView.class.getDeclaredMethod("setXfermode", Xfermode.class);
                if (null != xfremode) xfremode.setAccessible(true);
            }

            if (null != xfremode) xfremode.invoke(icon, mode);
        } catch (Throwable t) {
            LogUtils.LOGE("ColorHelper", "Error with ImageView#setXfermode", t);
        }
    }

    public static int noAlpha(final int color) {
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int getDominantColor(Bitmap bitmap) {
        if (null == bitmap) return Color.TRANSPARENT;

        int redBucket = 0;
        int greenBucket = 0;
        int blueBucket = 0;
        int alphaBucket = 0;

        boolean hasAlpha = bitmap.hasAlpha();
        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int y = 0, h = bitmap.getHeight(); y < h; y++)
        {
            for (int x = 0, w = bitmap.getWidth(); x < w; x++)
            {
                int color = pixels[x + y * w]; // x + y * barHeight
                redBucket += (color >> 16) & 0xFF; // Color.red
                greenBucket += (color >> 8) & 0xFF; // Color.greed
                blueBucket += (color & 0xFF); // Color.blue
                if (hasAlpha) alphaBucket += (color >>> 24); // Color.alpha
            }
        }

        return Color.argb(
            (hasAlpha) ? (alphaBucket / pixelCount) : 255,
            redBucket / pixelCount,
            greenBucket / pixelCount,
            blueBucket / pixelCount);
    }

    /**
     * Returns the hue component of a color int.
     *
     * @return A value between 0.0f and 1.0f
     *
     * @hide Pending API council
     */
    public static float hue(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int V = Math.max(b, Math.max(r, g));
        int temp = Math.min(b, Math.min(r, g));

        float H;

        if (V == temp) {
            H = 0;
        } else {
            final float vtemp = (float) (V - temp);
            final float cr = (V - r) / vtemp;
            final float cg = (V - g) / vtemp;
            final float cb = (V - b) / vtemp;

            if (r == V) {
                H = cb - cg;
            } else if (g == V) {
                H = 2 + cr - cb;
            } else {
                H = 4 + cg - cr;
            }

            H /= 6.f;
            if (H < 0) {
                H++;
            }
        }

        return H;
    }

    /**
     * Returns the saturation component of a color int.
     *
     * @return A value between 0.0f and 1.0f
     *
     * @hide Pending API council
     */
    public static float saturation(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;


        int V = Math.max(b, Math.max(r, g));
        int temp = Math.min(b, Math.min(r, g));

        float S;

        if (V == temp) {
            S = 0;
        } else {
            S = (V - temp) / (float) V;
        }

        return S;
    }

    /**
     * Returns the brightness component of a color int.
     *
     * @return A value between 0.0f and 1.0f
     *
     * @hide Pending API council
     */
    public static float brightness(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int V = Math.max(b, Math.max(r, g));

        return (V / 255.f);
    }

    public static class ColorFilterGenerator {

        private static double DELTA_INDEX[] = {
                0, 0.01, 0.02, 0.04, 0.05, 0.06, 0.07, 0.08, 0.1, 0.11,
                0.12, 0.14, 0.15, 0.16, 0.17, 0.18, 0.20, 0.21, 0.22, 0.24,
                0.25, 0.27, 0.28, 0.30, 0.32, 0.34, 0.36, 0.38, 0.40, 0.42,
                0.44, 0.46, 0.48, 0.5, 0.53, 0.56, 0.59, 0.62, 0.65, 0.68,
                0.71, 0.74, 0.77, 0.80, 0.83, 0.86, 0.89, 0.92, 0.95, 0.98,
                1.0, 1.06, 1.12, 1.18, 1.24, 1.30, 1.36, 1.42, 1.48, 1.54,
                1.60, 1.66, 1.72, 1.78, 1.84, 1.90, 1.96, 2.0, 2.12, 2.25,
                2.37, 2.50, 2.62, 2.75, 2.87, 3.0, 3.2, 3.4, 3.6, 3.8,
                4.0, 4.3, 4.7, 4.9, 5.0, 5.5, 6.0, 6.5, 6.8, 7.0,
                7.3, 7.5, 7.8, 8.0, 8.4, 8.7, 9.0, 9.4, 9.6, 9.8,
                10.0
        };

        /**
         * @param cm
         * @param value
         * @see http://groups.google.com/group/android-developers/browse_thread/thread/9e215c83c3819953
         * @see http://gskinner.com/blog/archives/2007/12/colormatrix_cla.html
         */
        public static void adjustHue(ColorMatrix cm, float value) {
            value = cleanValue(value, 180f) / 180f * (float) Math.PI;
            if (value == 0) {
                return;
            }

            float cosVal = (float) Math.cos(value);
            float sinVal = (float) Math.sin(value);
            float lumR = 0.213f;
            float lumG = 0.715f;
            float lumB = 0.072f;
            float[] mat = new float[]
                    {
                            lumR + cosVal * (1 - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0, 0,
                            lumR + cosVal * (-lumR) + sinVal * (0.143f), lumG + cosVal * (1 - lumG) + sinVal * (0.140f), lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0, 0,
                            lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)), lumG + cosVal * (-lumG) + sinVal * (lumG), lumB + cosVal * (1 - lumB) + sinVal * (lumB), 0, 0,
                            0f, 0f, 0f, 1f, 0f,
                            0f, 0f, 0f, 0f, 1f};
            cm.postConcat(new ColorMatrix(mat));
        }

        public static void adjustBrightness(ColorMatrix cm, float value) {
            value = cleanValue(value, 100);
            if (value == 0) {
                return;
            }

            float[] mat = new float[]
                    {
                            1, 0, 0, 0, value,
                            0, 1, 0, 0, value,
                            0, 0, 1, 0, value,
                            0, 0, 0, 1, 0,
                            0, 0, 0, 0, 1
                    };
            cm.postConcat(new ColorMatrix(mat));
        }

        public static void adjustContrast(ColorMatrix cm, int value) {
            value = (int) cleanValue(value, 100);
            if (value == 0) {
                return;
            }
            float x;
            if (value < 0) {
                x = 127 + value / 100 * 127;
            } else {
                x = value % 1;
                if (x == 0) {
                    x = (float) DELTA_INDEX[value];
                } else {
                    //x = DELTA_INDEX[(p_val<<0)]; // this is how the IDE does it.
                    x = (float) DELTA_INDEX[(value << 0)] * (1 - x) + (float) DELTA_INDEX[(value << 0) + 1] * x; // use linear interpolation for more granularity.
                }
                x = x * 127 + 127;
            }

            float[] mat = new float[]
                    {
                            x / 127, 0, 0, 0, 0.5f * (127 - x),
                            0, x / 127, 0, 0, 0.5f * (127 - x),
                            0, 0, x / 127, 0, 0.5f * (127 - x),
                            0, 0, 0, 1, 0,
                            0, 0, 0, 0, 1
                    };
            cm.postConcat(new ColorMatrix(mat));

        }

        public static void adjustSaturation(ColorMatrix cm, float value) {
            value = cleanValue(value, 100);
            if (value == 0) {
                return;
            }

            float x = 1 + ((value > 0) ? 3 * value / 100 : value / 100);
            float lumR = 0.3086f;
            float lumG = 0.6094f;
            float lumB = 0.0820f;

            float[] mat = new float[]
                    {
                            lumR * (1 - x) + x, lumG * (1 - x), lumB * (1 - x), 0, 0,
                            lumR * (1 - x), lumG * (1 - x) + x, lumB * (1 - x), 0, 0,
                            lumR * (1 - x), lumG * (1 - x), lumB * (1 - x) + x, 0, 0,
                            0, 0, 0, 1, 0,
                            0, 0, 0, 0, 1
                    };
            cm.postConcat(new ColorMatrix(mat));
        }


        protected static float cleanValue(float p_val, float p_limit) {
            return Math.min(p_limit, Math.max(-p_limit, p_val));
        }

        public static ColorFilter adjustColor(int brightness, int contrast, int saturation, int hue) {
            ColorMatrix cm = new ColorMatrix();
            adjustHue(cm, hue);
            adjustContrast(cm, contrast);
            adjustBrightness(cm, brightness);
            adjustSaturation(cm, saturation);

            return new ColorMatrixColorFilter(cm);
        }
    }
}