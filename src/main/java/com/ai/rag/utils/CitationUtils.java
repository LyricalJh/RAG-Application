package com.ai.rag.utils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CitationUtils {
    private CitationUtils() {}

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    /** answer 텍스트에서 [1], [2]... 를 순서 유지하며 추출 */
    public static Set<Integer> extractCitations(String text) {
        Set<Integer> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return out;

        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            out.add(idx);
        }
        return out;
    }

    /** 근거 섹션을 서버가 강제로 생성 */
    public static String buildEvidenceSection(Set<Integer> citations) {
        if (citations == null || citations.isEmpty()) return "(없음)";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Integer c : citations) {
            if (!first) sb.append(", ");
            sb.append("[").append(c).append("]");
            first = false;
        }
        return sb.toString();
    }
}
