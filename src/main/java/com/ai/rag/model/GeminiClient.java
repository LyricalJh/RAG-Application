package com.ai.rag.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GeminiClient(
            @Value("${gemini.apiKey}") String apiKey,
            @Value("${gemini.model}") String model
    ) {
        this.client = RestClient.create("https://generativelanguage.googleapis.com");
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generateAnswer(String systemInstruction, String prompt) {
        // 공식 스키마(GenerateContent): contents(parts(text)) :contentReference[oaicite:2]{index=2}
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(
                        Map.of("role", "user",
                                "parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 700
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> res = client.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return extractText(res);
    }

    private String extractText(Map<String, Object> res) {
        if (res == null) return "";

        Object candidatesObj = res.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) return "";

        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return "";

        Object contentObj = firstMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) return "";

        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) return "";

        Object part0 = parts.get(0);
        if (!(part0 instanceof Map<?, ?> part0Map)) return "";

        Object textObj = part0Map.get("text");
        return textObj == null ? "" : String.valueOf(textObj).trim();
    }
}
