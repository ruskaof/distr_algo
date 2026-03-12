package com.ruskaof.algorithm;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerfectHashTest {

    @Test
    void emptyMapHasSizeZeroAndNoKeys() {
        PerfectHash<String, Integer> ph = new PerfectHash<>(Map.of());
        assertEquals(0, ph.size());
        assertNull(ph.get("any"));
    }

    @Test
    void singleRandomKeyIsStoredAndRetrievable() {
        String key = UUID.randomUUID().toString();
        String value = TestRandomUtils.randomStringInRange(8, 7);
        PerfectHash<String, String> ph = new PerfectHash<>(Map.of(key, value));
        assertEquals(1, ph.size());
        assertEquals(value, ph.get(key));
    }

    @Test
    void multipleRandomKeysAreStoredWithoutCollisions() {
        Map<String, Integer> data = new HashMap<>();
        int count = 200;
        for (int i = 0; i < count; i++) {
            data.put(UUID.randomUUID().toString(), i);
        }

        PerfectHash<String, Integer> ph = new PerfectHash<>(data);

        assertEquals(data.size(), ph.size());
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            assertEquals(e.getValue(), ph.get(e.getKey()));
        }
    }

    @Test
    void fuzzyInsertAndGet() {
        int rounds = 20;
        int perRound = 200;

        for (int r = 0; r < rounds; r++) {
            Map<String, String> data = new HashMap<>();
            for (int i = 0; i < perRound; i++) {
                data.put(UUID.randomUUID().toString(), TestRandomUtils.randomStringInRange(8, 7));
            }

            PerfectHash<String, String> ph = new PerfectHash<>(data);

            for (Map.Entry<String, String> e : data.entrySet()) {
                assertEquals(e.getValue(), ph.get(e.getKey()));
            }
        }
    }

}

