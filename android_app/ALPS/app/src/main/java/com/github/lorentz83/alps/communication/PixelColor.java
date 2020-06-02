package com.github.lorentz83.alps.communication;

import android.graphics.Color;

public class PixelColor {
    public final byte R, G, B;

    public PixelColor(byte r, byte g, byte b) {
        R = r;
        G = g;
        B = b;
    }

    public PixelColor(int argb) {
        int alpha = Color.alpha(argb);
        int r = Color.red(argb);
        int g = Color.green(argb);
        int b = Color.blue(argb);

        R = (byte)Math.round(alpha/255.0 * r);
        G = (byte)Math.round(alpha/255.0 * g);
        B = (byte)Math.round(alpha/255.0 * b);
    }

    public PixelColor(int r, int g, int b) {
        this((byte) r, (byte) g, (byte) b);
    }
}
