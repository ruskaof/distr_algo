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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 1)
@Fork(1)
public class PerfectHashBenchmark {

    @Param({"1100", "1300", "1500", "1700", "1900", "2100", "2300", "2500", "2700", "2900"})
    public int keyCount;

    private PerfectHash<String, Integer> perfectHash;
    private String[] keys;
    private static final Random random = new Random();

    @Setup(Level.Trial)
    public void setup() {
        Map<String, Integer> data = new HashMap<>();
        keys = new String[keyCount];
        for (int i = 0; i < keyCount; i++) {
            String k = "key-" + i;
            keys[i] = k;
            data.put(k, i);
        }
        perfectHash = new PerfectHash<>(data);
    }

    @Benchmark
    public int benchmarkGetExisting() {
        int i = random.nextInt(keyCount);
        return perfectHash.get(keys[i]);
    }

    @Benchmark
    public PerfectHash<String, Integer> benchmarkBuild() {
        Map<String, Integer> data = new HashMap<>();
        for (int i = 0; i < keyCount; i++) {
            data.put("key-" + i, i);
        }
        return new PerfectHash<>(data);
    }
}

