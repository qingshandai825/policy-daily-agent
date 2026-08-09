package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicyCrawlRequest;
import com.itheima.policydailyagent.dto.PolicyCrawlResult;
import com.itheima.policydailyagent.dto.PolicyDocumentCreateRequest;
import com.itheima.policydailyagent.entity.PolicyDocument;
import com.itheima.policydailyagent.service.PolicyCrawlerService;
import com.itheima.policydailyagent.service.PolicyDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
public class PolicyCrawlerController {

    private final PolicyCrawlerService policyCrawlerService;
    private final PolicyDocumentService policyDocumentService;

    public PolicyCrawlerController(
            PolicyCrawlerService policyCrawlerService,
            PolicyDocumentService policyDocumentService
    ) {
        this.policyCrawlerService = policyCrawlerService;
        this.policyDocumentService = policyDocumentService;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@Valid @RequestBody PolicyCrawlRequest request) {
        try {
            PolicyCrawlResult result = policyCrawlerService.crawl(request.url());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/fetch-and-save")
    public ResponseEntity<?> fetchAndSave(@Valid @RequestBody PolicyCrawlRequest request) {
        try {
            PolicyCrawlResult crawlResult = policyCrawlerService.crawl(request.url());

            PolicyDocumentCreateRequest createRequest = new PolicyDocumentCreateRequest(
                    crawlResult.title(),
                    crawlResult.sourceName(),
                    crawlResult.publishDate(),
                    crawlResult.sourceUrl(),
                    crawlResult.content(),
                    "????",
                    null,
                    crawlResult.retrievedAt(),
                    crawlResult.sourceDomain(),
                    crawlResult.sourceType(),
                    crawlResult.authorityLevel(),
                    crawlResult.dateSource(),
                    crawlResult.dateText(),
                    crawlResult.dateConfidence(),
                    crawlResult.contentHash(),
                    crawlResult.evidenceSnippet(),
                    "ACCEPTED",
                    "Manual URL fetch."
            );

            PolicyDocument savedDocument = policyDocumentService.createPolicyDocument(createRequest);

            return ResponseEntity.ok(savedDocument);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}