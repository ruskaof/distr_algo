package com.ruskaof.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable two‑level perfect hash map for a fixed set of keys.
 * <p>
 * Level 1: a universal hash function distributes keys into buckets. <br>
 * Level 2: for each bucket, a separate perfect hash table with its own
 * universal hash function is built over the keys in that bucket.
 */
public final class PerfectHash<K, V> {

    // Large prime for universal hashing.
    private static final int PRIME = 1_000_000_007;

    private final int a1;
    private final int b1;
    private final int primarySize;
    private final SecondaryTable<K, V>[] buckets;
    private final int size;

    /**
     * Creates an empty perfect hash.
     */
    @SuppressWarnings("unchecked")
    public PerfectHash() {
        this.a1 = 1;
        this.b1 = 0;
        this.primarySize = 0;
        this.buckets = (SecondaryTable<K, V>[]) new SecondaryTable[0];
        this.size = 0;
    }

    /**
     * Builds a two‑level perfect hash for the given key-value mapping.
     * <p>
     * The primary hash uses parameters {@code (a1, b1)} to distribute keys into
     * {@code m} buckets. For each non‑empty bucket a secondary perfect hash
     * table is built, each with its own independently chosen universal hash
     * parameters.
     *
     * @param entries map of keys to values (must not be {@code null} and must not contain {@code null} keys)
     */
    @SuppressWarnings("unchecked")
    public PerfectHash(Map<K, V> entries) {
        Objects.requireNonNull(entries, "entries must not be null");

        if (entries.isEmpty()) {
            this.a1 = 1;
            this.b1 = 0;
            this.primarySize = 0;
            this.buckets = (SecondaryTable<K, V>[]) new SecondaryTable[0];
            this.size = 0;
            return;
        }

        int n = entries.size();
        int m = n; // size of primary table – linear in n

        int chosenA1;
        int chosenB1;
        SecondaryTable<K, V>[] chosenBuckets;

        // FKS analysis: with m = n, the expected value of sum n_i^2 over buckets
        // is O(n). We resample (a1, b1) until the realized sum stays within a
        // constant factor of n, guaranteeing total space O(n).
        outer:
        while (true) {
            @SuppressWarnings("unchecked") List<Map.Entry<K, V>>[] bucketLists =
                    (List<Map.Entry<K, V>>[]) new List[m];

            int aCandidate = ThreadLocalRandom.current().nextInt(1, PRIME); // a1 != 0
            int bCandidate = ThreadLocalRandom.current().nextInt(PRIME);

            for (Map.Entry<K, V> e : entries.entrySet()) {
                K key = e.getKey();
                if (key == null) {
                    throw new IllegalArgumentException("Null keys are not supported");
                }
                int idx = indexForPrimary(key, aCandidate, bCandidate, m);
                List<Map.Entry<K, V>> list = bucketLists[idx];
                if (list == null) {
                    list = new ArrayList<>();
                    bucketLists[idx] = list;
                }
                list.add(e);
            }

            long sumSquares = 0L;
            for (int i = 0; i < m; i++) {
                List<Map.Entry<K, V>> list = bucketLists[i];
                if (list != null) {
                    int bucketSize = list.size();
                    sumSquares += (long) bucketSize * bucketSize;
                }
            }

            // If the realized space would be too large, resample the primary hash.
            if (sumSquares > 4L * n) {
                continue;
            }

            SecondaryTable<K, V>[] tmpBuckets =
                    (SecondaryTable<K, V>[]) new SecondaryTable[m];
            for (int i = 0; i < m; i++) {
                List<Map.Entry<K, V>> list = bucketLists[i];
                if (list != null && !list.isEmpty()) {
                    tmpBuckets[i] = new SecondaryTable<>(list);
                }
            }

            chosenA1 = aCandidate;
            chosenB1 = bCandidate;
            chosenBuckets = tmpBuckets;
            break outer;
        }

        this.a1 = chosenA1;
        this.b1 = chosenB1;
        this.primarySize = m;
        this.buckets = chosenBuckets;
        this.size = n;
    }

    private static int baseHash(Object key) {
        int h = Objects.hashCode(key);
        return h & 0x7fffffff;
    }

    private static int indexForPrimary(Object key, int a, int b, int tableSize) {
        int x = baseHash(key);
        long res = ((long) a * x + b) % PRIME;
        return (int) (res % tableSize);
    }

    /**
     * Secondary perfect hash table for a single primary bucket.
     * Uses its own universal hash parameters (a2, b2) and table size m2.
     */
    private static final class SecondaryTable<K, V> {
        private final Object[] keys;
        private final Object[] values;
        private final int a2;
        private final int b2;
        private final int tableSize;

        SecondaryTable(List<Map.Entry<K, V>> entries) {
            int n = entries.size();
            if (n == 1) {
                this.tableSize = 1;
                this.keys = new Object[1];
                this.values = new Object[1];
                Map.Entry<K, V> e = entries.get(0);
                this.keys[0] = e.getKey();
                this.values[0] = e.getValue();
                this.a2 = 1;
                this.b2 = 0;
                return;
            }

            int m = n * n; // classic FKS: n_i^2 for bucket of size n_i

            Object[] chosenKeys;
            Object[] chosenValues;
            int chosenA;
            int chosenB;

            outer:
            while (true) {
                chosenKeys = new Object[m];
                chosenValues = new Object[m];

                int aCandidate = ThreadLocalRandom.current().nextInt(1, PRIME);
                int bCandidate = ThreadLocalRandom.current().nextInt(PRIME);

                for (Map.Entry<K, V> e : entries) {
                    Object key = e.getKey();
                    int idx = indexForSecondary(key, aCandidate, bCandidate, m);
                    Object existingKey = chosenKeys[idx];
                    if (existingKey == null) {
                        chosenKeys[idx] = key;
                        chosenValues[idx] = e.getValue();
                    } else if (!existingKey.equals(key)) {
                        // Collision with different key – try another (a2, b2)
                        continue outer;
                    } else {
                        // Same key, update value.
                        chosenValues[idx] = e.getValue();
                    }
                }

                chosenA = aCandidate;
                chosenB = bCandidate;
                break;
            }

            this.keys = chosenKeys;
            this.values = chosenValues;
            this.a2 = chosenA;
            this.b2 = chosenB;
            this.tableSize = m;
        }

        private static int indexForSecondary(Object key, int a, int b, int tableSize) {
            int x = baseHash(key);
            long res = ((long) a * x + b) % PRIME;
            return (int) (res % tableSize);
        }

        boolean containsKey(Object key) {
            int idx = indexForSecondary(key, a2, b2, tableSize);
            Object storedKey = keys[idx];
            return storedKey != null && storedKey.equals(key);
        }

        @SuppressWarnings("unchecked")
        V get(Object key) {
            int idx = indexForSecondary(key, a2, b2, tableSize);
            Object storedKey = keys[idx];
            if (storedKey == null || !storedKey.equals(key)) {
                return null;
            }
            return (V) values[idx];
        }

        int indexOf(Object key) {
            int idx = indexForSecondary(key, a2, b2, tableSize);
            Object storedKey = keys[idx];
            if (storedKey == null || !storedKey.equals(key)) {
                return -1;
            }
            return idx;
        }
    }

    /**
     * Returns the number of distinct keys this perfect hash was built for.
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if the given key is part of this perfect hash.
     */
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (primarySize == 0) {
            return false;
        }
        int primaryIdx = indexForPrimary(key, a1, b1, primarySize);
        SecondaryTable<K, V> table = buckets[primaryIdx];
        return table != null && table.containsKey(key);
    }

    /**
     * Returns the value associated with the given key, or {@code null} if the key
     * is not part of this perfect hash.
     */
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (primarySize == 0) {
            return null;
        }
        int primaryIdx = indexForPrimary(key, a1, b1, primarySize);
        SecondaryTable<K, V> table = buckets[primaryIdx];
        if (table == null) {
            return null;
        }
        return table.get(key);
    }

    /**
     * Returns an index for the given key that is collision‑free within this
     * structure. The exact numeric range is implementation‑dependent and
     * should be treated as an opaque identifier.
     *
     * @throws IllegalArgumentException if the key is not part of this perfect hash
     */
    public int indexOf(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (primarySize == 0) {
            throw new IllegalArgumentException("Unknown key for this PerfectHash: " + key);
        }
        int primaryIdx = indexForPrimary(key, a1, b1, primarySize);
        SecondaryTable<K, V> table = buckets[primaryIdx];
        if (table == null) {
            throw new IllegalArgumentException("Unknown key for this PerfectHash: " + key);
        }
        int secondaryIdx = table.indexOf(key);
        if (secondaryIdx < 0) {
            throw new IllegalArgumentException("Unknown key for this PerfectHash: " + key);
        }
        // Combine primary and secondary indices into a single integer identifier.
        // This does not need to be dense; it just needs to be collision‑free.
        return primaryIdx * 31 + secondaryIdx;
    }
}
