package com.ruskaof.algorithm;

import java.util.Map;
import java.util.Random;

final class TestRandomUtils {

    private static final Random RANDOM = new Random(123456L);
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private TestRandomUtils() {
    }

    static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    static String randomStringInRange(int minLength, int maxExtra) {
        int length = minLength + RANDOM.nextInt(maxExtra + 1);
        return randomString(length);
    }

    static String randomUniqueKey(Map<String, ?> existing, int length) {
        String key;
        do {
            key = randomString(length);
        } while (existing.containsKey(key));
        return key;
    }

    static double randomGaussian() {
        return RANDOM.nextGaussian();
    }

    static double randomDouble() {
        return RANDOM.nextDouble();
    }
}


