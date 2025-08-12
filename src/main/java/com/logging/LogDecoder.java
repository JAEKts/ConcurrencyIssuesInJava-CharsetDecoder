package com.logging;

import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

public class LogDecoder {

    private static final CharsetDecoder sharedDecoder = StandardCharsets.UTF_8.newDecoder();

    LogDecoder() {}

    public static String decodeInput(byte[] input) {
        try {
            return sharedDecoder.decode(ByteBuffer.wrap(input)).toString();
        } catch (Exception e) {
            throw new RuntimeException("Decoder failed (likely concurrency issue)", e);
        }
    }
}
