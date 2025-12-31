package com.ai.rag.controller;

import com.ai.rag.service.RagService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService service;

    public RagController(RagService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public RagController.IngestRes upload(@RequestPart("file") MultipartFile file) {
        long docId = service.ingestFile(file);
        return new RagController.IngestRes(docId);
    }

    @PostMapping("/ingest")
    public IngestRes ingest(@RequestBody IngestReq req) {
        return new IngestRes(service.ingest(req.title(), req.text()));
    }

    @PostMapping("/ask")
    public RagService.AskResponse ask(@RequestBody AskReq req) {
        return service.ask(req.question(), req.topK() == null ? 5 : req.topK(), req.maxDistance());
    }

    record IngestReq(String title, String text) {}
    record IngestRes(long documentId) {}
    record AskReq(String question, Integer topK, Double maxDistance) {}
}
