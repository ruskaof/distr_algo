package com.ruskaof.algorithm;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Set;

final class TestRandomUtils {

    private static final Random RANDOM = new Random(123456L);

    private TestRandomUtils() {
    }

    static byte[] randomBytes(int length) {
        byte[] b = new byte[length];
        RANDOM.nextBytes(b);
        return b;
    }

    static byte[] randomBytesInRange(int minLength, int maxExtra) {
        int length = minLength + RANDOM.nextInt(maxExtra + 1);
        return randomBytes(length);
    }

    static ByteBuffer randomUniqueKey(Set<ByteBuffer> existing, int length) {
        ByteBuffer key;
        do {
            key = ByteBuffer.wrap(randomBytes(length));
        } while (existing.contains(key));
        return key;
    }

    static double randomGaussian() {
        return RANDOM.nextGaussian();
    }

    static double randomDouble() {
        return RANDOM.nextDouble();
    }
}


