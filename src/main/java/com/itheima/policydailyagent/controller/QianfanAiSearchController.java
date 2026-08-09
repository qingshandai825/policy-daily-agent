package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicySearchResult;
import com.itheima.policydailyagent.service.QianfanAiSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class QianfanAiSearchController {

    private final QianfanAiSearchService qianfanAiSearchService;

    public QianfanAiSearchController(QianfanAiSearchService qianfanAiSearchService) {
        this.qianfanAiSearchService = qianfanAiSearchService;
    }

    @PostMapping("/qianfan-preview")
    public ResponseEntity<?> qianfanPreview(@RequestBody Map<String, Object> request) {
        try {
            String query = String.valueOf(request.get("query"));

            Integer maxResults = null;
            if (request.get("maxResults") != null) {
                maxResults = Integer.valueOf(String.valueOf(request.get("maxResults")));
            }

            List<String> sites = List.of();

            Object sitesObj = request.get("sites");
            if (sitesObj instanceof List<?> rawList) {
                sites = rawList.stream()
                        .map(String::valueOf)
                        .toList();
            }

            PolicySearchResult result = qianfanAiSearchService.searchPreview(
                    query,
                    sites,
                    maxResults
            );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/qianfan-discover-and-save")
    public ResponseEntity<?> qianfanDiscoverAndSave(@RequestBody Map<String, Object> request) {
        try {
            String query = String.valueOf(request.get("query"));

            Integer maxResults = null;
            if (request.get("maxResults") != null) {
                maxResults = Integer.valueOf(String.valueOf(request.get("maxResults")));
            }

            Boolean autoSummarize = false;
            if (request.get("autoSummarize") != null) {
                autoSummarize = Boolean.valueOf(String.valueOf(request.get("autoSummarize")));
            }

            LocalDate targetStartDate = parseDate(request.get("targetStartDate"));
            LocalDate targetEndDate = parseDate(request.get("targetEndDate"));

            List<String> sites = List.of();

            Object sitesObj = request.get("sites");
            if (sitesObj instanceof List<?> rawList) {
                sites = rawList.stream()
                        .map(String::valueOf)
                        .toList();
            }

            PolicySearchResult result = qianfanAiSearchService.searchAndSave(
                    query,
                    sites,
                    maxResults,
                    autoSummarize,
                    targetStartDate,
                    targetEndDate
            );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }


    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }

        return LocalDate.parse(text);
    }
}
