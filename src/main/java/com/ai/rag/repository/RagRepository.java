package com.ai.rag.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RagRepository {

    private final JdbcTemplate jdbc;

    public RagRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertDocument(String title) {
        jdbc.update("INSERT INTO documents(title) VALUES (?)", title);
        return jdbc.queryForObject("SELECT currval('documents_id_seq')", Long.class);
    }

    public void insertChunk(long docId, int idx, String content, String vec) {
        jdbc.update("""
            INSERT INTO document_chunks(document_id, chunk_index, content, embedding)
            VALUES (?, ?, ?, ?::vector)
        """, docId, idx, content, vec);
    }

    public List<ChunkHit> searchTopK(String qVec, int k) {
        return jdbc.query("""
        SELECT id, document_id, chunk_index, content,
               (embedding <=> ?::vector) AS distance
        FROM document_chunks
        ORDER BY distance
        LIMIT ?
    """, (rs, n) -> {
            double distance = rs.getDouble("distance");
            double similarity = clamp(1.0 - distance, -1.0, 1.0); // cosine similarity 범위 고려
            return new ChunkHit(
                    rs.getLong("id"),
                    rs.getLong("document_id"),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    distance,
                    similarity
            );
        }, qVec, k);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public record ChunkHit(
            long id,
            long documentId,
            int chunkIndex,
            String content,
            double distance,
            double similarity
    ) {}

}
