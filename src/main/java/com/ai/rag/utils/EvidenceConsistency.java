package com.ai.rag.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvidenceConsistency {
    private EvidenceConsistency() {}

    private static final Pattern NUMBERS = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final String[] STRONG_ASSERTIONS = {"무조건", "반드시", "절대", "항상", "확실히"};

    public static boolean isConsistent(String answerText, String ctx) {
        if (answerText == null || answerText.isBlank()) return false;
        if (ctx == null) ctx = "";

        String a = answerText.trim();
        String c = ctx;

        // 모델이 스스로 부족하다고 말하면 OK(최종적으로도 부족 처리로 가도 됨)
        if (containsAny(a, "문서 근거가 부족", "알 수 없", "모르겠", "정보가 없", "제공된 문서")) {
            return true;
        }

        // 1) 숫자 검증: 답변에 숫자가 있으면 ctx에도 최소 1개 숫자 매칭이 있어야 함
        Set<String> numsInAnswer = extractNumbers(a);
        if (!numsInAnswer.isEmpty()) {
            Set<String> numsInCtx = extractNumbers(c);

            boolean anyMatch = false;
            for (String n : numsInAnswer) {
                if (numsInCtx.contains(n)) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) return false;
        }

        // 2) 강한 단정어 + ctx 너무 짧으면 위험 (오탐 방지로 ctx 길이만 체크)
        if (containsAny(a, STRONG_ASSERTIONS) && c.length() < 200) {
            return false;
        }

        return true;
    }

    private static Set<String> extractNumbers(String s) {
        Set<String> out = new HashSet<>();
        Matcher m = NUMBERS.matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static boolean containsAny(String s, String... tokens) {
        for (String t : tokens) {
            if (t != null && !t.isBlank() && s.contains(t)) return true;
        }
        return false;
    }
}
