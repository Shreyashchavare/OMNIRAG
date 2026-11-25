package com.omragul.processing.util;

import com.omragul.processing.exception.PermanentProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BoundedInputStreamTest {

    @Test
    @DisplayName("FINDING-3.4-01: Below limit stream reads completely and tracks byte count")
    void testBelowLimitStreamReadsSuccessfully() throws IOException {
        byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8); // 13 bytes
        try (BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 20, "doc-1")) {
            byte[] readBytes = stream.readAllBytes();
            assertArrayEquals(data, readBytes);
            assertEquals(13, stream.getTotalBytesRead());
        }
    }

    @Test
    @DisplayName("FINDING-3.4-01: Exactly at limit stream reads completely without error")
    void testExactlyAtLimitStreamReadsSuccessfully() throws IOException {
        byte[] data = "1234567890".getBytes(StandardCharsets.UTF_8); // 10 bytes
        try (BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 10, "doc-2")) {
            byte[] readBytes = stream.readAllBytes();
            assertArrayEquals(data, readBytes);
            assertEquals(10, stream.getTotalBytesRead());
        }
    }

    @Test
    @DisplayName("FINDING-3.4-01: One byte above limit throws FILE_TOO_LARGE immediately on single-byte read")
    void testOneByteAboveLimitThrowsImmediatelyOnSingleByteRead() {
        byte[] data = "12345678901".getBytes(StandardCharsets.UTF_8); // 11 bytes
        assertThrows(PermanentProcessingException.class, () -> {
            try (BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 10, "doc-3")) {
                while (stream.read() != -1) {
                    // read byte-by-byte
                }
            }
        });
    }

    @Test
    @DisplayName("FINDING-3.4-01: One byte above limit throws FILE_TOO_LARGE immediately on buffer read")
    void testOneByteAboveLimitThrowsImmediatelyOnBufferRead() {
        byte[] data = "12345678901".getBytes(StandardCharsets.UTF_8); // 11 bytes
        PermanentProcessingException ex = assertThrows(PermanentProcessingException.class, () -> {
            try (BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 10, "doc-4")) {
                stream.readAllBytes();
            }
        });
        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exceeded maximum allowed size limit"));
    }

    @Test
    @DisplayName("FINDING-3.4-01: Missing or negative Content-Length stream is strictly bounded by actual bytes")
    void testMissingOrNegativeContentLengthIsStrictlyBounded() {
        // Stream delivers 150 bytes, max allowed is 100 bytes
        byte[] data = new byte[150];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;

        PermanentProcessingException ex = assertThrows(PermanentProcessingException.class, () -> {
            try (BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 100, "chunked-doc")) {
                byte[] buf = new byte[32];
                while (stream.read(buf) != -1) {
                    // read in chunks
                }
            }
        });
        assertEquals("FILE_TOO_LARGE", ex.getErrorCode());
    }

    @Test
    @DisplayName("FINDING-3.4-01: Interrupted stream failure propagates IOException")
    void testInterruptedStreamPropagatesIOException() {
        InputStream faultyStream = new InputStream() {
            private int count = 0;
            @Override
            public int read() throws IOException {
                if (++count > 5) {
                    throw new IOException("Simulated network socket reset mid-stream");
                }
                return 'A';
            }
        };

        IOException ex = assertThrows(IOException.class, () -> {
            try (BoundedInputStream stream = new BoundedInputStream(faultyStream, 100, "doc-err")) {
                stream.readAllBytes();
            }
        });
        assertEquals("Simulated network socket reset mid-stream", ex.getMessage());
    }
}
