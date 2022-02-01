/**
 *  Copyright 2020 Lorenzo Bossi
 *
 *  This file is part of ALPS (Another Light Painting Stick).
 *
 *  ALPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ALPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ALPS.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.github.lorentz83.alps.communication;

import java.util.Objects;

/**
 * Defines the color of a single pixel.
 */
public class PixelColor {
    private byte red;
    private byte green;
    private byte blue;

    public PixelColor() {
        red = 0;
        green = 0;
        blue = 0;
    }

    /**
     * Initializes the color from the given argb non pre-multiplied encoding and the brightness.
     *
     * @param argb       the argb non pre-multiplied encoded color of the pixel.
     * @param brightness between 0 and 1.
     */
    public PixelColor(int argb, float brightness) {
        setColor(argb, brightness);
    }

    /**
     * Sets the color from the given argb non pre-multiplied encoding and the brightness.
     *
     * @param argb       the argb non pre-multiplied encoded color of the pixel.
     * @param brightness between 0 and 1.
     */
    public void setColor(int argb, float brightness) {
        if (brightness < 0 || brightness > 1) {
            throw new IllegalArgumentException("brightness value must in [0, 1]");
        }

        int alpha = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        red = (byte) Math.round((brightness * alpha) / 255.0 * r);
        green = (byte) Math.round((brightness * alpha) / 255.0 * g);
        blue = (byte) Math.round((brightness * alpha) / 255.0 * b);
    }
    /**
     * Sets the color from the given argb non pre-multiplied encoding.
     *
     * Same as setColor(argb, 1)
     *
     * @param argb       the argb non pre-multiplied encoded color of the pixel.
     */
    public void setColor(int argb) {
        setColor(argb, 1);
    }

    @Override
    public String toString() {
        return String.format("PixelColor(%d, %d, %d)", red, green, blue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PixelColor that = (PixelColor) o;
        return red == that.red &&
                green == that.green &&
                blue == that.blue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue);
    }

    /**
     * The red component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public byte getRed() {
        return red;
    }

    /**
     * The green component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public byte getGreen() {
        return green;
    }

    /**
     * The blue component of the color.
     * <p>
     * Valid values are between 0 and 255.
     */
    public byte getBlue() {
        return blue;
    }
}
