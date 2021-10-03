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

import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.IntConsumer;

/**
 * Defines the communication protocol with the stick.
 */
public class Protocol {
    private final static LogUtility log = new LogUtility(Protocol.class);

    private InputStream _in;
    private OutputStream _out;

    private final byte OFF = 'o';
    private final byte READY = 'r';
    private final byte COLUMN = 'c';
    private final byte UPLOAD = 'u';
    private final byte RESHOW = 'w';
    private final byte SETTINGS = 's';

    private final byte[] _buf = new byte[255*3+2];

    /**
     * Initializes the protocol.
     * <p>
     * Note: at this point the communication stream is not initialized yet.
     * Call initializeConnection to set it up.
     */
    public Protocol() {
        _in = new NoInputStream();
        _out = new NoOutputStream();
    }

    /**
     * Sets up the protocol to connect to the specified bluetooth socket.
     *
     * @param socket the bluetooth socket
     * @throws IOException if the socket is not connected.
     */
    public void initializeConnection(@NonNull BluetoothSocket socket) throws IOException {
        // TODO it would be nice to remove this method, because it is the only android
        // dependency in this file.
        if (!socket.isConnected()) {
            throw new IOException("Bluetooth not connected");
        }
        _in = socket.getInputStream();
        _out = socket.getOutputStream();
    }

    /**
     * Sets up the protocol to connect to the specified streams.
     */
    public void initializeConnection(@NonNull InputStream in, @NonNull OutputStream out) {
        _in = in;
        _out = out;
    }


    /**
     * Blocks until len bytes are available and puts them in the buffer.
     *
     * @param buf the buffer to fill.
     * @param len how many bites to read.
     * @throws IOException  in case of IO error
     * @throws EOFException if the stream closes.
     */
    final void readFully(byte[] buf, int len) throws IOException {
        // TODO we should add a timeout here.
        if (len < 0)
            throw new IndexOutOfBoundsException();
        for (int bytesRead = 0; bytesRead < len; ) {
            int count = _in.read(buf, bytesRead, len - bytesRead);
            if (count < 0)
                throw new EOFException();
            bytesRead += count;
        }
    }

    private void waitAck() throws IOException {
        // TODO we should add a timeout here.
        byte[] buf = new byte[5];

        readFully(buf, 5);
        if (buf[0] != 'o') {
            // TODO at this point we should probably reset the bluetooth connection.
            _in.skip(_in.available());
            throw new ProtocolException("protocol error: nack " + new String(buf));
        }
    }

    private void sendOnly(byte[] buf, int len) throws IOException {
        _out.write(buf, 0, len);
        _out.flush();
    }

    private void sendAndWaitForAck(byte[] buf, int len) throws IOException {
        sendOnly(buf, len);
        waitAck();
    }

    /**
     * Sends the while column to the stick and shows it.
     *
     * It is 10% faster than writePixels on big images.
     *
     * @param pixels the color of the pixels.
     * @throws IOException in case of error.
     */
    public void writeColumn(int[] pixels, float brightness) throws IOException {
        int len = (pixels.length * 3) + 2;
        _buf[0] = COLUMN;

        if (pixels.length> 255) {
            throw new ProtocolException("cannot send more than 255 pixels with writeColumn");
        }

        _buf[1] = (byte) pixels.length;

        int i = 2;
        PixelColor c = new PixelColor();
        for (int rawColor : pixels) {
            c.setColor(rawColor, brightness);
            _buf[i++] = c.getRed();
            _buf[i++] = c.getGreen();
            _buf[i++] = c.getBlue();
        }

        sendAndWaitForAck(_buf, len);
    }

    /**
     * Uploads an image to the internal stick memory.
     *
     * @param w the width of the image.
     * @param h the height of the image.
     * @param pixels the color of the pixels, ordered in columns.
     * @param callback if non null, this predicate is called with the number of the last column sent.
     * @throws IOException in case of error.
     */
    public void uploadImage(int w, int h, int[] pixels, IntConsumer callback) throws IOException, InterruptedException {
        if ( h > 255 ) {
            throw new ProtocolException("uploaded images cannot be higher than 255 pixels");
        }
        if ( w > 0xffff ) { // 255 *255
            throw new ProtocolException("uploaded images cannot be wider than 65025 pixels");
        }
        if (pixels.length != w*h) {
            throw new IllegalArgumentException("number of pixels doesn't match the image size");
        }

        byte wHi = (byte)((w >>> 8) & 0xff);
        byte wLow =  (byte)(w & 0xff);

        _buf[0] = UPLOAD;
        _buf[1] = (byte) h;
        _buf[2] = wHi;
        _buf[3] = wLow;
        sendAndWaitForAck(_buf, 4);

        log.i("uploading image %d x %d. w = (%x, %x)", w, h, wHi, wLow);


        int i = 0;
        int col = 0;
        PixelColor c = new PixelColor();
        for (int rawColor : pixels) {
            c.setColor(rawColor);
            _buf[i++] = c.getRed();
            _buf[i++] = c.getGreen();
            _buf[i++] = c.getBlue();

            if ( (i / 3) % h == 0 ) {
                col++;
                sendAndWaitForAck(_buf, i);
                i = 0;
                if (callback != null) {
                    callback.accept(col);
                }
            }
        }
        log.i("upload completed");
    }

    /**
     * Waits for the stick button to be pushed.
     *
     * @throws IOException in case of error.
     */
    public void ready() throws IOException {
        // TODO add a timeout, or a way to exit this function.
        _buf[0] = READY;
        _buf[1] = 2;
        for (; ; ) {
            sendOnly(_buf, 2);
            try {
                waitAck();
            } catch (IOException ex) {
                log.i("wait ack: %s", ex.getMessage());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                throw new IOException("error waiting", ex);
            }
        }
    }

    /**
     * Turns immediately off the stick.
     *
     * @throws IOException
     */
    public void off() throws IOException {
        _buf[0] = OFF;
        _buf[1] = 2;
        sendAndWaitForAck(_buf, 2);
    }

    /**
     * Sets the runtime settings for uploaded images.
     *
     * @param delayMs the delay between columns.
     * @param brightness the brightness (0 to 255).
     * @param loop if the image should go in loop.
     *
     * @throws IOException
     */
    public void replaySettings(int delayMs, int brightness, boolean loop) throws IOException {
        // TODO it would be nice to handle the brightness in the same way at this level.

        // TODO better error checking on boundaries.
        _buf[0] = SETTINGS;
        _buf[1] = (byte) (delayMs/10);
        _buf[2] = (byte) brightness;
        _buf[3] = (byte) (loop ? 1: 0);

        sendAndWaitForAck(_buf, 4);
    }


}

class NoInputStream extends InputStream {
    @Override
    public int read() throws IOException {
        throw new IOException("Connection not initialized");
    }
}

class NoOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
        throw new IOException("Connection not initialized");
    }
}