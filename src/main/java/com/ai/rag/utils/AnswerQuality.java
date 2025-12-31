package com.ai.rag.utils;

public final class AnswerQuality {
    private AnswerQuality() {}

    public static boolean isLowQuality(String answerText) {
        if (answerText == null) return true;
        String t = answerText.trim();

        // 너무 짧으면 정보 부족
        if (t.length() < 60) return true;

        // 별표/불릿만 있고 내용이 없을 때
        String compact = t.replaceAll("\\s+", " ");
        if (compact.equals("*") || compact.equals("-") || compact.equals("•")) return true;

        // "다음과 같습니다" 같은 서론만 있고 뒤에 실내용이 거의 없을 때
        if ((t.contains("다음과 같습니다") || t.contains("다음과 같") || t.contains("아래와 같"))
                && t.length() < 120) {
            return true;
        }

        // 문장이 미완성으로 끝나기 쉬운 패턴(너가 겪은 "다릅" 등)
        if (t.endsWith("다릅") || t.endsWith("다르") || t.endsWith("있") || t.endsWith("없")) return true;

        return false;
    }

    /** 질문에 숫자/기간/금액 힌트가 있는 경우, 답변에도 숫자가 없으면 품질 미달로 간주(조건부) */
    public static boolean expectsNumbers(String question) {
        if (question == null) return false;
        String q = question;
        return q.matches(".*\\d+.*") // 질문 자체에 숫자 포함
                || q.contains("얼마") || q.contains("몇") || q.contains("한도") || q.contains("금액")
                || q.contains("기간") || q.contains("일") || q.contains("년") || q.contains("월")
                || q.contains("%") || q.contains("비율");
    }

    public static boolean hasAnyDigit(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) return true;
        }
        return false;
    }
}
