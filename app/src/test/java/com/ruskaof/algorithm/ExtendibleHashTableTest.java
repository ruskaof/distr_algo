package com.ruskaof.algorithm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtendibleHashTableTest {

    private ExtendibleHashTable table;
    private Path tempDir;

    private void setup(int bucketCapacity) throws IOException {
        tempDir = Files.createTempDirectory("ext-hash-test");
        table = new ExtendibleHashTable(tempDir, bucketCapacity, 64, 256);
    }

    private void setup() throws IOException {
        setup(4);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (table != null) {
            table.close();
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void newTableIsEmpty() throws IOException {
        setup();
        assertEquals(0, table.size());
    }

    @Test
    void putAndGetSingleElement() throws IOException {
        setup();
        String key = TestRandomUtils.randomString(8);
        String value = TestRandomUtils.randomString(16);
        table.putString(key, value);
        assertEquals(1, table.size());
        assertEquals(value, table.getString(key));
    }

    @Test
    void updateExistingKeyDoesNotChangeSize() throws IOException {
        setup();
        String key = TestRandomUtils.randomString(8);
        String first = TestRandomUtils.randomString(16);
        String second = TestRandomUtils.randomString(16);
        table.putString(key, first);
        table.putString(key, second);
        assertEquals(1, table.size());
        assertEquals(second, table.getString(key));
    }

    @Test
    void removeElementUpdatesSizeAndReturnsOldValue() throws IOException {
        setup();
        String key1 = TestRandomUtils.randomString(8);
        String key2 = TestRandomUtils.randomString(8);
        String value1 = TestRandomUtils.randomString(16);
        String value2 = TestRandomUtils.randomString(16);

        table.putString(key1, value1);
        table.putString(key2, value2);

        String removed = table.removeString(key1);
        assertEquals(value1, removed);
        assertEquals(1, table.size());
        assertNull(table.getString(key1));

        assertNull(table.remove(TestRandomUtils.randomString(8).getBytes()));
        assertEquals(1, table.size());
    }

    @Test
    void nullKeyNotAllowed() throws IOException {
        setup();
        assertThrows(IllegalArgumentException.class, () -> table.put(null, "x".getBytes()));
    }

    @Test
    void initialDirectoryAndBucketLayout() throws IOException {
        setup();

        int depth = table.getGlobalDepth();
        int dirSize = table.getDirectorySize();

        assertEquals(1, depth);
        assertEquals(1 << depth, dirSize);

        int firstBucketId = table.getBucketIdForIndex(0);
        for (int i = 0; i < dirSize; i++) {
            assertEquals(firstBucketId, table.getBucketIdForIndex(i));
            assertEquals(depth, table.getBucketLocalDepthForIndex(i));
        }
    }
    @Test
    void fuzzyInsertAndGet() throws IOException {
        setup();

        Map<String, String> model = new HashMap<>();
        int operations = 1_000;

        for (int i = 0; i < operations; i++) {
            int op = i % 3;
            String key = TestRandomUtils.randomString(8);

            switch (op) {
                case 0 -> {
                    String value = TestRandomUtils.randomString(16);
                    table.putString(key, value);
                    model.put(key, value);
                }
                case 1 -> {
                    String expected = model.get(key);
                    String actual = table.getString(key);
                    assertEquals(expected, actual);
                }
                case 2 -> {
                    String expected = model.remove(key);
                    String actual = table.removeString(key);
                    assertEquals(expected, actual);
                }
                default -> throw new IllegalStateException("Unexpected value: " + op);
            }

            assertEquals(model.size(), table.size());
        }
    }
}
