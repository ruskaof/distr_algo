package com.ruskaof.algorithm.hash;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
        byte[] key = TestRandomUtils.randomBytes(8);
        byte[] value = TestRandomUtils.randomBytes(16);
        table.put(key, value);
        assertEquals(1, table.size());
        assertArrayEquals(value, table.get(key));
    }

    @Test
    void updateExistingKeyDoesNotChangeSize() throws IOException {
        setup();
        byte[] key = TestRandomUtils.randomBytes(8);
        byte[] first = TestRandomUtils.randomBytes(16);
        byte[] second = TestRandomUtils.randomBytes(16);
        table.put(key, first);
        table.put(key, second);
        assertEquals(1, table.size());
        assertArrayEquals(second, table.get(key));
    }

    @Test
    void removeElementUpdatesSizeAndReturnsOldValue() throws IOException {
        setup();
        byte[] key1 = TestRandomUtils.randomBytes(8);
        byte[] key2 = TestRandomUtils.randomBytes(8);
        byte[] value1 = TestRandomUtils.randomBytes(16);
        byte[] value2 = TestRandomUtils.randomBytes(16);

        table.put(key1, value1);
        table.put(key2, value2);

        byte[] removed = table.remove(key1);
        assertArrayEquals(value1, removed);
        assertEquals(1, table.size());
        assertNull(table.get(key1));

        assertNull(table.remove(TestRandomUtils.randomBytes(8)));
        assertEquals(1, table.size());
    }

    @Test
    void nullKeyNotAllowed() throws IOException {
        setup();
        assertThrows(IllegalArgumentException.class, () -> table.put(null, new byte[]{1}));
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

        Map<ByteArrayKey, byte[]> model = new HashMap<>();
        int operations = 1_000;

        for (int i = 0; i < operations; i++) {
            int op = i % 3;
            byte[] key = TestRandomUtils.randomBytes(8);
            ByteArrayKey wrappedKey = new ByteArrayKey(key);

            switch (op) {
                case 0 -> {
                    byte[] value = TestRandomUtils.randomBytes(16);
                    table.put(key, value);
                    model.put(wrappedKey, value);
                }
                case 1 -> {
                    byte[] expected = model.get(wrappedKey);
                    byte[] actual = table.get(key);
                    assertArrayEquals(expected, actual);
                }
                case 2 -> {
                    byte[] expected = model.remove(wrappedKey);
                    byte[] actual = table.remove(key);
                    assertArrayEquals(expected, actual);
                }
                default -> throw new IllegalStateException("Unexpected value: " + op);
            }

            assertEquals(model.size(), table.size());
        }
    }

    private record ByteArrayKey(byte[] data) {
        @Override
        public boolean equals(Object o) {
            return o instanceof ByteArrayKey other && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
