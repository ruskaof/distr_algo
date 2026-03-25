package com.ruskaof.algorithm.hash;

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

import com.ruskaof.algorithm.hash.LshHashTable;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class LshHashTableBenchmark {

    @Param({ "100", "300", "500", "700", "900", "1100", "1300", "1500", "1700", "1900", "2100", "2300", "2500", "2700",
            "2900" })
    public int entryCount;

    public int dimension = 16;

    private LshHashTable lsh;
    private double[][] vectors;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        vectors = new double[entryCount][dimension];
        random = new Random();

        for (int i = 0; i < entryCount; i++) {
            for (int d = 0; d < dimension; d++) {
                vectors[i][d] = random.nextGaussian();
            }
        }

        lsh = new LshHashTable(dimension, 16);
        for (int i = 0; i < entryCount; i++) {
            lsh.add(vectors[i]);
        }
    }

    @Benchmark
    public List<List<Integer>> benchmarkReadBuckets() {
        return lsh.read();
    }

    @Benchmark
    public void benchmarkAddVector() {
        double[] v = new double[dimension];
        for (int d = 0; d < dimension; d++) {
            v[d] = random.nextGaussian();
        }
        lsh.add(v);
    }

    @Benchmark
    public LshHashTable benchmarkBuildTable() {
        LshHashTable table = new LshHashTable(dimension, 16);
        for (int i = 0; i < entryCount; i++) {
            table.add(vectors[i]);
        }
        return table;
    }
}
