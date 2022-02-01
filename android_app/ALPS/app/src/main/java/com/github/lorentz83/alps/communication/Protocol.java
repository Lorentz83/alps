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
import java.util.zip.CRC32;

/**
 * Defines the communication protocol with the stick.
 */
public class Protocol {
    private final static LogUtility log = new LogUtility(Protocol.class);

    private InputStream _in;
    private OutputStream _out;

    private final byte INFO = '?';
    private final byte OFF = 'o';
    private final byte NEW_IMAGE = 'n';
    private final byte CONTINUE_IMAGE = 'c';

    CRC32 _crc = new CRC32();
    private byte[] _buf = new byte[255*3+2];

    private int _maxPixels = 144;
    private int _maxCols = 1;

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
        initializeConnection(socket.getInputStream(), socket.getOutputStream());
    }

    /**
     * Sets up the protocol to connect to the specified streams.
     */
    public void initializeConnection(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        _in = in;
        _out = out;

        info();
    }

    private void info() throws IOException {
        _buf[0] = INFO;
        _out.write(_buf, 0, 1);
        _out.flush();

        readFully(_buf, 5);
        if ( _buf[0] != '!' ) {
            throw new ProtocolException("be sure the stick and the app are on the same version");
        }
        // _buf[1] we don't support any extension
        // _buf[2] is unused
        _maxPixels = _buf[3];
        _maxCols = _buf[4];
        int requiredLen = _maxCols + _maxPixels * 3 +10; // extra space for headers.
        if ( _buf.length < requiredLen) {
            _buf = new byte[requiredLen];
        }
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

        try {
            readFully(buf, 5);
            if (buf[0] != 'o') {
                // TODO at this point we should probably reset the bluetooth connection.
                _in.skip(_in.available());
                throw new ProtocolException("protocol error: " + new String(buf));
            }

            long got = 0;
            for (int i = 4; i >= 1; i--) {
                long b = buf[i] & 0xFF; // This is required otherwise it casts as signed byte.
                got = (got << 8) + b;
            }

            if ( got != _crc.getValue() ) {
                throw new ProtocolException(String.format("transmission corrupted, got 0X%x want 0X%x", got, _crc.getValue()));
            }
        } finally {
            _crc.reset();
        }
    }

    private void sendOnly(byte[] buf, int len) throws IOException {
        _out.write(buf, 0, len);
        _out.flush();
    }

    private void sendAndWaitForAck(byte[] buf, int len) throws IOException {
        sendOnly(buf, len);
        _crc.update(buf, 0, len);
        waitAck();
    }

    /**
     * Turns immediately off the stick.
     *
     * @throws IOException
     */
    public void off() throws IOException {
        _buf[0] = OFF;
        for ( int i = 1 ; i <= 4 ; i++ ) {
            _buf[i] = (byte) (Math.random() * 255);
        }
        sendAndWaitForAck(_buf, 5);
    }

    /**
     * Shows the image.
     *
     * @param w the width of the image.
     * @param h the height of the image.
     * @param pixels the color of the pixels, ordered in columns.
     * @param callback if non null, this predicate is called with the number of the last column sent.
     * @throws IOException in case of error.
     */
    public void showImage(int w, int h, int[] pixels, int sleep, IntConsumer callback) throws IOException, InterruptedException {
        if ( h > 255 ) { // TODO we should check the pixel length here.
            throw new ProtocolException("uploaded images cannot be higher than 255 pixels");
        }
        if (pixels.length != w*h) {
            throw new IllegalArgumentException("number of pixels doesn't match the image size");
        }

        byte wHi = (byte)((w >>> 8) & 0xff);
        byte wLow =  (byte)(w & 0xff);

        _buf[0] = NEW_IMAGE;
        _buf[1] = (byte) h;
        _buf[2] = 0; // TODO add delay here.
        _buf[3] = 1; // TODO use the max num of cols we can send.
        sendOnly(_buf, 4);


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
     * Uploads an image to the internal stick memory.
     *
     * @param w the width of the image.
     * @param h the height of the image.
     * @param pixels the color of the pixels, ordered in columns.
     * @param callback if non null, this predicate is called with the number of the last column sent.
     * @throws IOException in case of error.
     */
    public void uploadImage(int w, int h, int[] pixels, IntConsumer callback) throws IOException, InterruptedException {
        throw new ProtocolException("not supported");
    }
    public void replaySettings(int delay, int brightness, boolean loop) throws ProtocolException {
        throw new ProtocolException("not supported");
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