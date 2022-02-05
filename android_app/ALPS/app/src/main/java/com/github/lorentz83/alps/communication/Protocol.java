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

import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

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
    private InputStream _in;
    private OutputStream _out;

    private static final byte INFO = '?';
    private static final byte OFF = 'o';
    private static final byte NEW_IMAGE = 'n';
    private static final byte CONTINUE_IMAGE = 'c';

    private final CRC32 _crc = new CRC32();
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

        try {
            info();
        } catch (IOException e) {
            // In case of protocol negotiation error, reset the streams.
            _in = new NoInputStream();
            _out = new NoOutputStream();
            throw e;
        }
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
        _maxPixels = _buf[3] & 0xFF; // Otherwise it considers buf as a signed byte.
        _maxCols = _buf[4] & 0xFF;
        int requiredLen = _maxCols * _maxPixels * 3 + 10; // extra space for headers.

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
     * Shows the image on the stick.
     *
     * @param w the width of the image.
     * @param h the height of the image.
     * @param pixels the color of the pixels in ARGB format, ordered in columns.
     * @param brightness between 0 (totally off) and 1 (full brightness).
     * @param sleep between columns, in ms between 0 and 255.
     * @param loop if the image must be repeated in loop (if true this function never returns, send an interrupt to the thread to block it with an InterruptedException)
     * @param callback if non null, this predicate is called with the number of the last column sent.
     * @throws IOException in case of error.
     * @throws IllegalArgumentException if the number of pixels is not aligned with the image size, or sleep is out of bounds.
     * @throws InterruptedException if the thread gets interrupted.
     */
    public void showImage(int w, int h, int[] pixels, float brightness, int sleep, boolean loop, IntConsumer callback) throws IOException, InterruptedException {
        if ( h > _maxPixels ) {
            throw new ProtocolException("the stick has only " + _maxPixels + " pixels");
        }
        if (pixels.length != w*h) {
            throw new IllegalArgumentException("number of pixels doesn't match the image size");
        }
        if ( sleep > 255 || sleep < 0 ) {
            throw new IllegalArgumentException("sleep must be between 0 and 255");
        }
        if ( brightness > 1 || brightness < 0 ) {
            throw new IllegalArgumentException("brightness must be between 0 and 1");
        }

        do {

            int idx = 0;
            int col = Math.min(_maxCols, w);

            _buf[idx++] = NEW_IMAGE;
            _buf[idx++] = (byte) h;
            _buf[idx++] = (byte) sleep;
            _buf[idx++] = (byte) col;

            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int argb = pixels[x * h + y];

                    int alpha = argb >>> 24;
                    int red = (argb >> 16) & 0xFF;
                    int green = (argb >> 8) & 0xFF;
                    int blue = argb & 0xFF;

                    _buf[idx++] = (byte) Math.round((brightness * alpha) / 255.0 * red);
                    _buf[idx++] = (byte) Math.round((brightness * alpha) / 255.0 * green);
                    _buf[idx++] = (byte) Math.round((brightness * alpha) / 255.0 * blue);
                }
                if (--col == 0) {
                    // Send the data.
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                    sendAndWaitForAck(_buf, idx);
                    // Reset counters.
                    idx = 0;
                    int colsRemaining = w - x - 1; // We just sent the col "x".
                    col = Math.min(_maxCols, colsRemaining);
                    // Prepare new message.
                    // NOTE: it may be useless because we just sent the last col.
                    // But in case we sent everything with the 1st message we still need to send the
                    // terminator.
                    _buf[idx++] = CONTINUE_IMAGE;
                    _buf[idx++] = (byte) ((col == colsRemaining && ! loop) ? 1 : 0); // lastBatchOfCols
                    _buf[idx++] = (byte) col; // Num cols to send.
                }
                if (callback != null) {
                    callback.accept(x + 1);
                }
            }
            if (w <= _maxCols) {
                sendAndWaitForAck(_buf, idx);
            }
        } while (loop);
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