package com.ai.rag.utils;
import com.ai.rag.dto.GuardrailResult;

import java.util.Locale;
import java.util.regex.Pattern;

public final class InputGuardrail {
    private InputGuardrail() {}

    public static final int MIN_LEN = 4;
    public static final int MAX_LEN = 3000;

    // PII patterns
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_KR = Pattern.compile("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern RRN = Pattern.compile("\\b\\d{6}[- ]?\\d{7}\\b"); // 주민번호 형태(단순)
    private static final Pattern CARD_CANDIDATE = Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");

    // Prompt injection-ish
    private static final String[] INJECTION_TOKENS = {
            "ignore previous", "system prompt", "developer message", "system instruction",
            "이전 지시", "시스템 프롬프트", "시스템 지침", "개발자 메시지", "규칙을 무시",
            "role:", "### system", "### developer"
    };

    // Harmful content (MVP 최소 키워드; 실제론 더 체계적으로)
    private static final String[] SELF_HARM = {"자살", "자해", "죽고싶", "목숨", "극단적 선택"};
    private static final String[] VIOLENCE_GUIDE = {"폭탄", "제조법", "만드는 법", "칼로", "살인", "테러"};
    private static final String[] SEXUAL = {"성관계", "포르노", "야동", "강간", "성폭행"};
    private static final String[] HATE = {"혐오", "박멸", "열등", "죽여", "학살"};

    public static GuardrailResult validateAndSanitize(String q) {
        if (q == null) return GuardrailResult.block("question is required");
        String trimmed = q.trim();
        if (trimmed.isEmpty()) return GuardrailResult.block("question is required");
        if (trimmed.length() < MIN_LEN) return GuardrailResult.block("질문이 너무 짧습니다. 조금 더 구체적으로 입력해주세요.");
        if (trimmed.length() > MAX_LEN) return GuardrailResult.block("질문이 너무 깁니다. 핵심만 요약해서 다시 입력해주세요.");

        // 1) Prompt injection block
        if (looksLikePromptInjection(trimmed)) {
            return GuardrailResult.block("시스템 지침 변경/노출 요청은 처리할 수 없습니다. 문서 관련 질문만 입력해주세요.");
        }

        // 2) Harmful content block (MVP 최소)
        if (containsAny(trimmed, SELF_HARM) || containsAny(trimmed, VIOLENCE_GUIDE)
                || containsAny(trimmed, SEXUAL) || containsAny(trimmed, HATE)) {
            return GuardrailResult.block("유해하거나 부적절한 요청은 처리할 수 없습니다.");
        }

        // 3) PII sanitize (mask)
        String sanitized = trimmed;
        boolean redacted = false;

        if (EMAIL.matcher(sanitized).find()) {
            sanitized = EMAIL.matcher(sanitized).replaceAll("[REDACTED_EMAIL]");
            redacted = true;
        }
        if (PHONE_KR.matcher(sanitized).find()) {
            sanitized = PHONE_KR.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
            redacted = true;
        }
        if (RRN.matcher(sanitized).find()) {
            sanitized = RRN.matcher(sanitized).replaceAll("[REDACTED_RRN]");
            redacted = true;
        }
        if (CARD_CANDIDATE.matcher(sanitized).find()) {
            sanitized = CARD_CANDIDATE.matcher(sanitized).replaceAll("[REDACTED_CARD]");
            redacted = true;
        }

        if (redacted) {
            return GuardrailResult.redact(sanitized, "입력에서 민감정보를 마스킹했습니다.");
        }
        return GuardrailResult.allow(sanitized);
    }

    private static boolean looksLikePromptInjection(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String t : INJECTION_TOKENS) {
            if (x.contains(t.toLowerCase(Locale.ROOT))) score++;
        }
        // 오탐 줄이려고 2개 이상 매칭일 때만 block
        return score >= 2;
    }

    private static boolean containsAny(String s, String[] tokens) {
        for (String t : tokens) {
            if (t != null && !t.isBlank() && s.contains(t)) return true;
        }
        return false;
    }
}
