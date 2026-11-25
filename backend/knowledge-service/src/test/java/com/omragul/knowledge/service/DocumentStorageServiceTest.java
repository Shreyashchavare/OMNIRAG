package com.omragul.knowledge.service;

import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.service.impl.DocumentStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageServiceTest {

    @TempDir
    Path tempStorageRoot;

    @TempDir
    Path externalDir;

    private DocumentStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new DocumentStorageServiceImpl(tempStorageRoot.toString());
    }

    @Test
    @DisplayName("PROC-04: Successfully streams stored binary content from valid relative storage path")
    void testOpenStreamSuccess() throws IOException {
        Path testFile = tempStorageRoot.resolve("test-doc.pdf");
        Files.writeString(testFile, "PDF-1.4 Mock Binary Content");

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("test-doc.pdf");
        doc.setMimeType("application/pdf");

        assertTrue(storageService.exists(doc));
        assertEquals(Files.size(testFile), storageService.getFileSize(doc));

        try (InputStream is = storageService.openStream(doc)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("PDF-1.4 Mock Binary Content", content);
        }
    }

    @Test
    @DisplayName("PROC-04: Missing physical content throws ResourceNotFoundException without leaking filesystem path")
    void testMissingPhysicalContentThrowsNotFound() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("nonexistent/missing-file.pdf");

        assertFalse(storageService.exists(doc));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> storageService.openStream(doc));
        assertFalse(ex.getMessage().contains(tempStorageRoot.toString()), "Must not leak internal storage path in exception");
        assertTrue(ex.getMessage().contains("Document storage file not found"));
    }

    @Test
    @DisplayName("PROC-04: Path traversal attempt using '..' is blocked with SecurityException")
    void testPathTraversalAttemptBlocked() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("../../etc/passwd");

        SecurityException ex = assertThrows(SecurityException.class, () -> storageService.openStream(doc));
        assertTrue(ex.getMessage().contains("Illegal path traversal"));
        assertFalse(ex.getMessage().contains(tempStorageRoot.toString()), "Must not leak internal storage path");
    }

    @Test
    @DisplayName("PROC-04: Path traversal attempt using null bytes is blocked with SecurityException")
    void testNullByteTraversalAttemptBlocked() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("valid.pdf\0/../../shadow");

        SecurityException ex = assertThrows(SecurityException.class, () -> storageService.openStream(doc));
        assertFalse(ex.getMessage().contains(tempStorageRoot.toString()));
    }

    @Test
    @DisplayName("PROC-04: Absolute path outside storage boundaries is blocked with SecurityException")
    void testAbsolutePathOutsideRootBlocked() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        File outsideFile = new File(File.listRoots()[0], "sensitive_system_file.txt");
        doc.setStoragePath(outsideFile.getAbsolutePath());

        SecurityException ex = assertThrows(SecurityException.class, () -> storageService.openStream(doc));
        assertFalse(ex.getMessage().contains(outsideFile.getAbsolutePath()), "Must not leak target path in exception");
    }

    @Test
    @DisplayName("PROC-04: Attempted access to system temporary directory outside storage root is blocked")
    void testSystemTempDirectoryAccessBlocked() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath(System.getProperty("java.io.tmpdir") + File.separator + "other_app_secret.txt");

        SecurityException ex = assertThrows(SecurityException.class, () -> storageService.openStream(doc));
        assertFalse(ex.getMessage().contains("other_app_secret.txt"));
    }

    @Test
    @DisplayName("PROC-04: Symbolic link pointing outside storage root is detected and rejected")
    void testSymlinkOutsideRootBlocked() throws IOException {
        Path externalFile = externalDir.resolve("external_secret.txt");
        Files.writeString(externalFile, "TOP_SECRET_DATA");

        Path symlink = tempStorageRoot.resolve("symlink_to_external.txt");
        try {
            Files.createSymbolicLink(symlink, externalFile);
        } catch (UnsupportedOperationException | FileSystemException e) {
            // Windows without SeCreateSymbolicLinkPrivilege may not permit symlink creation in non-admin mode
            return;
        }

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("symlink_to_external.txt");

        SecurityException ex = assertThrows(SecurityException.class, () -> storageService.openStream(doc));
        assertFalse(ex.getMessage().contains(externalFile.toString()));
    }

    @Test
    @DisplayName("PROC-04: Large-file streaming reads chunks without loading entire file to memory")
    void testLargeFileStreamingChunked() throws IOException {
        Path largeFile = tempStorageRoot.resolve("large-data.bin");
        // Write 120 KB in chunks
        byte[] chunk = new byte[8192];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = (byte) (i % 128);
        }

        try (var out = Files.newOutputStream(largeFile)) {
            for (int i = 0; i < 15; i++) {
                out.write(chunk);
            }
        }

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStoragePath("large-data.bin");

        assertEquals(8192 * 15, storageService.getFileSize(doc));

        long totalBytesRead = 0;
        try (InputStream is = storageService.openStream(doc)) {
            byte[] readBuffer = new byte[4096];
            int read;
            while ((read = is.read(readBuffer)) != -1) {
                totalBytesRead += read;
            }
        }

        assertEquals(8192 * 15, totalBytesRead);
    }
}
