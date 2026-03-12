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
@Measurement(iterations = 1, time = 1)
@Fork(1)
public class ExtendibleHashTableBenchmark {

    @Param({"100", "300", "500", "700", "900"})
    public int initialKeys;

    private ExtendibleHashTable table;
    private Path tempDir;
    private Random random;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("ext-hash-bench");
        table = new ExtendibleHashTable(tempDir, 8, 64, 256);
        random = new Random();

        for (int i = 0; i < initialKeys; i++) {
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

    @Benchmark
    public String benchmarkGetExisting() {
        int i = random.nextInt(initialKeys);
        String key = "key-" + i;
        return table.getString(key);
    }

    @Benchmark
    public void benchmarkPutUpdateExisting() {
        int i = random.nextInt(initialKeys);
        String key = "key-" + i;
        String value = "value-updated-" + i;
        table.putString(key, value);
    }

    @Benchmark
    public void benchmarkPutNewKeys() {
        int i = initialKeys + random.nextInt(initialKeys);
        String key = "insert-key-" + i;
        String value = "insert-value-" + i;
        table.putString(key, value);
    }
}
