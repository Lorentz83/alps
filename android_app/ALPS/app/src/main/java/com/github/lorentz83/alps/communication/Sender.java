package com.github.lorentz83.alps.communication;

import android.graphics.Bitmap;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Sender implements an abstraction around Protocol to send images to the stick.
 * <p>
 * It works on a separate thread to don't block the UI.
 */
public class Sender {
    private final static LogUtility log = new LogUtility(Sender.class);

    private internalSender _sender;
    private final SenderCallbacks _callbacks;
    private final Protocol _p;

    /**
     * Initializes the class.
     *
     * @param p         the protocol to use.
     * @param callbacks the callbacks to call while the bitmap is sent.
     */
    public Sender(Protocol p, SenderCallbacks callbacks) {
        log.i("Sender.New");
        _p = p;
        _callbacks = callbacks;
        _sender = new internalSender(_p, _callbacks);
        _sender.start();
    }

    /**
     * Sends the specified bitmap to the stick.
     *
     * @param bitmap the image to send.
     */
    public void sendBitmap(Bitmap bitmap) {
        _sender.sendBitmap(bitmap);
    }

    /**
     * Stops sending the image.
     */
    public synchronized void stop() {
        log.i("Sender.stop");
        _sender.interrupt();
        _sender = new internalSender(_p, _callbacks);
        _sender.start();
    }

    /**
     * Kills the sender and all the running threads.
     * <p>
     * After calling this method, the sender enters in an invalid state and shouldn't be used anymore.
     */
    public synchronized void kill() {
        log.i("Sender.kill");
        _sender.interrupt();
        _sender = null;
    }

    /**
     * Sets the delay to wait between different columns of the images are sent.
     *
     * @param millis the delay in milliseconds.
     */
    public void setExtraDelay(int millis) {
        _sender.setExtraDelay(millis);
    }

    /**
     * Sets if the image should be sent in an infinite loop.
     * <p>
     * If set to true, use stop() to stop the sender.
     *
     * @param loop if the image should be in loop.
     */
    public void setLoop(boolean loop) {
        _sender.setLoop(loop);
    }

    /**
     * Adjust the brightness of the image to send.
     *
     * @param brightness a number between 0 and 1.
     */
    public void setBrightness(float brightness) {
        if (brightness < 0 || brightness > 1) {
            throw new IllegalArgumentException("brightness must be in [0, 1]");
        }
        _sender.setBrightness(brightness);
    }
}

/**
 * internalSender hides the Thread and the synchronization to the caller of Sender.
 */
class internalSender extends Thread {
    private final static LogUtility log = new LogUtility(Sender.class);

    private SenderCallbacks _callbacks;
    private Bitmap _bitmap;
    private Protocol _p;
    private int _delay = 0;
    private boolean _loop = false;
    private float _brightness;

    public internalSender(Protocol p, SenderCallbacks callbacks) {
        _p = p;
        _callbacks = callbacks;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Get all the parameters in a syncronized way.

                Bitmap bmp = waitForBitmap();
                log.i("got bitmap");

                int delay = 0;
                boolean loop = false;
                float brightness = 100;
                synchronized (this) {
                    delay = _delay;
                    loop = _loop;
                    brightness = _brightness;
                }

                _callbacks.start();

                sendBitmap(_p, _callbacks, bmp, delay, loop, brightness);
                _p.off(); // Turn the stick off once we are done.

                _callbacks.done();
            }
        } catch (InterruptedException e) {
            log.i("Sender interrupted: %s", e.getMessage());
            try {
                _p.off();
            } catch (IOException ex) {
                log.w("Protocol error while turning off stick", e);
            }
        } catch (IOException e) {
            log.w("Protocol error", e);
        } finally {
            _callbacks.done();
        }
    }

    // To avoid syncronization problems, this function is static and cannot access any field.
    private static void sendBitmap(Protocol _p, SenderCallbacks _callbacks, Bitmap bmp, int delay, boolean loop, float brightness) throws IOException, InterruptedException {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        ArrayList<PixelColor> col = new ArrayList<>();
        do {
            for (int x = 0; x < w; x++) {
                col.clear();
                for (int y = h - 1; y >= 0; y--) {
                    PixelColor color = new PixelColor(bmp.getPixel(x, y), brightness);
                    col.add(color);
                }
                _p.writePixels(0, col.iterator());
                _p.show();
                sleep(delay); // isInterrupted is checked already here.

                // TODO: send the progress less frequently to save (very little) resources.
                _callbacks.progress(Math.round((float) (x + 1) / w * 100));
            }
        } while (loop);
    }

    /**
     * Blocks the current thread until a new bitmap is passed calling the sendBitmap(Bitmap) method.
     *
     * @return the bitmap to send.
     * @throws InterruptedException if the thread is interrupted.
     */
    private synchronized Bitmap waitForBitmap() throws InterruptedException {
        while (_bitmap == null) {
            wait();
        }
        Bitmap bmp = _bitmap;
        _bitmap = null;
        return bmp;
    }

    /**
     * Sends a new bitmap.
     *
     * @param bitmap the image to send.
     */
    public synchronized void sendBitmap(Bitmap bitmap) {
        this._bitmap = bitmap;
        notifyAll();
    }

    public synchronized void setExtraDelay(int millis) {
        _delay = millis;
    }

    public synchronized void setLoop(boolean loop) {
        _loop = loop;
    }

    public void setBrightness(float brightness) {
        _brightness = brightness;
    }
}
