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
    void singleKeyIsStoredAndRetrievable() {
        PerfectHash<String, String> ph = new PerfectHash<>(Map.of("a", "value"));
        assertEquals(1, ph.size()); 
        assertEquals("value", ph.get("a"));
    }

    @Test
    void multipleKeysAreStoredWithoutCollisions() {
        Map<String, Integer> data = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            data.put("key-" + i, i);
        }

        PerfectHash<String, Integer> ph = new PerfectHash<>(data);

        assertEquals(data.size(), ph.size());
        for (int i = 0; i < 100; i++) {
            String k = "key-" + i;
            assertEquals(i, ph.get(k));
        }
    }

    @Test
    void randomKeysWorkCorrectly() {
        Map<String, String> data = new HashMap<>();
        for (int i = 0; i < 1_000; i++) {
            data.put(UUID.randomUUID().toString(), "v-" + i);
        }

        PerfectHash<String, String> ph = new PerfectHash<>(data);

        for (Map.Entry<String, String> e : data.entrySet()) {
            String k = e.getKey();
            assertEquals(e.getValue(), ph.get(k));
        }
    }
}

