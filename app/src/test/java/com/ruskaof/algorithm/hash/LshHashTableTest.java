package com.ruskaof.algorithm.hash;

import org.junit.jupiter.api.Test;

import com.ruskaof.algorithm.hash.LshHashTable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LshHashTableTest {

    @Test
    void emptyIndexHasSizeZeroAndNoCandidates() {
        LshHashTable lsh = new LshHashTable(3, 4);

        assertEquals(0, lsh.size());
        assertEquals(3, lsh.getDimension());

        assertTrue(lsh.read().isEmpty());
    }

    @Test
    void addedVectorIsReturnedAsCandidateForItself() {
        LshHashTable lsh = new LshHashTable(3, 8);

        double[] v = new double[]{1.0, 2.0, 3.0};
        int id = lsh.add(v);

        assertEquals(1, lsh.size());
        assertEquals(0, id);

        boolean foundInSomeBucket = lsh.read().stream().anyMatch(bucket -> bucket.contains(id));
        assertTrue(foundInSomeBucket, "Self id must be present in at least one bucket");
    }

    @Test
    void multipleVectorsSelfQueryAlwaysFindsSelfId() {
        int dim = 5;
        int count = 100;

        LshHashTable lsh = new LshHashTable(dim, 10);

        List<double[]> vectors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] v = new double[dim];
            for (int d = 0; d < dim; d++) {
                v[d] = TestRandomUtils.randomGaussian();
            }
            vectors.add(v);
            int id = lsh.add(v);
            assertEquals(i, id);
        }

        assertEquals(count, lsh.size());

        List<List<Integer>> buckets = lsh.read();
        for (int i = 0; i < count; i++) {
            final int id = i;
            boolean present = buckets.stream().anyMatch(bucket -> bucket.contains(id));
            assertTrue(present, "Self id must be present in at least one bucket for index " + i);
        }
    }

    @Test
    void constructorFromListStoresAllVectorsAndAllowsQueries() {
        int dim = 4;
        List<double[]> initial = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double[] v = new double[dim];
            for (int d = 0; d < dim; d++) {
                v[d] = TestRandomUtils.randomGaussian();
            }
            initial.add(v);
        }

        LshHashTable lsh = new LshHashTable(initial, 8);
        assertEquals(dim, lsh.getDimension());
        assertEquals(initial.size(), lsh.size());

        List<List<Integer>> buckets = lsh.read();
        boolean firstPresent = buckets.stream().anyMatch(bucket -> bucket.contains(0));
        assertTrue(firstPresent, "First vector id must be present in at least one bucket");

        double[] storedFirst = lsh.getVector(0);
        assertArrayEquals(initial.get(0), storedFirst);
    }

    @Test
    void duplicateVectorsEndUpInSameBucket() {
        int dim = 6;
        int baseCount = 20;
        int duplicatesPerVector = 3;

        LshHashTable lsh = new LshHashTable(dim, 12);

        List<List<Integer>> groups = new ArrayList<>();

        for (int i = 0; i < baseCount; i++) {
            double[] base = new double[dim];
            for (int d = 0; d < dim; d++) {
                base[d] = TestRandomUtils.randomGaussian();
            }

            List<Integer> groupIds = new ArrayList<>();
            for (int j = 0; j < duplicatesPerVector; j++) {
                int id = lsh.add(base.clone());
                groupIds.add(id);
            }
            groups.add(groupIds);
        }

        List<List<Integer>> buckets = lsh.read();

        for (List<Integer> group : groups) {
            boolean foundTogether = buckets.stream()
                    .anyMatch(bucket -> bucket.containsAll(group));
            assertTrue(foundTogether,
                    "All duplicate ids for a vector must appear together in at least one bucket");
        }
    }

    @Test
    void similarVectorsTendToShareBuckets() {
        int dim = 10;
        int vectorsPerCluster = 50;

        LshHashTable lsh = new LshHashTable(dim, 16);

        List<Integer> clusterAIds = new ArrayList<>();
        List<Integer> clusterBIds = new ArrayList<>();

        double[] base = new double[dim];
        for (int d = 0; d < dim; d++) {
            base[d] = TestRandomUtils.randomGaussian();
        }

        double epsilon = 0.01;
        for (int i = 0; i < vectorsPerCluster; i++) {
            double[] v1 = new double[dim];
            double[] v2 = new double[dim];
            for (int d = 0; d < dim; d++) {
                double delta1 = (TestRandomUtils.randomDouble() * 2 - 1) * epsilon;
                double delta2 = (TestRandomUtils.randomDouble() * 2 - 1) * epsilon;
                v1[d] = base[d] + delta1;
                v2[d] = base[d] + delta2;
            }
            clusterAIds.add(lsh.add(v1));
            clusterBIds.add(lsh.add(v2));
        }

        List<List<Integer>> buckets = lsh.read();

        int bestClusterAInOneBucket = 0;
        int bestClusterBInOneBucket = 0;

        for (List<Integer> bucket : buckets) {
            int inA = 0;
            int inB = 0;
            for (int id : bucket) {
                if (clusterAIds.contains(id)) {
                    inA++;
                }
                if (clusterBIds.contains(id)) {
                    inB++;
                }
            }
            if (inA > bestClusterAInOneBucket) {
                bestClusterAInOneBucket = inA;
            }
            if (inB > bestClusterBInOneBucket) {
                bestClusterBInOneBucket = inB;
            }
        }

        assertTrue(bestClusterAInOneBucket >= vectorsPerCluster * 0.5,
                "At least half of cluster A vectors should appear together in some bucket");
        assertTrue(bestClusterBInOneBucket >= vectorsPerCluster * 0.5,
                "At least half of cluster B vectors should appear together in some bucket");
    }

    @Test
    void dimensionMismatchOnAddThrows() {
        LshHashTable lsh = new LshHashTable(3, 4);

        double[] badVector = new double[]{1.0, 2.0};
        assertThrows(IllegalArgumentException.class, () -> lsh.add(badVector));
    }

    @Test
    void getVectorWithInvalidIdThrows() {
        LshHashTable lsh = new LshHashTable(2, 4);
        lsh.add(new double[]{1.0, 2.0});

        assertThrows(IndexOutOfBoundsException.class, () -> lsh.getVector(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> lsh.getVector(1));
    }
}

