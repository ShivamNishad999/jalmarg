package org.growforge.jalmarg;

import org.growforge.jalmarg.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;

    @BeforeEach
    void setUp() throws IOException {
        // Use relative-style directory path with dot notation to verify normalization
        Path uploadFolder = tempDir.resolve("sub/./uploads");
        storage = new StorageService(null, "", uploadFolder.toString());
    }

    @Test
    void saveAndLoadValidPhoto() throws IOException {
        byte[] dummyImage = new byte[]{ (byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9 };
        String key = storage.save(dummyImage, "image/jpeg");

        assertNotNull(key);
        assertTrue(key.startsWith("reports/"));
        assertTrue(key.endsWith(".jpg"));

        byte[] loaded = storage.load(key);
        assertNotNull(loaded, "Loaded photo bytes should not be null");
        assertArrayEquals(dummyImage, loaded);
    }

    @Test
    void saveAndLoadPngPhoto() throws IOException {
        byte[] dummyImage = new byte[]{ 0x11, 0x22, 0x33 };
        String key = storage.save(dummyImage, "image/png");

        assertNotNull(key);
        assertTrue(key.endsWith(".png"));

        byte[] loaded = storage.load(key);
        assertNotNull(loaded);
        assertArrayEquals(dummyImage, loaded);
    }

    @Test
    void loadNonexistentFileReturnsNull() {
        byte[] loaded = storage.load("reports/2026-10-09/does-not-exist.jpg");
        assertNull(loaded, "Nonexistent file must return null");
    }

    @Test
    void loadNullOrEmptyReturnsNull() {
        assertNull(storage.load(null));
        assertNull(storage.load(""));
    }

    @Test
    void pathTraversalAttemptsReturnNull() throws IOException {
        // Create a sensitive file outside the upload directory
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP_SECRET");

        // Attempt relative path traversal with ..
        assertNull(storage.load("../secret.txt"), "Parent directory traversal must be blocked");
        assertNull(storage.load("../../secret.txt"), "Multi-level traversal must be blocked");
        assertNull(storage.load("reports/../../secret.txt"), "Nested traversal must be blocked");
        assertNull(storage.load("..\\secret.txt"), "Backslash traversal must be blocked");
    }

    @Test
    void contentTypeDetection() {
        assertEquals("image/png", StorageService.contentTypeFor("photo.png"));
        assertEquals("image/webp", StorageService.contentTypeFor("photo.webp"));
        assertEquals("image/gif", StorageService.contentTypeFor("photo.gif"));
        assertEquals("image/jpeg", StorageService.contentTypeFor("photo.jpg"));
        assertEquals("image/jpeg", StorageService.contentTypeFor("photo.jpeg"));
        assertEquals("image/jpeg", StorageService.contentTypeFor("unknown_file"));
    }
}
