package com.github.lorentz83.alps.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

/**
 * OverlayBuilder is a helper to get functions to apply overlays to bitmaps.
 */
public class OverlayBuilder {
    private final static LogUtility log = new LogUtility(OverlayBuilder.class);
    private final static Paint black;

    static {
        black = new Paint();
        black.setColor(Color.BLACK);
    }

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

    private static class PaintTools {
        final Bitmap bmp;
        final Canvas c;

        PaintTools(Bitmap original) {
            bmp = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
            c = new Canvas(bmp);
            c.drawBitmap(original, 0, 0, null);
        }
    }

    private static Bitmap applyHorizontalStripes(Bitmap original, int p1, int p2) {
        PaintTools t = new PaintTools(original);

        for (int y  = p2 ; y < t.bmp.getHeight(); y = y + p1 + p2) {
            Rect rect = new Rect(0, y, original.getWidth(), y + p1);
            t.c.drawRect(rect, black);
        }
        return t.bmp;
    }

    private static Bitmap applyVerticalStripes(Bitmap original, int p1, int p2) {
        PaintTools t = new PaintTools(original);

        for (int x  = p2 ; x < t.bmp.getWidth(); x = x + p1 + p2) {
            Rect rect = new Rect(x, 0, x + p1, original.getHeight());
            t.c.drawRect(rect, black);
        }
        return t.bmp;
    }

    private static Bitmap applyRectangles(Bitmap original, int p1, int p2, boolean square) {
        PaintTools t = new PaintTools(original);

        int w = t.bmp.getWidth();
        int h = t.bmp.getHeight();

        boolean xOE = true;
        for (int x  = 0 ; x < w; ) {
            boolean yOE = true;
            int nextX = x + (xOE ? p1 : p2);
            for (int y  = 0 ; y < h; ) {
                int nextY = y + (yOE == square ? p1 : p2);
                Rect rect = new Rect(x, y, nextX, nextY);
                if ( xOE == yOE ) {
                    t.c.drawRect(rect, black);
                }
                y = nextY;
                yOE = ! yOE;
            }
            x = nextX;
            xOE = ! xOE;
        }
        return t.bmp;
    }

    private static Bitmap applyDiagonal(Bitmap original, int p1, int p2, boolean up) {
        PaintTools t = new PaintTools(original);

        int w = t.bmp.getWidth();
        int h = t.bmp.getHeight();

        Path diagonal = new Path();
        // (0, 0)(w, h)(w+p1, h)(p1, 0) translated right just outside the frame x = x-(w+p1).
        diagonal.moveTo(-(w+p1)+0, 0);
        diagonal.lineTo(-(w+p1)+w, h);
        diagonal.lineTo(-(w+p1)+w + p1, h);
        diagonal.lineTo(-(w+p1)+p1, 0);

        if (up) {
            Matrix flip = new Matrix();
            flip.setScale(1, -1, 0, h/(float)2);
            diagonal.transform(flip);
        }

        Matrix translate = new Matrix();
        translate.setTranslate(p1 + p2, 0);

        // iterate over the translations of the 1st origin is x == -(w+p1)
        for ( int x = -(w+p1) ; x < w ; x = x + p1 + p2 ) {
            t.c.drawPath(diagonal, black);
            diagonal.transform(translate);
        }

        return t.bmp;
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

    /**
     * Returns an overlayer to apply a diagonal pattern.
     *
     * @return a diagonal pattern overlayer.
     */
    public static OverlayBuilder.Overlayer diagonalDown() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyDiagonal(original, p1, p2, false);
            }

            @Override
            public String toString() {
                return "Diagonal down";
            }
        };
    }

    /**
     * Returns an overlayer to apply a diagonal pattern.
     *
     * @return a diagonal pattern overlayer.
     */
    public static OverlayBuilder.Overlayer diagonalUp() {
        return new OverlayBuilder.Overlayer() {
            @Override
            public Bitmap apply(Bitmap original, int p1, int p2) {
                return applyDiagonal(original, p1, p2, true);
            }

            @Override
            public String toString() {
                return "Diagonal up";
            }
        };
    }
}
