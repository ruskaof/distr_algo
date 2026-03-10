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
        assertFalse(ph.containsKey("any"));
        assertNull(ph.get("any"));
    }

    @Test
    void singleKeyIsStoredAndRetrievable() {
        PerfectHash<String, String> ph = new PerfectHash<>(Map.of("a", "value"));
        assertEquals(1, ph.size());
        assertTrue(ph.containsKey("a"));
        assertEquals("value", ph.get("a"));
        assertThrows(IllegalArgumentException.class, () -> ph.indexOf("b"));
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
            assertTrue(ph.containsKey(k));
            assertEquals(i, ph.get(k));
            int idx = ph.indexOf(k);
            // indexOf must be stable for the same key.
            assertEquals(idx, ph.indexOf(k));
        }

        assertFalse(ph.containsKey("missing"));
        assertNull(ph.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> ph.indexOf("missing"));
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
            assertTrue(ph.containsKey(k));
            assertEquals(e.getValue(), ph.get(k));
            assertNotEquals(-1, ph.indexOf(k));
        }
    }
}

