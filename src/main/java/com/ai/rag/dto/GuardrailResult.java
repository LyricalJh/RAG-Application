package com.ai.rag.dto;

public record GuardrailResult(Action action, String message, String normalizedQuestion) {
    public static GuardrailResult allow(String q) {
        return new GuardrailResult(Action.ALLOW, null, q);
    }
    public static GuardrailResult redact(String q, String msg) {
        return new GuardrailResult(Action.ALLOW_WITH_REDACTION, msg, q);
    }
    public static GuardrailResult block(String msg) {
        return new GuardrailResult(Action.BLOCK, msg, null);
    }
}