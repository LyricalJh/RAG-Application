package com.ai.rag.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerFormatter {
    private AnswerFormatter() {}

    private static final Pattern ANSWER_SECTION =
            Pattern.compile("(?s)-\\s*답변\\s*:\\s*(.*?)(?:\\n\\s*-\\s*근거\\s*:|$)");

    public static NormalizedAnswer normalize(String rawAnswer, String forcedEvidenceLine) {
        if (rawAnswer == null) rawAnswer = "";
        String trimmed = rawAnswer.trim();

        String answerText = extractAnswer(trimmed);
        if (answerText == null || answerText.isBlank()) {
            // 포맷을 안 지킨 경우: 전체를 답변으로 취급
            answerText = trimmed;
        }

        answerText = answerText.trim();

        String formatted = """
                - 답변:
                %s
                - 근거:
                %s
                """.formatted(answerText, forcedEvidenceLine == null ? "(없음)" : forcedEvidenceLine);

        return new NormalizedAnswer(answerText, formatted);
    }

    private static String extractAnswer(String s) {
        Matcher m = ANSWER_SECTION.matcher(s);
        if (!m.find()) return null;
        return m.group(1);
    }

    public record NormalizedAnswer(String answerText, String formatted) {}
}
