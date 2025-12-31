package com.ai.rag.service;

import com.ai.rag.dto.Action;
import com.ai.rag.dto.GuardrailResult;
import com.ai.rag.model.GeminiClient;
import com.ai.rag.repository.RagRepository;
import com.ai.rag.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final RagRepository repo;
    private final GeminiClient geminiClient;
    private final DocumentParser documentParser;

    private static final double DEFAULT_MAX_DISTANCE = 0.35;
    private static final int MAX_CONTEXT_CHARS = Integer.MAX_VALUE;

    public RagService(RagRepository repo, GeminiClient geminiClient, DocumentParser documentParser) {
        this.repo = repo;
        this.geminiClient = geminiClient;
        this.documentParser = documentParser;
    }

    public long ingest(String title, String text) {
        long docId = repo.insertDocument(title);

        List<String> chunks = Chunker.chunkByChars(text, 1200, 200);
        for (int i = 0; i < chunks.size(); i++) {
            double[] emb = VectorUtils.embedLocal(chunks.get(i));
            repo.insertChunk(docId, i, chunks.get(i), VectorUtils.toPgVectorLiteral(emb));
        }
        return docId;
    }

    public AskResponse ask(String q, int k, Double maxDistance) {

        final String 부족응답 = """
            - 답변:
            문서 근거가 부족합니다
            - 근거:
            (없음)
            """.trim();

        // ---------- Input Guardrail ----------
        GuardrailResult gr = InputGuardrail.validateAndSanitize(q);
        if (gr.action() == Action.BLOCK) {
            return new AskResponse(gr.message(), "", List.of());
        }
        String normalizedQ = gr.normalizedQuestion();

        // ---------- parameters ----------
        int topK = (k <= 0 ? 5 : k);
        double md = (maxDistance == null ? DEFAULT_MAX_DISTANCE : maxDistance);

        // ---------- retrieval ----------
        String qVec = VectorUtils.toPgVectorLiteral(VectorUtils.embedLocal(normalizedQ));
        List<RagRepository.ChunkHit> hits = repo.searchTopK(qVec, topK);

        List<RagRepository.ChunkHit> filtered = hits.stream()
                .filter(h -> h.distance() <= md)
                .toList();

        filtered = deduplicate(filtered);

        if (filtered.isEmpty()) {
            log.info("중복 제거 후 없는 컨텍스트가 존재하지 않아 부족 응답으로 결과를 냅니다. maxcdn_distance: {}, filtered_hits: {}", md, filtered);
            return new AskResponse(부족응답, "", filtered);
        }

        String ctx = buildContext(filtered, MAX_CONTEXT_CHARS);

        // ---------- prompt ----------
        String system = """
        너는 문서 근거 기반 Q&A 어시스턴트다.
        제공된 '문서 근거'에 없는 내용은 추측하지 말고, "문서 근거가 부족합니다"라고 답해라.
        출력 형식은 반드시 아래를 지켜라:
        - 답변:
        - 근거:
        """.trim();

        String prompt = """
        ### 문서 근거(TopK)
        %s

        ### 질문
        %s

        ### 출력 형식
        - 답변:
        - 근거:
        """.formatted(ctx, normalizedQ);

        // ---------- 1st generation ----------
        String rawAnswer = geminiClient.generateAnswer(system, prompt);

        log.info("첫번째 ai 모델 응답 : {}", rawAnswer);

        AnswerFormatter.NormalizedAnswer normalized = normalizeWithAutoEvidence(rawAnswer, filtered.size(), 부족응답, ctx, filtered);
        if (normalized == null) {
            return new AskResponse(부족응답, ctx, filtered);
        }

        // ---------- Quality gate + 1 retry (domain-neutral) ----------
        boolean needRetry = AnswerQuality.isLowQuality(normalized.answerText())
                || (AnswerQuality.expectsNumbers(normalizedQ) && !AnswerQuality.hasAnyDigit(normalized.answerText()));

        if (needRetry) {
            log.info("응답 퀄리티 가 좋지 않아 1회 재시도 합니다. input 질문내용 : {}", normalized);
            String retrySystem = """
            너는 문서 근거 기반 Q&A 어시스턴트다.
            제공된 문서 근거에서만 답하고, 근거에 없는 내용은 "문서 근거가 부족합니다"라고 답해라.
            답변은 구체적으로 작성하라(조건/절차/예외/수치가 있으면 포함).
            출력 형식은 반드시 아래를 지켜라:
            - 답변:
            - 근거:
            """.trim();

            String retryPrompt = """
            ### 문서 근거(TopK)
            %s

            ### 질문
            %s

            ### 작성 규칙(중요)
            1) 답변은 3~7개 bullet로 핵심만 정리해라.
            2) 문서에 숫자/조건/절차가 있으면 반드시 포함해라.
            3) 문서에 없으면 "문서 근거가 부족합니다"라고만 답해라.
            4) 출력 형식:
               - 답변:
               - 근거:
            """.formatted(ctx, normalizedQ);

            rawAnswer = geminiClient.generateAnswer(retrySystem, retryPrompt);

            log.info("재시도 후 AI 모델 첫번째 응답 : {}", rawAnswer);

            normalized = normalizeWithAutoEvidence(rawAnswer, filtered.size(), 부족응답, ctx, filtered);
            if (normalized == null) {
                log.info("근거범위에 도착하지 못해 부족응답으로 치부합니다. normalized: {}", normalized);
                return new AskResponse(부족응답, ctx, filtered);
            }

            // 재시도 후에도 품질이 너무 낮으면 fallback
            boolean stillBad = AnswerQuality.isLowQuality(normalized.answerText())
                    || (AnswerQuality.expectsNumbers(normalizedQ) && !AnswerQuality.hasAnyDigit(normalized.answerText()));
            if (stillBad) {
                log.warn("재시도 이후 에도 응답 품질이 좋지 않아 fallback 합니다/");
                return new AskResponse(부족응답, ctx, filtered);
            }
        }

        // ---------- consistency check ----------
        if (!EvidenceConsistency.isConsistent(normalized.answerText(), ctx)) {
            log.warn("consistency check 응답에 false 로 빠졌습니다.");
            return new AskResponse(부족응답, ctx, filtered);
        }

        return new AskResponse(normalized.formatted(), ctx, filtered);
    }

    /**
     * - 근거번호가 없으면 자동으로 [1] 부여 (auto evidence)
     * - 근거번호 범위 검증
     * - 서버가 근거 섹션 강제 생성
     * - 포맷 정규화
     *
     * 실패하면 null 반환
     */
    private AnswerFormatter.NormalizedAnswer normalizeWithAutoEvidence(
            String rawAnswer,
            int maxIndex,
            String 부족응답,
            String ctx,
            List<RagRepository.ChunkHit> filtered
    ) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return null;
        }

        Set<Integer> citations = CitationUtils.extractCitations(rawAnswer);

        // ✅ 자동 근거 보강: 없으면 Top1 근거로 처리
        if (citations.isEmpty()) {
            citations = Set.of(1);
        }

        // 근거번호 범위 검증
        boolean citationRangeOk = citations.stream().allMatch(i -> i >= 1 && i <= maxIndex);
        if (!citationRangeOk) {
            return null;
        }

        String forcedEvidence = CitationUtils.buildEvidenceSection(citations);
        return AnswerFormatter.normalize(rawAnswer, forcedEvidence);
    }

    public long ingestFile(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename(), "filename");
        String ext = getExtLower(filename);

        String text = switch (ext) {
            case "pdf" -> documentParser.extractPdf(file);
            case "docx" -> documentParser.extractDocx(file);
            case "hwpx" -> documentParser.extractHwpx(file);
            default -> throw new IllegalArgumentException("Unsupported file type: " + ext);
        };

        String title = stripExt(filename);
        return ingest(title, text); // 기존 ingest 재사용
    }

    private String getExtLower(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) {
            throw new IllegalArgumentException("파일 확장자가 없습니다: " + filename);
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) {
            return filename;
        }
        return filename.substring(0, dot);
    }

    private List<RagRepository.ChunkHit> deduplicate(List<RagRepository.ChunkHit> hits) {
        return hits.stream()
                .collect(Collectors.toMap(
                        h -> h.documentId() + "_" + h.chunkIndex(),
                        h -> h,
                        (a, b) -> a.distance() <= b.distance() ? a : b
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(RagRepository.ChunkHit::distance))
                .toList();
    }

    private String buildContext(List<RagRepository.ChunkHit> hits, int maxChars) {
        StringBuilder ctx = new StringBuilder();

        for (int i = 0; i < hits.size(); i++) {
            RagRepository.ChunkHit h = hits.get(i);

            String block = """
            [%d] (doc=%d, chunk=%d, dist=%.4f, sim=%.4f)
            %s

            """.formatted(
                    i + 1,
                    h.documentId(),
                    h.chunkIndex(),
                    h.distance(),
                    h.similarity(),
                    h.content()
            );

            if (ctx.length() + block.length() > maxChars) break;
            ctx.append(block);
        }
        return ctx.toString();
    }


    public record AskResponse(String answer, String context, List<RagRepository.ChunkHit> sources) {}
}
