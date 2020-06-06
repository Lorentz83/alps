package com.github.lorentz83.alps.communication;

import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/**
 * Defines the communication protocol with the stick.
 */
public class Protocol {
    private final static LogUtility log = new LogUtility(Protocol.class);

    private InputStream _in;
    private OutputStream _out;

    private final byte OFF = 'o';
    private final byte SHOW = 's';
    private final byte PIXEL = 'p';
    private final byte READY = 'r';

    private static final int BUFFER_SIZE = 64;
    private final byte[] _buf = new byte[BUFFER_SIZE];

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
     * Blocks until len bytes are available and puts them in the buffer.
     *
     * @param buf the buffer to fill.
     * @param len how many bites to read.
     * @throws IOException  in case of IO error
     * @throws EOFException if the stream closes.
     */
    final void readFully(byte buf[], int len) throws IOException {
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
            throw new ProtocolException("protocol error: nack " + new String(buf));
        }
    }

    private void sendOnly(byte[] buf, int len) throws IOException {
        _out.write(buf, 0, len);
    }

    private void sendAndWaitForAck(byte[] buf, int len) throws IOException {
        for (int i = 1; ; i++) {
            try {
                sendOnly(buf, len);
                waitAck();
                return;
            } catch (ProtocolException ex) {
                if (i <= 3) {
                    log.w("retry %d on %v", i, ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }
    }

    /**
     * Sends the pixels to the stick.
     * <p>
     * To show them, call show().
     *
     * @param pos    the position of the 1st pixel to set.
     * @param pixels the color of the pixels.
     * @throws IOException in case of error.
     */
    public void writePixels(int pos, Iterator<PixelColor> pixels) throws IOException {
        _buf[0] = PIXEL;
        // _buf[1] = len // will be filled later.
        _buf[2] = (byte) pos;

        int i = 3;
        int num = 0;
        for (int avail = BUFFER_SIZE - 3; avail > 3 && pixels.hasNext(); avail -= 3) {
            PixelColor c = pixels.next();
            _buf[i++] = c.R;
            _buf[i++] = c.G;
            _buf[i++] = c.B;
            num++;
        }
        _buf[1] = (byte) i;

        sendOnly(_buf, i);
        if (pixels.hasNext()) {
            writePixels(pos + num, pixels);
        }
    }

    /**
     * Sends the command to show the pixels, as set by writePixels().
     *
     * @throws IOException in case of error.
     */
    public void show() throws IOException {
        _buf[0] = SHOW;
        _buf[1] = 2;
        sendAndWaitForAck(_buf, 2);
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