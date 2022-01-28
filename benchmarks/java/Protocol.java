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

    /**
     * Sends the while column to the stick and shows it.
     *
     * It is 10% faster than writePixels on big images.
     *
     * @param pixels the color of the pixels.
     * @throws IOException in case of error.
     */
    public void writeColumn(int []pixels, float brightness, int num) throws IOException {
        int i = 0;
        PixelColor c = new PixelColor();
int n = 0;
        for (int px : pixels) {
            n++;
            if ((n+num) %144 ==0)
                c.setColor(0, brightness);
            else
                c.setColor(px, brightness);


            _buf[i++] = c.getRed() == '\n' ? '\n'+1 : c.getRed();
            _buf[i++] = c.getGreen() == '\n' ? '\n'+1 : c.getGreen();
            _buf[i++] = c.getBlue() == '\n' ? '\n'+1 : c.getBlue();
//
//            _buf[i++] = 0;
//            _buf[i++] = (byte)((n+num)%144 == 0 ?  3 : 0);
//            _buf[i++] = 0;
        }
        _buf[i++] = '\n';

        _out.write(_buf, 0, i);

        CRC32 crc = new CRC32();
        crc.update(_buf, 0, i);
        //log.i("sent " +i );
        readFully(_buf, 8);
        //log.i("got ack ");
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
        throw new ProtocolException("not implemented");
    }

    /**
     * Turns immediately off the stick.
     *
     * @throws IOException
     */
    public void off() throws IOException {
//        throw new ProtocolException("not implemented");
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
        throw new ProtocolException("not implemented");
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