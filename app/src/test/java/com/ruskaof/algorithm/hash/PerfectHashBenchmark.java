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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class PerfectHashBenchmark {

    @Param({ "100", "300", "500", "700", "900", "1100", "1300", "1500", "1700", "1900", "2100", "2300", "2500", "2700",
            "2900" })
    public int entryCount;

    private static final int KEY_LENGTH = 16;
    private static final int VALUE_LENGTH = 32;
    private static final int RANDOM_INDEX_POOL_SIZE = 4096;

    private PerfectHash<ByteBuffer, byte[]> perfectHash;
    private ByteBuffer[] keys;
    private Random random;
    private int[] getExistingIndices;
    private int getExistingCursor;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random();

        Map<ByteBuffer, byte[]> data = new HashMap<>();
        keys = new ByteBuffer[entryCount];
        for (int i = 0; i < entryCount; i++) {
            byte[] keyBytes = new byte[KEY_LENGTH];
            byte[] valueBytes = new byte[VALUE_LENGTH];
            random.nextBytes(keyBytes);
            random.nextBytes(valueBytes);
            keys[i] = ByteBuffer.wrap(keyBytes);
            data.put(keys[i], valueBytes);
        }
        perfectHash = new PerfectHash<>(data);

        getExistingIndices = new int[RANDOM_INDEX_POOL_SIZE];
        for (int i = 0; i < RANDOM_INDEX_POOL_SIZE; i++) {
            getExistingIndices[i] = random.nextInt(entryCount);
        }
        getExistingCursor = 0;
    }

    @Benchmark
    public byte[] benchmarkGetExisting() {
        int i = getExistingIndices[getExistingCursor];
        getExistingCursor = (getExistingCursor + 1) % RANDOM_INDEX_POOL_SIZE;
        return perfectHash.get(keys[i]);
    }

    @Benchmark
    public PerfectHash<ByteBuffer, byte[]> benchmarkBuild() {
        Map<ByteBuffer, byte[]> data = new HashMap<>();
        for (int i = 0; i < entryCount; i++) {
            byte[] keyBytes = new byte[KEY_LENGTH];
            byte[] valueBytes = new byte[VALUE_LENGTH];
            random.nextBytes(keyBytes);
            random.nextBytes(valueBytes);
            data.put(ByteBuffer.wrap(keyBytes), valueBytes);
        }
        return new PerfectHash<>(data);
    }
}
