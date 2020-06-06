package com.github.lorentz83.alps.communication;

import android.graphics.Color;

import java.util.Objects;

/**
 * Defines the color of a single pixel.
 */
public class PixelColor {
    /**
     * The red component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public final byte R;
    /**
     * The green component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public final byte G;
    /**
     * The blue component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public final byte B;

    /**
     * Initializes the color from the given argb non pre-multiplied encoding and the brightness.
     *
     * @param argb       the argb non pre-multiplied encoded color of the pixel.
     * @param brightness between 0 and 1.
     */
    public PixelColor(int argb, float brightness) {
        if (brightness < 0 || brightness > 1) {
            throw new IllegalArgumentException("brightness value must in [0, 1]");
        }

        int alpha = Color.alpha(argb);
        int r = Color.red(argb);
        int g = Color.green(argb);
        int b = Color.blue(argb);

        R = (byte) Math.round((brightness * alpha) / 255.0 * r);
        G = (byte) Math.round((brightness * alpha) / 255.0 * g);
        B = (byte) Math.round((brightness * alpha) / 255.0 * b);
    }

    @Override
    public String toString() {
        return String.format("PixelColor(%d, %d, %d)", R, G, B);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PixelColor that = (PixelColor) o;
        return R == that.R &&
                G == that.G &&
                B == that.B;
    }

    @Override
    public int hashCode() {
        return Objects.hash(R, G, B);
    }
}
