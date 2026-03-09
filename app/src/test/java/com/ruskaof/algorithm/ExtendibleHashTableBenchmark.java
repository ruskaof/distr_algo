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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ExtendibleHashTableBenchmark {

    private static final int INITIAL_KEYS = 10_000;
    private static final int KEY_SPACE = 50_000;

    private ExtendibleHashTable table;
    private Path tempDir;
    private Random random;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("ext-hash-bench");
        // Use a small bucket capacity to exercise splitting moderately,
        // but not so small that the directory explodes immediately.
        table = new ExtendibleHashTable(tempDir, 8, 64, 256);
        random = new Random(42);

        // Pre-populate with a set of keys so that benchmarks mostly
        // measure steady-state get/put on an existing structure.
        for (int i = 0; i < INITIAL_KEYS; i++) {
            String k = "key-" + i;
            String v = "value-" + i;
            table.putString(k, v);
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

    /**
     * Benchmark read throughput on mostly-existing keys.
     */
    @Benchmark
    public String benchmarkGetExisting() {
        int i = random.nextInt(INITIAL_KEYS);
        String key = "key-" + i;
        return table.getString(key);
    }

    /**
     * Benchmark overwrite throughput (updates an existing key).
     */
    @Benchmark
    public void benchmarkPutUpdateExisting() {
        int i = random.nextInt(INITIAL_KEYS);
        String key = "key-" + i;
        String value = "value-updated-" + i;
        table.putString(key, value);
    }

    /**
     * Benchmark inserts over a larger key space to trigger splits over time.
     */
    @Benchmark
    public void benchmarkPutNewKeys() {
        int i = random.nextInt(KEY_SPACE);
        String key = "insert-key-" + i;
        String value = "insert-value-" + i;
        table.putString(key, value);
    }
}
