package com.itheima.policydailyagent.service;

import com.itheima.policydailyagent.domain.report.MonthlyReport;
import com.itheima.policydailyagent.domain.report.MonthlyReportItem;
import com.itheima.policydailyagent.domain.report.MonthlyReportItemRevision;
import com.itheima.policydailyagent.domain.report.MonthlyReportStatus;
import com.itheima.policydailyagent.domain.report.ReportItemStatus;
import com.itheima.policydailyagent.domain.report.ReportSection;
import com.itheima.policydailyagent.dto.MonthlyReportActionRequest;
import com.itheima.policydailyagent.dto.MonthlyReportEditorView;
import com.itheima.policydailyagent.dto.MonthlyReportItemTraceView;
import com.itheima.policydailyagent.dto.ReorderReportSectionRequest;
import com.itheima.policydailyagent.dto.UpdateMonthlyReportItemRequest;
import com.itheima.policydailyagent.repository.MonthlyReportItemRepository;
import com.itheima.policydailyagent.repository.MonthlyReportItemRevisionRepository;
import com.itheima.policydailyagent.repository.MonthlyReportItemSourceRepository;
import com.itheima.policydailyagent.repository.MonthlyReportRepository;
import com.itheima.policydailyagent.repository.ReportSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonthlyReportEditingService {

    private final MonthlyReportRepository reportRepository;
    private final ReportSectionRepository sectionRepository;
    private final MonthlyReportItemRepository itemRepository;
    private final MonthlyReportItemRevisionRepository revisionRepository;
    private final MonthlyReportItemSourceRepository sourceRepository;

    public MonthlyReportEditingService(
            MonthlyReportRepository reportRepository,
            ReportSectionRepository sectionRepository,
            MonthlyReportItemRepository itemRepository,
            MonthlyReportItemRevisionRepository revisionRepository,
            MonthlyReportItemSourceRepository sourceRepository
    ) {
        this.reportRepository = reportRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
        this.revisionRepository = revisionRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyReportEditorView getEditor(Long reportId) {
        return buildEditor(requireReport(reportId));
    }

    @Transactional(readOnly = true)
    public MonthlyReportItemTraceView getTrace(Long reportId, Long itemId) {
        MonthlyReportItem item = requireItem(reportId, itemId);
        Map<Long, String> sectionCodes = sectionRepository.findAll().stream()
                .collect(Collectors.toMap(ReportSection::getId, ReportSection::getSectionCode));
        return new MonthlyReportItemTraceView(
                reportId,
                itemId,
                item.getPolicyId(),
                item.getAnalysisId(),
                item.getAgentDraft(),
                sourceRepository.findByReportItemIdOrderByIdAsc(itemId).stream()
                        .map(source -> new MonthlyReportItemTraceView.SourceTraceView(
                                source.getId(), source.getSourceType(), source.getPolicyId(),
                                source.getSourceTitle(), source.getSourceUrl(),
                                source.getSourceContentSnapshot(), source.getCreatedAt()
                        )).toList(),
                revisionViews(itemId, sectionCodes)
        );
    }

    @Transactional
    public MonthlyReportEditorView updateItem(
            Long reportId,
            Long itemId,
            UpdateMonthlyReportItemRequest request
    ) {
        MonthlyReport report = requireDraftReport(reportId);
        MonthlyReportItem item = requireItem(reportId, itemId);
        if (item.getStatus() == ReportItemStatus.REMOVED) {
            throw new IllegalArgumentException("已删除条目不能直接编辑，请先恢复");
        }
        if (item.getLockVersion() != request.lockVersion()) {
            throw new IllegalArgumentException("该条目已被其他操作更新，请刷新后再编辑");
        }

        ReportSection section = requireWritableSection(request.sectionCode());
        boolean changed = !Objects.equals(item.getSectionId(), section.getId())
                || !Objects.equals(item.getItemTitle(), request.itemTitle().trim())
                || !Objects.equals(item.getFinalContent(), request.finalContent().trim());
        if (!changed) {
            return buildEditor(report);
        }

        if (!Objects.equals(item.getSectionId(), section.getId())) {
            item.setSectionId(section.getId());
            item.setSortOrder(nextSortOrder(reportId, section.getId()));
        }
        item.setItemTitle(request.itemTitle().trim());
        item.setFinalContent(request.finalContent().trim());
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setUpdatedBy(request.editor().trim());
        item = itemRepository.saveAndFlush(item);
        saveRevision(item, "EDITED", "人工编辑月报标题、正文或所属栏目", request.editor());
        return buildEditor(report);
    }

    @Transactional
    public MonthlyReportEditorView reorderSection(
            Long reportId,
            String sectionCode,
            ReorderReportSectionRequest request
    ) {
        MonthlyReport report = requireDraftReport(reportId);
        ReportSection section = requireWritableSection(sectionCode);
        List<MonthlyReportItem> items = itemRepository
                .findByReportIdAndSectionIdOrderBySortOrderAsc(reportId, section.getId()).stream()
                .filter(item -> item.getStatus() != ReportItemStatus.REMOVED)
                .toList();
        Set<Long> expectedIds = items.stream().map(MonthlyReportItem::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> requestedIds = new LinkedHashSet<>(request.orderedItemIds());
        if (requestedIds.size() != request.orderedItemIds().size() || !requestedIds.equals(expectedIds)) {
            throw new IllegalArgumentException("排序列表必须完整且不能包含重复或其他栏目的条目");
        }

        Map<Long, MonthlyReportItem> byId = items.stream()
                .collect(Collectors.toMap(MonthlyReportItem::getId, Function.identity()));
        for (int index = 0; index < request.orderedItemIds().size(); index++) {
            MonthlyReportItem item = byId.get(request.orderedItemIds().get(index));
            int newOrder = (index + 1) * 10;
            if (item.getSortOrder() != newOrder) {
                item.setSortOrder(newOrder);
                item.setUpdatedBy(request.editor().trim());
                item = itemRepository.saveAndFlush(item);
                saveRevision(item, "REORDERED", "人工调整栏目内顺序", request.editor());
            }
        }
        return buildEditor(report);
    }

    @Transactional
    public MonthlyReportEditorView removeItem(
            Long reportId,
            Long itemId,
            MonthlyReportActionRequest request
    ) {
        MonthlyReport report = requireDraftReport(reportId);
        MonthlyReportItem item = requireItem(reportId, itemId);
        if (item.getStatus() == ReportItemStatus.REMOVED) {
            return buildEditor(report);
        }
        item.setStatus(ReportItemStatus.REMOVED);
        item.setUpdatedBy(request.operator().trim());
        item = itemRepository.saveAndFlush(item);
        saveRevision(item, "REMOVED", note("人工从月报内容池删除条目", request.reason()), request.operator());
        return buildEditor(report);
    }

    @Transactional
    public MonthlyReportEditorView restoreItem(
            Long reportId,
            Long itemId,
            MonthlyReportActionRequest request
    ) {
        MonthlyReport report = requireDraftReport(reportId);
        MonthlyReportItem item = requireItem(reportId, itemId);
        if (item.getStatus() != ReportItemStatus.REMOVED) {
            return buildEditor(report);
        }
        requireWritableSectionById(item.getSectionId());
        item.setStatus(ReportItemStatus.CONFIRMED);
        item.setSortOrder(nextSortOrder(reportId, item.getSectionId()));
        item.setUpdatedBy(request.operator().trim());
        item = itemRepository.saveAndFlush(item);
        saveRevision(item, "RESTORED", note("人工恢复月报内容条目", request.reason()), request.operator());
        return buildEditor(report);
    }

    @Transactional
    public MonthlyReportEditorView confirmContent(Long reportId, MonthlyReportActionRequest request) {
        MonthlyReport report = requireReport(reportId);
        if (report.getStatus() == MonthlyReportStatus.CONTENT_CONFIRMED) {
            return buildEditor(report);
        }
        if (report.getStatus() != MonthlyReportStatus.DRAFT) {
            throw new IllegalArgumentException("当前月报状态不能确认内容，请先重新打开编辑");
        }
        List<String> errors = validationErrors(
                itemRepository.findByReportIdOrderBySectionIdAscSortOrderAsc(reportId),
                sectionRepository.findByActiveTrueOrderBySortOrderAsc()
        );
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("月报内容尚不能确认：" + String.join("；", errors));
        }
        report.setStatus(MonthlyReportStatus.CONTENT_CONFIRMED);
        report.setContentConfirmedBy(request.operator().trim());
        report.setContentConfirmedAt(LocalDateTime.now());
        report = reportRepository.saveAndFlush(report);
        return buildEditor(report);
    }

    @Transactional
    public MonthlyReportEditorView reopen(Long reportId, MonthlyReportActionRequest request) {
        MonthlyReport report = requireReport(reportId);
        if (report.getStatus() == MonthlyReportStatus.ARCHIVED) {
            throw new IllegalArgumentException("已归档月报不能重新编辑");
        }
        if (report.getStatus() == MonthlyReportStatus.DRAFT) {
            return buildEditor(report);
        }
        report.setStatus(MonthlyReportStatus.DRAFT);
        report.setContentConfirmedBy(null);
        report.setContentConfirmedAt(null);
        report.setGeneratedAt(null);
        report.setOutputFileName(null);
        report.setOutputHash(null);
        report.setTemplateVersion(null);
        report.setTemplateHash(null);
        report = reportRepository.saveAndFlush(report);
        return buildEditor(report);
    }

    private MonthlyReportEditorView buildEditor(MonthlyReport report) {
        List<ReportSection> sections = sectionRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<Long, String> sectionCodes = sections.stream()
                .collect(Collectors.toMap(ReportSection::getId, ReportSection::getSectionCode));
        List<MonthlyReportItem> allItems = itemRepository
                .findByReportIdOrderBySectionIdAscSortOrderAsc(report.getId());

        Map<Long, List<MonthlyReportEditorView.ItemView>> activeBySection = new HashMap<>();
        List<MonthlyReportEditorView.ItemView> removed = new ArrayList<>();
        for (MonthlyReportItem item : allItems) {
            MonthlyReportEditorView.ItemView view = itemView(item, sectionCodes);
            if (item.getStatus() == ReportItemStatus.REMOVED) {
                removed.add(view);
            } else {
                activeBySection.computeIfAbsent(item.getSectionId(), ignored -> new ArrayList<>()).add(view);
            }
        }

        List<MonthlyReportEditorView.SectionView> sectionViews = sections.stream()
                .map(section -> new MonthlyReportEditorView.SectionView(
                        section.getId(), section.getSectionCode(), section.getParentId(),
                        section.getSectionName(), section.getSectionLevel(), section.getSortOrder(),
                        section.getWritingGuide(), writable(section),
                        activeBySection.getOrDefault(section.getId(), List.of())
                )).toList();
        List<String> errors = validationErrors(allItems, sections);
        int activeItemCount = (int) allItems.stream()
                .filter(item -> item.getStatus() != ReportItemStatus.REMOVED)
                .count();
        return new MonthlyReportEditorView(
                report.getId(), report.getReportYear(), report.getReportMonth(), report.getTitle(),
                report.getStatus(), report.getContentConfirmedBy(), report.getContentConfirmedAt(),
                report.getGeneratedAt(), report.getOutputFileName(), report.getOutputHash(),
                report.getTemplateVersion(), report.getTemplateHash(), report.getLockVersion(),
                activeItemCount, errors.isEmpty(), errors,
                sectionViews, removed
        );
    }

    private MonthlyReportEditorView.ItemView itemView(
            MonthlyReportItem item,
            Map<Long, String> sectionCodes
    ) {
        List<MonthlyReportEditorView.SourceView> sources = sourceRepository
                .findByReportItemIdOrderByIdAsc(item.getId()).stream()
                .map(source -> new MonthlyReportEditorView.SourceView(
                        source.getId(), source.getSourceType(), source.getPolicyId(),
                        source.getSourceTitle(), source.getSourceUrl(),
                        source.getSourceContentSnapshot() == null ? 0 : source.getSourceContentSnapshot().length()
                )).toList();
        return new MonthlyReportEditorView.ItemView(
                item.getId(), item.getSectionId(), sectionCodes.get(item.getSectionId()),
                item.getPolicyId(), item.getAnalysisId(), item.getSourceType(), item.getItemTitle(),
                item.getAgentDraft(), item.getFinalContent(), item.getStatus(), item.getSortOrder(),
                item.getCreatedBy(), item.getUpdatedBy(), item.getLockVersion(), item.getCreatedAt(),
                item.getUpdatedAt(), sources, revisionViews(item.getId(), sectionCodes)
        );
    }

    private List<MonthlyReportEditorView.RevisionView> revisionViews(
            Long itemId,
            Map<Long, String> sectionCodes
    ) {
        return revisionRepository.findByReportItemIdOrderByRevisionNoDesc(itemId).stream()
                .map(revision -> new MonthlyReportEditorView.RevisionView(
                        revision.getId(), revision.getRevisionNo(), revision.getItemTitle(),
                        revision.getSectionId(), sectionCodes.get(revision.getSectionId()),
                        revision.getItemStatus(), revision.getSortOrder() == null ? 0 : revision.getSortOrder(),
                        revision.getContent(), revision.getChangeType(), revision.getChangeNote(),
                        revision.getEditor(), revision.getCreatedAt()
                )).toList();
    }

    private List<String> validationErrors(
            List<MonthlyReportItem> allItems,
            List<ReportSection> sections
    ) {
        List<MonthlyReportItem> activeItems = allItems.stream()
                .filter(item -> item.getStatus() != ReportItemStatus.REMOVED)
                .toList();
        if (activeItems.isEmpty()) {
            return List.of("至少需要一条人工确认的月报内容");
        }
        Map<Long, ReportSection> sectionsById = sections.stream()
                .collect(Collectors.toMap(ReportSection::getId, Function.identity()));
        List<String> errors = new ArrayList<>();
        for (MonthlyReportItem item : activeItems) {
            String label = "条目#" + item.getId();
            if (item.getStatus() != ReportItemStatus.CONFIRMED) {
                errors.add(label + "尚未完成人工确认");
            }
            if (!hasText(item.getItemTitle())) {
                errors.add(label + "标题为空");
            }
            if (!hasText(item.getFinalContent())) {
                errors.add(label + "正文为空");
            }
            ReportSection section = sectionsById.get(item.getSectionId());
            if (section == null || !writable(section)) {
                errors.add(label + "没有可写入 Word 的有效栏目");
            }
        }
        return errors;
    }

    private MonthlyReport requireReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("月报不存在，id=" + reportId));
    }

    private MonthlyReport requireDraftReport(Long reportId) {
        MonthlyReport report = requireReport(reportId);
        if (report.getStatus() != MonthlyReportStatus.DRAFT) {
            throw new IllegalArgumentException("月报已整月确认或生成，请先重新打开编辑");
        }
        return report;
    }

    private MonthlyReportItem requireItem(Long reportId, Long itemId) {
        MonthlyReportItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("月报条目不存在，id=" + itemId));
        if (!Objects.equals(item.getReportId(), reportId)) {
            throw new IllegalArgumentException("月报条目不属于当前月报");
        }
        return item;
    }

    private ReportSection requireWritableSection(String sectionCode) {
        ReportSection section = sectionRepository.findBySectionCode(sectionCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("月报栏目不存在：" + sectionCode));
        if (!writable(section)) {
            throw new IllegalArgumentException("该栏目是结构容器，不能直接写入内容：" + sectionCode);
        }
        return section;
    }

    private ReportSection requireWritableSectionById(Long sectionId) {
        ReportSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("月报栏目不存在，id=" + sectionId));
        if (!writable(section)) {
            throw new IllegalArgumentException("原栏目已停用或不能写入，恢复前请先调整栏目");
        }
        return section;
    }

    private boolean writable(ReportSection section) {
        return section.isActive() && hasText(section.getContentPlaceholder());
    }

    private int nextSortOrder(Long reportId, Long sectionId) {
        return itemRepository.findTopByReportIdAndSectionIdOrderBySortOrderDesc(reportId, sectionId)
                .map(item -> item.getSortOrder() + 10)
                .orElse(10);
    }

    private void saveRevision(
            MonthlyReportItem item,
            String changeType,
            String changeNote,
            String editor
    ) {
        MonthlyReportItemRevision revision = new MonthlyReportItemRevision();
        revision.setReportItemId(item.getId());
        revision.setRevisionNo(revisionRepository
                .findTopByReportItemIdOrderByRevisionNoDesc(item.getId())
                .map(existing -> existing.getRevisionNo() + 1)
                .orElse(1));
        revision.setItemTitle(item.getItemTitle());
        revision.setSectionId(item.getSectionId());
        revision.setItemStatus(item.getStatus());
        revision.setSortOrder(item.getSortOrder());
        revision.setContent(item.getFinalContent() == null ? "" : item.getFinalContent());
        revision.setChangeType(changeType);
        revision.setChangeNote(changeNote);
        revision.setEditor(editor.trim());
        revisionRepository.save(revision);
    }

    private String note(String action, String reason) {
        return hasText(reason) ? action + "：" + reason.trim() : action;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
