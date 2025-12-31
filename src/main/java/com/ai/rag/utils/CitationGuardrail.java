package com.ai.rag.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationGuardrail {
    private CitationGuardrail() {}

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    public static boolean isCitationValid(String answer, int maxIndex) {
        if (answer == null || answer.isBlank()) return false;

        Matcher m = CITATION.matcher(answer);
        boolean foundAny = false;

        while (m.find()) {
            foundAny = true;
            int idx = Integer.parseInt(m.group(1));
            if (idx < 1 || idx > maxIndex) {
                return false; // 범위 밖 근거번호
            }
        }

        // “근거 번호 반드시 포함” 정책이면 최소 1개 이상 존재해야 true
        return foundAny;
    }
}