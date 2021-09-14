package com.github.lorentz83.alps.communication;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
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

    @org.junit.jupiter.api.Timeout(value = 2, unit = SECONDS)
    @org.junit.jupiter.api.RepeatedTest(30)
    void writeColumn() throws IOException, InterruptedException {
        Process testing = new ProcessBuilder("./protocol_tester")
                .directory(new File(testingDir))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            Protocol p = new Protocol();
            p.initializeConnection(testing.getInputStream(), testing.getOutputStream());

            int[] pixels = new int[100];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = i << 8 | 0xFF000000; // Fully opaque green.
            }
            p.writeColumn(pixels, .5f);
        } finally {
            testing.destroy();
        }
    }


    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.Timeout(value = 2, unit = SECONDS)
    void writeColumn_missingBytes() throws IOException, InterruptedException {
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