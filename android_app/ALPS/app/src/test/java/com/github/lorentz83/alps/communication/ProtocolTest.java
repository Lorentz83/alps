package com.github.lorentz83.alps.communication;

import org.junit.Assert;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @org.junit.jupiter.api.BeforeAll
    static void makeTestHelper() throws IOException, InterruptedException {
        assertEquals(0, Tester.make(),  "Cannot make tester");
    }

    @Timeout(value = 2, unit = SECONDS)
    //@Test
    @RepeatedTest(30)
    void off() throws IOException {
        try (Tester tester = new Tester("tester_1")) {
            Protocol p = new Protocol();
            p.initializeConnection(tester.getInputStream(), tester.getOutputStream());

            p.off();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tester_1", "tester_3"})
    public void showImage(String testerCmd) throws IOException, InterruptedException {
        try (Tester tester = new Tester(testerCmd,
                //Tester.Flags.DEBUG_TO_STDERR,
                Tester.Flags.PIXELS_TO_STDERR)){
            Protocol p = new Protocol();
            p.initializeConnection(tester.getInputStream(), tester.getOutputStream());

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

            p.showImage(w, h, pixels, .5F, 0,false, null);

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

            byte[] got = tester.stopAndGetStderr();

            printByteArray("got", got);
            Assert.assertArrayEquals(want, got);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tester_1", "tester_3"})
    public void showImage_big(String testerCmd) throws IOException, InterruptedException {
        try (Tester tester = new Tester(testerCmd)){

            Protocol p = new Protocol();
            p.initializeConnection(tester.getInputStream(), tester.getOutputStream());

            int w = 144*3;
            int h = 140;
            int[] pixels = new int[w * h];
            for ( int i = 0 ; i < pixels.length ; i++ ) {
                pixels[i] = (int)(Math.random()*0XFFFFFF) | 0XFF000000; // Random color fully opaque.
            }

            p.showImage(w, h, pixels, .5F, 0, false, null);
            p.off(); // Let's be sure we are still in sync.
        }
    }

    private void printByteArray(String prefix, byte[] arr) {
        System.out.printf("%s = [", prefix);
        for ( byte b : arr ){
            if ( b>=32 && b<= 126 ) { // ASCII printable.
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

class Tester implements AutoCloseable {
    static final String testingDir = "../../../testing/";
    private final Process _testing;
    private final StreamReader _stderr;
    private boolean _throwIfTerminated = true;

    public enum Flags {
        DEBUG_TO_STDERR("--debug_to_stderr"),
        PIXELS_TO_STDERR("--pixels_to_stderr");

        Flags(String val) {
            this.val = val;
        };
        @Override public String toString() {
            return val;
        }
        private String val;
    }

    public Tester(String cmd, Flags ...flags) throws IOException {
        cmd = "./"+cmd;
        String[] args= new String[flags.length +1];
        args[0] = cmd;
        boolean errorRedirected = false;
        for ( int i = 0 ; i < flags.length ; i++ ) {
            args[i+1] = flags[i].toString();
            errorRedirected = true;
        }
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(new File(testingDir));

        if ( !errorRedirected ) {
            pb = pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        _testing = pb.start();
        _stderr = new StreamReader(_testing.getErrorStream());
    }

    public static int make() throws InterruptedException, IOException {
        Process make = new ProcessBuilder("make", "all")
                .directory(new File(testingDir))
                .inheritIO()
                .start();

        return make.waitFor();
    }

    public byte[] stopAndGetStderr() throws InterruptedException, IOException {
        Thread.sleep(100); // Give some time to flush stderr.
        close();
        return _stderr.getAll();
    }

    public void close() throws IOException {
        if ( !_testing.isAlive() && _throwIfTerminated) { // TODO better an exception?
            throw new IOException("testing program terminated (probably a segfault)");
        }
        _throwIfTerminated = false;
        _testing.destroy();
    }

    public InputStream getInputStream() {
        return _testing.getInputStream();
    }

    public OutputStream getOutputStream() {
        return _testing.getOutputStream();
    }
}

class StreamReader {
    private final InputStream _inner;
    private byte[] _buf;
    private Thread _t;
    private IOException _err;
    private int _read;

    public StreamReader(InputStream in) {
        _inner = in;
        if (_inner == null)
            return;
        _buf = new byte[1024];
        _read = 0;

        _t = new Thread(()->{
            try {
                while (true) {
                    int m = _buf.length - _read;
                    synchronized (_buf) {
                        int b = _inner.read(_buf, _read, m);
                        if (b == -1) { // EOF
                            return;
                        }
                        _read += b;
                    }
                    // Increase the size if required.
                    if (_read == _buf.length) {
                        _buf = Arrays.copyOf(_buf,_read*2);
                    }
                }
            } catch (IOException e) {
                _err = e;
            }
        });
        _t.start();
    }

    public byte[] getAll() throws IOException {
        if (_inner == null)
            throw new IOException("Error stream not redirected.");
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