package com.itheima.policydailyagent.controller;

import com.itheima.policydailyagent.dto.CreateMonthlyReportRequest;
import com.itheima.policydailyagent.dto.MonthlyReportActionRequest;
import com.itheima.policydailyagent.dto.ReorderReportSectionRequest;
import com.itheima.policydailyagent.dto.UpdateMonthlyReportItemRequest;
import com.itheima.policydailyagent.service.MonthlyReportEditingService;
import com.itheima.policydailyagent.service.MonthlyReportWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/monthly-reports")
public class MonthlyReportWorkspaceController {

    private final MonthlyReportWorkspaceService workspaceService;
    private final MonthlyReportEditingService editingService;

    public MonthlyReportWorkspaceController(
            MonthlyReportWorkspaceService workspaceService,
            MonthlyReportEditingService editingService
    ) {
        this.workspaceService = workspaceService;
        this.editingService = editingService;
    }

    @PostMapping
    public ResponseEntity<?> createOrGet(@Valid @RequestBody CreateMonthlyReportRequest request) {
        return ResponseEntity.ok(workspaceService.createOrGet(request));
    }

    @GetMapping
    public ResponseEntity<?> listRecent() {
        return ResponseEntity.ok(workspaceService.listRecent());
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<?> getWorkspace(@PathVariable Long reportId) {
        try {
            return ResponseEntity.ok(workspaceService.getWorkspace(reportId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{reportId}/editor")
    public ResponseEntity<?> getEditor(@PathVariable Long reportId) {
        return execute(() -> editingService.getEditor(reportId));
    }

    @GetMapping("/{reportId}/items/{itemId}/trace")
    public ResponseEntity<?> getItemTrace(@PathVariable Long reportId, @PathVariable Long itemId) {
        return execute(() -> editingService.getTrace(reportId, itemId));
    }

    @PutMapping("/{reportId}/items/{itemId}")
    public ResponseEntity<?> updateItem(
            @PathVariable Long reportId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateMonthlyReportItemRequest request
    ) {
        return execute(() -> editingService.updateItem(reportId, itemId, request));
    }

    @PostMapping("/{reportId}/sections/{sectionCode}/reorder")
    public ResponseEntity<?> reorderSection(
            @PathVariable Long reportId,
            @PathVariable String sectionCode,
            @Valid @RequestBody ReorderReportSectionRequest request
    ) {
        return execute(() -> editingService.reorderSection(reportId, sectionCode, request));
    }

    @PostMapping("/{reportId}/items/{itemId}/remove")
    public ResponseEntity<?> removeItem(
            @PathVariable Long reportId,
            @PathVariable Long itemId,
            @Valid @RequestBody MonthlyReportActionRequest request
    ) {
        return execute(() -> editingService.removeItem(reportId, itemId, request));
    }

    @PostMapping("/{reportId}/items/{itemId}/restore")
    public ResponseEntity<?> restoreItem(
            @PathVariable Long reportId,
            @PathVariable Long itemId,
            @Valid @RequestBody MonthlyReportActionRequest request
    ) {
        return execute(() -> editingService.restoreItem(reportId, itemId, request));
    }

    @PostMapping("/{reportId}/confirm-content")
    public ResponseEntity<?> confirmContent(
            @PathVariable Long reportId,
            @Valid @RequestBody MonthlyReportActionRequest request
    ) {
        return execute(() -> editingService.confirmContent(reportId, request));
    }

    @PostMapping("/{reportId}/reopen")
    public ResponseEntity<?> reopen(
            @PathVariable Long reportId,
            @Valid @RequestBody MonthlyReportActionRequest request
    ) {
        return execute(() -> editingService.reopen(reportId, request));
    }

    private ResponseEntity<?> execute(java.util.function.Supplier<?> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
