package com.ruskaof.algorithm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        table.putString("1", "one");
        assertEquals(1, table.size());
        assertEquals("one", table.getString("1"));
    }

    @Test
    void updateExistingKeyDoesNotChangeSize() throws IOException {
        setup();
        table.putString("1", "one");
        table.putString("1", "uno");
        assertEquals(1, table.size());
        assertEquals("uno", table.getString("1"));
    }

    @Test
    void removeElementUpdatesSizeAndReturnsOldValue() throws IOException {
        setup();
        table.putString("1", "one");
        table.putString("2", "two");

        String removed = table.removeString("1");
        assertEquals("one", removed);
        assertEquals(1, table.size());
        assertNull(table.getString("1"));

        assertNull(table.remove("42".getBytes()));
        assertEquals(1, table.size());
    }

    @Test
    void bucketSplittingAndDirectoryGrowthOnManyInserts() throws IOException {
        setup(2); // Small capacity to force frequent splits

        int count = 100;
        for (int i = 0; i < count; i++) {
            table.put(Integer.toString(i).getBytes(), Integer.toString(i).getBytes());
        }

        assertEquals(count, table.size());

        for (int i = 0; i < count; i++) {
            byte[] key = Integer.toString(i).getBytes();
            byte[] value = table.get(key);
            assertNotNull(value);
            assertEquals(Integer.toString(i), new String(value));
        }
    }

    @Test
    void removeElementsAfterSplitsKeepsStructureConsistent() throws IOException {
        setup(2);

        int count = 64;
        for (int i = 0; i < count; i++) {
            table.put(Integer.toString(i).getBytes(), Integer.toString(i).getBytes());
        }
        assertEquals(count, table.size());

        for (int i = 0; i < count; i += 2) {
            byte[] key = Integer.toString(i).getBytes();
            byte[] removed = table.remove(key);
            assertNotNull(removed);
            assertEquals(Integer.toString(i), new String(removed));
        }

        assertEquals(count / 2, table.size());

        for (int i = 0; i < count; i++) {
            byte[] key = Integer.toString(i).getBytes();
            byte[] value = table.get(key);
            if (i % 2 == 0) {
                assertNull(value);
            } else {
                assertNotNull(value);
                assertEquals(Integer.toString(i), new String(value));
            }
        }
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
    void dataAndMetadataPersistAcrossReopen() throws IOException {
        setup();

        int count = 50;
        for (int i = 0; i < count; i++) {
            table.putString("k" + i, "v" + i);
        }

        int depthBefore = table.getGlobalDepth();
        int dirSizeBefore = table.getDirectorySize();
        int[] bucketIdsBefore = new int[dirSizeBefore];
        for (int i = 0; i < dirSizeBefore; i++) {
            bucketIdsBefore[i] = table.getBucketIdForIndex(i);
        }

        table.close();

        ExtendibleHashTable reopened = new ExtendibleHashTable(tempDir, 4, 64, 256);
        table = reopened;

        assertEquals(count, table.size());
        for (int i = 0; i < count; i++) {
            assertEquals("v" + i, table.getString("k" + i));
        }

        assertEquals(depthBefore, table.getGlobalDepth());
        assertEquals(dirSizeBefore, table.getDirectorySize());
        for (int i = 0; i < dirSizeBefore; i++) {
            assertEquals(bucketIdsBefore[i], table.getBucketIdForIndex(i));
        }
    }
}
