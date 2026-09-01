package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.PolicySearchResult;
import com.itheima.policydailyagent.service.QianfanAiSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> qianfanDiscoverAndSave() {
        return ResponseEntity.badRequest().body(Map.of(
                "message",
                "该直写入口已停用。千帆仅用于搜索预览；候选入库请使用 /api/search-tasks/run，以确保搜索任务关联和人工审核链路完整。"
        ));
    }

}
