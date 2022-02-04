/**
 *  Copyright 2020-2021 Lorenzo Bossi
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

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;

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
     * Uploads (without showing) the specified bitmap to the stick.
     *
     * @param bitmap the image to send.
     */
    public void uploadBitmap(Bitmap bitmap) {
        _sender.uploadBitmap(bitmap);
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
    private boolean _upload;

    public internalSender(Protocol p, SenderCallbacks callbacks) {
        _p = p;
        _callbacks = callbacks;
        setName("ProtocolSenderThread"); // Name of the thread for debugging purposes.
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Get all the parameters in a synchronized way.

                Bitmap bmp = waitForBitmap();
                log.i("got bitmap");

                int delay = 0;
                boolean loop = false;
                float brightness = 100;
                boolean upload = false;
                synchronized (this) {
                    delay = _delay;
                    loop = _loop;
                    brightness = _brightness;
                    upload = _upload;
                }

                _callbacks.start();

                if (upload) {
                    log.i("uploading bitmap");
                    uploadBitmap(_p, _callbacks, bmp);
                } else {
                    log.i("sending bitmap");
                    sendBitmap(_p, _callbacks, bmp, delay, loop, brightness);
                }

                _callbacks.done();
            }
        } catch (InterruptedException e) {
            log.i("Sender interrupted: %s", e.getMessage());
            // no callback.onError here, this happens every time the user stops the sending.
            try {
                _p.off();
            } catch (IOException ex) {
                log.w("Protocol error while turning off stick", e);
            }
        } catch (IOException e) {
            _callbacks.onError(e);
            log.w("Protocol error", e);
        } finally {
            _callbacks.done();
        }
    }

    // To avoid synchronization problems, this function is static and cannot access any field.
    private static void sendBitmap(Protocol _p, SenderCallbacks _callbacks, Bitmap bmp, int delay, boolean loop, float brightness) throws IOException, InterruptedException {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // getPixels returns row by row from the top.
        // rotating 90 we get the columns.
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);

        int []pixels = new int[w*h];
        bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());

        do {
            _p.showImage(w, h, pixels, brightness, delay, (int col) -> {
                _callbacks.progress(Math.round((float) col / w * 100));
            });
        } while (loop);
    }

    // To avoid synchronization problems, this function is static and cannot access any field.
    private static void uploadBitmap(Protocol _p, SenderCallbacks _callbacks, Bitmap bmp) throws IOException, InterruptedException {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // getPixels returns row by row from the top.
        // rotating 90 we get the columns.
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);

        int []pixels = new int[w*h];
        bmp.getPixels(pixels, 0, h, 0, 0, h, w); // h and w are flipped because we rotated 90 deg.

        _p.uploadImage(w, h, pixels, (int col) -> _callbacks.progress(Math.round((float) col / w * 100)));
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
        this._upload = false;
        notifyAll();
    }

    /**
     * Uploads a new bitmap.
     *
     * @param bitmap the image to send.
     */
    public synchronized void uploadBitmap(Bitmap bitmap) {
        this._bitmap = bitmap;
        this._upload = true;
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
