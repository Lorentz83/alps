package com.github.lorentz83.alps.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * OverlayBuilder is a helper to get functions to apply overlays to bitmaps.
 */
public class OverlayBuilder {
    private final static LogUtility log = new LogUtility(OverlayBuilder.class);

    /**
     * Overlayer is the basic interface to expose a named function to apply a an overlay to a bitmap.
     *
     * It can be used in an ArrayAdapter to populate a Spinner.
     */
    public interface Overlayer {
        /**
         * Applies the current overlay.
         * @param original the bitmap to overlay.
         * @param p1 1st parameter of the overlay (greater than 1).
         * @param p2 2nd parameter of the overlay (greater than 1).
         * @return the overlaid bitmap.
         */
        Bitmap apply(Bitmap original, int p1, int p2);

        /**
         * Returns the name of this overlay.
         * @return the name of this overlay.
         */
        String toString();
    }

    private static Bitmap applyHorizontalStripes(Bitmap original, int p1, int p2) {
        Bitmap bmp = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawBitmap(original, 0,0, null);
        Paint p = new Paint();
        p.setColor(Color.BLACK);

        for (int y  = p2 ; y < bmp.getHeight(); y = y + p1 + p2) {
            Rect rect = new Rect(0, y, original.getWidth(), y + p1);
            c.drawRect(rect, p);
        }
        return bmp;
    }

    private static Bitmap applyVerticalStripes(Bitmap original, int p1, int p2) {
        Bitmap bmp = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawBitmap(original, 0,0, null);
        Paint p = new Paint();
        p.setColor(Color.BLACK);

        for (int x  = p2 ; x < bmp.getWidth(); x = x + p1 + p2) {
            Rect rect = new Rect(x, 0, x + p1, original.getHeight());
            c.drawRect(rect, p);
        }
        return bmp;
    }

    private static Bitmap applyRectangles(Bitmap original, int p1, int p2, boolean square) {
        Bitmap bmp = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawBitmap(original, 0,0, null);
        Paint p = new Paint();
        p.setColor(Color.BLACK);

        boolean xOE = true;
        for (int x  = 0 ; x < bmp.getWidth(); ) {
            boolean yOE = true;
            int nextX = x + (xOE ? p1 : p2);
            for (int y  = 0 ; y < bmp.getHeight(); ) {
                int nextY = y + (yOE == square ? p1 : p2);
                Rect rect = new Rect(x, y, nextX, nextY);
                if ( xOE == yOE ) {
                    c.drawRect(rect, p);
                }
                y = nextY;
                yOE = ! yOE;
            }
            x = nextX;
            xOE = ! xOE;
        }
        return bmp;
    }

    /**
     * Returns a no-op overlay.
     * It simply returns the original bitmap. Useful to disable the overlay.
     * @return a no-op overlay.
     */
    public static OverlayBuilder.Overlayer noOverlay() {
        return new OverlayBuilder.Overlayer() {

            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return original;
            }

            @Override
            public String toString() {
                return "No overlay";
            }
        };
    }

    /**
     * Returns an overlayer to apply horizontal stripes.
     *
     * The 1st parameter defines the width of the black lines, the 2nd the gap between them.
     *
     * @return an horizontal stripes overlayer.
     */
    public static OverlayBuilder.Overlayer horizontalStripes() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyHorizontalStripes(original, p1, p2);
            }

            @Override
            public String toString() {
                return "Horizontal stripes";
            }
        };
    }

    /**
     * Returns an overlayer to apply vertical stripes.
     *
     * The 1st parameter defines the width of the black lines, the 2nd the gap between them.
     *
     * @return a vertical stripes overlayer.
     */
    public static OverlayBuilder.Overlayer verticalStripes() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyVerticalStripes(original, p1, p2);
            }

            @Override
            public String toString() {
                return "Vertical stripes";
            }
        };
    }

    /**
     * Returns an overlayer to apply a chessboard pattern.
     *
     * @return a chessboard pattern overlayer.
     */
    public static OverlayBuilder.Overlayer chessboard() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyRectangles(original, p1, p2, true);
            }

            @Override
            public String toString() {
                return "Chessboard";
            }
        };
    }

    /**
     * Returns an overlayer to apply a rectangle pattern.
     *
     * @return a rectangle pattern overlayer.
     */
    public static OverlayBuilder.Overlayer rectangles() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyRectangles(original, p1, p2, false);
            }

            @Override
            public String toString() {
                return "Rectangles";
            }
        };
    }
}
