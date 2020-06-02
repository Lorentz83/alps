package com.github.lorentz83.alps.communication;

import com.github.lorentz83.alps.utils.LogUtility;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;


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

    public Protocol(InputStream in, OutputStream out) {
        _in = in;
        _out = out;
    }


    public final void readFully(byte b[], int off, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = _in.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    private void waitAck() throws IOException {
        // TODO we should add a timeout here.
        byte[] buf = new byte[5];

        readFully(buf, 0, 5);
        if (buf[0] != 'o') {
            throw new ProtocolException("protocol error: nack " + new String(buf));
        }
    }

    private void sendOnly(byte[] buf, int len) throws IOException {
        _out.write(buf, 0, len);
    }

    private void send(byte[] buf, int len) throws IOException {
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


    public void writePixels(int pos, Iterator<PixelColor> j) throws IOException {
        _buf[0] = PIXEL;
        // _buf[1] = len
        _buf[2] = (byte) pos;

        int i = 3;
        int num = 0;
        for (int avail = BUFFER_SIZE - 3; avail > 3 && j.hasNext(); avail -= 3) {
            PixelColor c = j.next();
            _buf[i++] = c.R;
            _buf[i++] = c.G;
            _buf[i++] = c.B;
            num++;
        }
        _buf[1] = (byte) i;

        sendOnly(_buf, i);
        if (j.hasNext()) {
            writePixels(pos + num, j);
        }
    }

    public void show() throws IOException {
        _buf[0] = SHOW;
        _buf[1] = 2;
        send(_buf, 2);
    }

    public void ready() throws IOException {
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

    public void off() throws IOException {
        _buf[0] = OFF;
        _buf[1] = 2;
        send(_buf, 2);
    }
}
