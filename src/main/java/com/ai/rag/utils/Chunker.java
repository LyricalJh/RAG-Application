package com.ai.rag.utils;

import java.util.ArrayList;
import java.util.List;

public final class Chunker {

    private Chunker() {}

    public static List<String> chunkByChars(String text, int size, int overlap) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        String t = text.replace("\r\n", "\n").trim();
        int i = 0;
        while (i < t.length()) {
            int end = Math.min(i + size, t.length());
            String c = t.substring(i, end).trim();
            if (!c.isBlank()) out.add(c);
            if (end == t.length()) break;
            i = Math.max(0, end - overlap);
        }
        return out;
    }
}
