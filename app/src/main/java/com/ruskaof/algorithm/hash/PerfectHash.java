package com.ruskaof.algorithm.hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;


@SuppressWarnings("unchecked")
public final class PerfectHash<K, V> {

    private static final int PRIME = 1_000_000_007;

    private final int a1;
    private final int b1;
    private final int primarySize;
    private final SecondaryTable<K, V>[] buckets;
    private final int size;

    public PerfectHash(Map<K, V> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }

        int n = entries.size();
        int m = n;

        int chosenA1;
        int chosenB1;
        SecondaryTable<K, V>[] chosenBuckets;
        Random rng = new Random();

        outer: while (true) {
            List<Map.Entry<K, V>>[] bucketLists = (List<Map.Entry<K, V>>[]) new List[m];

            int aCandidate = rng.nextInt(1, PRIME);
            int bCandidate = rng.nextInt(PRIME);

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

            if (sumSquares > 10L * n) {
                continue;
            }

            SecondaryTable<K, V>[] tmpBuckets = (SecondaryTable<K, V>[]) new SecondaryTable[m];
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
        return h == Integer.MIN_VALUE ? 0 : Math.abs(h);
    }

    private static int indexForPrimary(Object key, int a, int b, int tableSize) {
        int x = baseHash(key);
        long res = ((long) a * x + b) % PRIME;
        return (int) (res % tableSize);
    }

    private static final class SecondaryTable<K, V> {
        private final Object[] keys;
        private final Object[] values;
        private final int a2;
        private final int b2;
        private final int tableSize;

        private static final int SECONDARY_MAX_RETRIES = 100;

        SecondaryTable(List<Map.Entry<K, V>> entries) {
            int n = entries.size();
            int m = n * n;

            Object[] chosenKeys = new Object[m];
            Object[] chosenValues = new Object[m];
            int chosenA = 1;
            int chosenB = 0;

            Random rng = new Random();

            for (int attempt = 0; attempt < SECONDARY_MAX_RETRIES; attempt++) {
                chosenKeys = new Object[m];
                chosenValues = new Object[m];

                int aCandidate = rng.nextInt(1, PRIME);
                int bCandidate = rng.nextInt(PRIME);

                boolean collision = false;
                for (Map.Entry<K, V> e : entries) {
                    Object key = e.getKey();
                    int idx = indexForSecondary(key, aCandidate, bCandidate, m);
                    Object existingKey = chosenKeys[idx];
                    if (existingKey == null) {
                        chosenKeys[idx] = key;
                        chosenValues[idx] = e.getValue();
                    } else if (!existingKey.equals(key)) {
                        collision = true;
                        break;
                    } else {
                        chosenValues[idx] = e.getValue();
                    }
                }

                chosenA = aCandidate;
                chosenB = bCandidate;

                if (!collision) {
                    break;
                }

                if (attempt == SECONDARY_MAX_RETRIES - 1) {
                    chosenKeys = new Object[m];
                    chosenValues = new Object[m];
                    for (Map.Entry<K, V> e : entries) {
                        Object key = e.getKey();
                        int idx = indexForSecondary(key, chosenA, chosenB, m);
                        chosenKeys[idx] = key;
                        chosenValues[idx] = e.getValue();
                    }
                }
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

        V get(Object key) {
            int idx = indexForSecondary(key, a2, b2, tableSize);
            Object storedKey = keys[idx];
            if (storedKey == null || !storedKey.equals(key)) {
                return null;
            }
            return (V) values[idx];
        }

    }

    public int size() {
        return size;
    }

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
}
