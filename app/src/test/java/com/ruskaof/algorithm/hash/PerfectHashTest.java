package com.ruskaof.algorithm.hash;

import org.junit.jupiter.api.Test;

import com.ruskaof.algorithm.hash.PerfectHash;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerfectHashTest {

    @Test
    void singleRandomKeyIsStoredAndRetrievable() {
        ByteBuffer key = ByteBuffer.wrap(TestRandomUtils.randomBytes(16));
        byte[] value = TestRandomUtils.randomBytes(32);
        PerfectHash<ByteBuffer, byte[]> ph = new PerfectHash<>(Map.of(key, value));
        assertEquals(1, ph.size());
        assertArrayEquals(value, ph.get(key));
    }

    @Test
    void multipleRandomKeysAreStoredWithoutCollisions() {
        Map<ByteBuffer, byte[]> data = new HashMap<>();
        int count = 200;
        for (int i = 0; i < count; i++) {
            data.put(ByteBuffer.wrap(TestRandomUtils.randomBytes(16)), TestRandomUtils.randomBytes(32));
        }

        PerfectHash<ByteBuffer, byte[]> ph = new PerfectHash<>(data);

        assertEquals(data.size(), ph.size());
        for (Map.Entry<ByteBuffer, byte[]> e : data.entrySet()) {
            assertArrayEquals(e.getValue(), ph.get(e.getKey()));
        }
    }

    @Test
    void fuzzyInsertAndGet() {
        int rounds = 20;
        int perRound = 200;

        for (int r = 0; r < rounds; r++) {
            Map<ByteBuffer, byte[]> data = new HashMap<>();
            for (int i = 0; i < perRound; i++) {
                data.put(
                        ByteBuffer.wrap(TestRandomUtils.randomBytes(16)),
                        TestRandomUtils.randomBytes(32)
                );
            }

            PerfectHash<ByteBuffer, byte[]> ph = new PerfectHash<>(data);

            for (Map.Entry<ByteBuffer, byte[]> e : data.entrySet()) {
                assertArrayEquals(e.getValue(), ph.get(e.getKey()));
            }
        }
    }
}
