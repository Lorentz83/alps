package com.github.lorentz83.alps.communication;

import java.io.IOException;

/**
 * Represents an error in the communication protocol.
 */
public class ProtocolException extends IOException {
    public ProtocolException(String message) {
        super(message);
    }
}
