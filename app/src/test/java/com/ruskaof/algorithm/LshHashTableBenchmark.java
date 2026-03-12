package com.ruskaof.algorithm;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 1)
@Fork(1)
public class LshHashTableBenchmark {

    @Param({"100", "300", "500", "700", "900"})
    public int vectorCount;

    public int dimension = 16;

    private LshHashTable lsh;
    private double[][] vectors;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        vectors = new double[vectorCount][dimension];
        random = new Random();

        for (int i = 0; i < vectorCount; i++) {
            for (int d = 0; d < dimension; d++) {
                vectors[i][d] = random.nextGaussian();
            }
        }

        lsh = new LshHashTable(dimension, 16);
        for (int i = 0; i < vectorCount; i++) {
            lsh.add(vectors[i]);
        }
    }

    @Benchmark
    public List<List<Integer>> benchmarkReadBuckets() {
        return lsh.read();
    }
}
