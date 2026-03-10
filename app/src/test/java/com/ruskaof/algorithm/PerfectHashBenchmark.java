package com.ruskaof.algorithm;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class PerfectHashBenchmark {

    private static final int KEY_COUNT = 50_000;

    private PerfectHash<String, Integer> perfectHash;
    private String[] keys;
    private Random random;

    @Setup(Level.Trial)
    public void setup() {
        Map<String, Integer> data = new HashMap<>();
        keys = new String[KEY_COUNT];
        for (int i = 0; i < KEY_COUNT; i++) {
            String k = "key-" + i;
            keys[i] = k;
            data.put(k, i);
        }
        perfectHash = new PerfectHash<>(data);
        random = new Random(42);
    }

    @Benchmark
    public int benchmarkGetExisting() {
        int i = random.nextInt(KEY_COUNT);
        return perfectHash.get(keys[i]);
    }

    @Benchmark
    public int benchmarkIndexOfExisting() {
        int i = random.nextInt(KEY_COUNT);
        return perfectHash.indexOf(keys[i]);
    }
}

