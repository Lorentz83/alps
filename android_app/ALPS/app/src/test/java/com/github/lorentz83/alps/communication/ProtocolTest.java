package com.github.lorentz83.alps.communication;

import org.junit.Assert;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Protocol_IntegrationTest {

    static final String testingDir = "../../../testing/";

    @org.junit.jupiter.api.BeforeAll
    static void makeTestHelper() throws IOException, InterruptedException {
        Process make = new ProcessBuilder("make", "all")
                .directory(new File(testingDir))
                .inheritIO()
                .start();

        int exit = make.waitFor();
        assertEquals(0, exit);
    }

    @Timeout(value = 2, unit = SECONDS)
    //@Test
    @RepeatedTest(30)
    void off() throws IOException, InterruptedException {
        Process testing = new ProcessBuilder("./protocol_tester")
                .directory(new File(testingDir))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            Protocol p = new Protocol();
            p.initializeConnection(testing.getInputStream(), testing.getOutputStream());

            p.off();
        } finally {
            testing.destroy();
        }
    }

    @Timeout(value = 2, unit = SECONDS)
    @Test
    void showImage() throws IOException, InterruptedException {
        Process testing = new ProcessBuilder(
                "./protocol_tester",
                //"--debug_to_stderr",
                "--pixels_to_stderr")
                .directory(new File(testingDir))
                .start();
        try {
            Protocol p = new Protocol();
            p.initializeConnection(testing.getInputStream(), testing.getOutputStream());
            StreamReader data = new StreamReader(testing.getErrorStream(), 10000);

            int w = 3;
            int h = 2;
            int []pixels = new int[w*h];
            int color = 0xFF0000;
            for ( int x = 0 ; x < w ; x++ ) {
                for ( int y = 0 ; y < h ; y ++ ) {
                    pixels[x*h + y] = color | 0XFF000000; // Add alpha channel.
                }
                color = color >>> 8;
            }

            p.showImage(w, h, pixels, .5F, 0, (i)-> {});

            Thread.sleep(50); // Give some time to flush stderr.
            testing.destroy(); // Let's close the error stream.

            byte s = (byte) 0x80; // scaled by brightness.
            byte []want = new byte[]{
                    s, 0, 0, s, 0, 0, // 1st col red;
                    'S', 'H', 'O', 'W',
                    0, s, 0, 0, s, 0, // 2nd col green;
                    'S', 'H', 'O', 'W',
                    0, 0, s, 0, 0, s, // 3rd col blue;
                    'S', 'H', 'O', 'W',
                    'O', 'F', 'F',
            };
            byte[] got = data.getAll();
            printByteArray("got", got);
            Assert.assertArrayEquals(want, got);

        } finally {
            testing.destroy();
        }
    }

    private void printByteArray(String prefix, byte[] arr) {
        System.out.printf("%s = [", prefix);
        for ( byte b : arr ){
            if ( (b >= 'a' && b <='z') || (b >= 'A' && b <='Z') || (b >= '0' && b <='9') ) {
                System.out.printf("%c, ", b);
            } else {
                System.out.printf("0x%X, ", b);
            }
        }
        System.out.print("]\n");
    }
/*

    @Test
    @Timeout(value = 2, unit = SECONDS)
    void writeColumn_missingBytes() throws IOException, InterruptedException {
        // TODO this shouldn't throw but retry.
        Process testing = new ProcessBuilder("./protocol_tester")
                .directory(new File(testingDir))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            Protocol p = new Protocol();
            JammedOutputStream out = new JammedOutputStream(testing.getOutputStream());
            out.skipRange(10, 12);
            p.initializeConnection(testing.getInputStream(), out);

            int[] pixels = new int[20];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = i << 8 | 0xFF000000; // Fully opaque green.
            }

            assertThrows(ProtocolException.class, () -> {
                p.writeColumn(pixels, 1f);
            });
        } finally {
            testing.destroy();
        }
    }

    @Test
    @Timeout(value = 2, unit = SECONDS)
    @Disabled("TODO")
    void writeColumn_recoversAfterError() throws IOException, InterruptedException {
        // TODO this shouldn't throw but retry.
        Process testing = new ProcessBuilder("./protocol_tester")
                .directory(new File(testingDir))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            Protocol p = new Protocol();
            JammedOutputStream out = new JammedOutputStream(testing.getOutputStream());
            out.skipRange(10, 12);
            p.initializeConnection(testing.getInputStream(), out);

            int[] pixels = new int[20];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = i << 8 | 0xFF000000; // Fully opaque green.
            }

            assertThrows(ProtocolException.class, () -> {
                p.writeColumn( new int[200], 1f);
            });
            p.writeColumn(pixels, 1f);

        } finally {
            testing.destroy();
        }
    }
    */
}

class StreamReader {
    private final InputStream _inner;
    private final byte[] _buf;
    private final Thread _t;
    private IOException _err;
    private int _read;

    public StreamReader(InputStream in, int bufSize) {
        _inner = in;
        _buf = new byte[bufSize];
        _read = 0;

        _t = new Thread(()->{
            try {
                while (true) {
                    synchronized (_buf) {
                        int b = _inner.read();
                        if (b == -1) { // EOF
                            return;
                        }
                        _buf[_read++] = (byte) b;
                    }
                }
            } catch (IOException e) {
                _err = e;
            }
        });
        _t.start();
    }

    public byte[] getAll() throws IOException {
        if (_err != null) {
            throw _err;
        }
        synchronized (_buf) {
            return Arrays.copyOf(_buf, _read);
        }
    }
}

class JammedOutputStream extends OutputStream {
    private final OutputStream _inner;
    private final Set<Integer> _skipBytes = new HashSet<>();
    private int _actualBytesSent, _bytesSent;

    JammedOutputStream(OutputStream out) {
        _inner = out;
    }

    public void skip(int b) {
        _skipBytes.add(b);
    }

    public void skipRange(int from, int to) {
        for (; from < to; from++)
            _skipBytes.add(from);
    }

    @Override
    public void write(int b) throws IOException {
        _bytesSent++;
        if (_skipBytes.contains(_bytesSent)) {
            System.out.println("skipping byte");
            return;
        }
        _actualBytesSent++;
        _inner.write(b);
    }

    @Override
    public void flush() throws IOException {
        _inner.flush();
    }

    public int getActualBytesSent() {
        return _actualBytesSent;
    }

    public int getBytesSent() {
        return _bytesSent;
    }
}