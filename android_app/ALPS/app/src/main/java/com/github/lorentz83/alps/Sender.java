package com.github.lorentz83.alps;

import android.graphics.Bitmap;

import com.github.lorentz83.alps.communication.PixelColor;
import com.github.lorentz83.alps.communication.Protocol;
import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.util.ArrayList;

public class Sender extends Thread {
    private final static LogUtility log = new LogUtility(Sender.class);

    private Bitmap _bitmap;
    private Protocol _p;

    public Sender(Protocol p) {
        _p = p;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Bitmap bmp = waitForBitmap();
                log.i("got bitmap");

                int w = bmp.getWidth();
                int h = bmp.getHeight();

                ArrayList<PixelColor> col = new ArrayList<>();
                for (int x = 0; x<w; x++) {
                    col.clear();
                    for (int y = h-1; y >=0; y-- ) {
                        PixelColor color = new PixelColor(bmp.getPixel(x, y));
                        col.add(color);
                    }
                    if (isInterrupted()) {
                        throw new InterruptedException("interrupt() called");
                    }
                    _p.writePixels(0, col.iterator());
                    _p.show();
                }

                _p.off();
                _p.show();
            }
        } catch (InterruptedException e) {
            log.i("Sender interrupted: %s", e.getMessage());
            try {
                _p.off();
                _p.show();
            } catch (IOException ex) {
                log.w("Protocol error while turning off stick", e);
            }
        } catch (IOException e) {
            log.w("Protocol error", e);
        }
    }

    private synchronized Bitmap waitForBitmap() throws InterruptedException {
        while (_bitmap == null) {
            wait();
        }
        Bitmap bmp = _bitmap;
        _bitmap = null;
        return bmp;
    }

    public synchronized void sendBitmap(Bitmap bitmap) {
        this._bitmap = bitmap;
        notifyAll();
    }
}
