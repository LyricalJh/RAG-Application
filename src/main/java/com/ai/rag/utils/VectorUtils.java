package com.ai.rag.utils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class VectorUtils {

    public static final int DIM = 768;

    private VectorUtils() {}

    public static double[] embedLocal(String text) {
        double[] v = new double[DIM];
        if (text == null || text.isBlank()) return v;

        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-zA-Z가-힣\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) return v;

        String[] tokens = normalized.split(" ");
        for (String t : tokens) {
            if (t.isBlank()) continue;
            int idx = positiveMod(fnv1a32(t), DIM);
            v[idx] += 1.0;
        }

        double norm = 0.0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
        return v;
    }

    public static String toPgVectorLiteral(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static int fnv1a32(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int positiveMod(int x, int mod) {
        int r = x % mod;
        return r < 0 ? r + mod : r;
    }
}
