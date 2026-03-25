package com.ruskaof.algorithm.hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class LshHashTable {

    private final int dimension;
    private final int numHashFunctions;

    private final double[][] planes;

    private final Map<Long, List<Integer>> table;

    private final List<double[]> vectors = new ArrayList<>();

    public LshHashTable(int dimension, int numHashFunctions) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        if (numHashFunctions <= 0) {
            throw new IllegalArgumentException("numHashFunctions must be positive");
        }

        this.dimension = dimension;
        this.numHashFunctions = numHashFunctions;

        this.planes = new double[numHashFunctions][dimension];
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int h = 0; h < numHashFunctions; h++) {
            double[] vec = planes[h];
            for (int d = 0; d < dimension; d++) {
                vec[d] = rnd.nextGaussian();
            }
        }

        this.table = new HashMap<>();
    }

    public LshHashTable(List<double[]> initialVectors, int numHashFunctions) {
        this(requireDimension(initialVectors), numHashFunctions);
        addAll(initialVectors);
    }

    private static int requireDimension(List<double[]> vectors) {
        Objects.requireNonNull(vectors, "vectors must not be null");
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("vectors must not be empty");
        }
        double[] first = vectors.get(0);
        if (first == null) {
            throw new IllegalArgumentException("First vector is null");
        }
        int dim = first.length;
        if (dim == 0) {
            throw new IllegalArgumentException("Vector dimension must be positive");
        }
        for (int i = 1; i < vectors.size(); i++) {
            double[] v = vectors.get(i);
            if (v == null) {
                throw new IllegalArgumentException("Vector at index " + i + " is null");
            }
            if (v.length != dim) {
                throw new IllegalArgumentException("All vectors must have the same dimension");
            }
        }
        return dim;
    }

    public int add(double[] vector) {
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.length != dimension) {
            throw new IllegalArgumentException(
                    "Vector dimension " + vector.length + " does not match index dimension " + dimension);
        }
        int id = vectors.size();
        vectors.add(vector.clone());

        long hash = hash(vector);
        List<Integer> bucket = table.get(hash);
        if (bucket == null) {
            bucket = new ArrayList<>();
            table.put(hash, bucket);
        }
        bucket.add(id);

        return id;
    }

    public void addAll(List<double[]> newVectors) {
        Objects.requireNonNull(newVectors, "newVectors must not be null");
        for (double[] v : newVectors) {
            add(v);
        }
    }

    public List<List<Integer>> read() {
        if (vectors.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Integer>> allBuckets = new ArrayList<>();
        for (List<Integer> bucket : table.values()) {
            if (bucket.isEmpty()) {
                continue;
            }
            allBuckets.add(new ArrayList<>(bucket));
        }
        return allBuckets;
    }

    public double[] getVector(int id) {
        if (id < 0 || id >= vectors.size()) {
            throw new IndexOutOfBoundsException("Invalid vector id: " + id);
        }
        return vectors.get(id).clone();
    }

    public int size() {
        return vectors.size();
    }

    public int getDimension() {
        return dimension;
    }

    private long hash(double[] vector) {
        double[][] proj = planes;
        long bits = 0L;
        for (int h = 0; h < numHashFunctions; h++) {
            double[] dir = proj[h];
            double dot = 0.0;
            for (int d = 0; d < dimension; d++) {
                dot += vector[d] * dir[d];
            }
            if (dot >= 0.0) {
                bits |= (1L << h);
            }
        }
        return bits;
    }
}
