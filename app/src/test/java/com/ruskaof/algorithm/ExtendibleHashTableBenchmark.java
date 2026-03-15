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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class ExtendibleHashTableBenchmark {

    @Param({ "100", "300", "500", "700", "900", "1100", "1300", "1500", "1700", "1900", "2100", "2300" })
    public int entryCount;

    private static final int KEY_LENGTH = 16;
    private static final int VALUE_LENGTH = 32;

    private ExtendibleHashTable table;
    private Path tempDir;
    private Random random;
    private byte[][] keys;
    private byte[][] values;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("ext-hash-bench");
        table = new ExtendibleHashTable(tempDir, 8, 64, 256);
        random = new Random();

        keys = new byte[entryCount][];
        values = new byte[entryCount][];
        for (int i = 0; i < entryCount; i++) {
            keys[i] = new byte[KEY_LENGTH];
            values[i] = new byte[VALUE_LENGTH];
            random.nextBytes(keys[i]);
            random.nextBytes(values[i]);
            table.put(keys[i], values[i]);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (table != null) {
            table.close();
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Benchmark
    public byte[] benchmarkGetExisting() {
        int i = random.nextInt(entryCount);
        return table.get(keys[i]);
    }

    /** Always looks up the same key (same entry/bucket); verifies O(1) with good cache locality. */
    @Benchmark
    public byte[] benchmarkGetSameEntry() {
        return table.get(keys[0]);
    }

    @Benchmark
    public void benchmarkPutUpdateExisting() {
        int i = random.nextInt(entryCount);
        byte[] newValue = new byte[VALUE_LENGTH];
        random.nextBytes(newValue);
        table.put(keys[i], newValue);
    }

    @Benchmark
    public void benchmarkPutNewKeys() {
        byte[] key = new byte[KEY_LENGTH];
        byte[] value = new byte[VALUE_LENGTH];
        random.nextBytes(key);
        random.nextBytes(value);
        table.put(key, value);
    }
}
