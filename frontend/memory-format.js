/**
 * 任务执行上下文面板的纯函数集合（无 DOM、无网络依赖）。
 * 以 UMD 方式导出：浏览器下挂到 window.MemoryFormat，Node 下通过 module.exports 供单测复用。
 * 仅做展示层映射与防御式解析，不包含任何业务副作用。
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.MemoryFormat = factory();
    }
}(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    var EVENT_TYPE_TEXT = {
        TASK_STARTED: '任务开始',
        SOURCE_SEARCHED: '信源搜索完成',
        SOURCE_FAILED: '信源搜索失败',
        ROUND_PLANNED: '本轮计划生成',
        ROUND_STARTED: '本轮搜索开始',
        COVERAGE_EVALUATED: '主题覆盖度评估',
        SEARCH_FEEDBACK_EVALUATED: '材料缺口与术语反馈',
        ROUND_COMPLETED: '本轮搜索完成',
        TASK_COMPLETED: '任务完成',
        TASK_FAILED: '任务失败'
    };

    var PHASE_TEXT = {
        INITIALIZED: '初始化',
        SEARCHING: '搜索中',
        COMPLETED: '已完成',
        FAILED: '失败'
    };

    var MEMORY_STATUS_TEXT = {
        ACTIVE: '进行中',
        COMPLETED: '已完成',
        FAILED: '失败'
    };

    var TASK_TYPE_TEXT = {
        POLICY_SEARCH: '政策搜索',
        POLICY_ANALYSIS: '政策分析',
        REPORT_WRITING: '月报生成'
    };

    function text(map, value, fallback) {
        if (value == null || value === '') return fallback || '';
        return Object.prototype.hasOwnProperty.call(map, value) ? map[value] : value;
    }

    function eventTypeText(value) { return text(EVENT_TYPE_TEXT, value, value); }
    function phaseText(value) { return text(PHASE_TEXT, value, value); }
    function memoryStatusText(value) { return text(MEMORY_STATUS_TEXT, value, value); }
    function taskTypeText(value) { return text(TASK_TYPE_TEXT, value, value); }

    // 安全解析 JSON：null/空串/非法 JSON 一律返回 null，绝不抛异常。
    function safeParseJson(json) {
        if (json == null) return null;
        var s = String(json).trim();
        if (s === '') return null;
        try {
            return JSON.parse(s);
        } catch (e) {
            return null;
        }
    }

    // 空值兜底：null/undefined/空串/空数组 → 指定占位文案；其余原样返回。
    function emptyOr(value, fallback) {
        var f = (fallback == null) ? '暂无' : fallback;
        if (value == null) return f;
        if (typeof value === 'string' && value.trim() === '') return f;
        if (Array.isArray(value) && value.length === 0) return f;
        return value;
    }

    // ISO 时间串 → 可读显示：'2026-08-01T10:30:00.123456' → '2026-08-01 10:30:00'。
    function fmtDateTime(iso) {
        if (iso == null || iso === '') return '未记录';
        return String(iso).replace('T', ' ').replace(/\.\d+$/, '');
    }

    // 数字兜底：null/undefined/NaN → 0。
    function num(value) {
        var n = (value == null) ? 0 : Number(value);
        return Number.isFinite(n) ? n : 0;
    }

    // 事件输入的关键字段摘要；无法识别时返回 null（由调用方决定是否隐藏）。
    function summarizeEventInput(eventType, json) {
        var obj = safeParseJson(json);
        if (!obj) return null;
        if (eventType === 'SOURCE_SEARCHED' || eventType === 'SOURCE_FAILED') {
            return '信源：' + (obj.sourceName || obj.sourceId || '未记录');
        }
        return null;
    }

    // 事件输出的关键字段摘要；无法识别时返回 null。
    function summarizeEventOutput(eventType, json) {
        var obj = safeParseJson(json);
        if (!obj) return null;
        if (eventType === 'SEARCH_FEEDBACK_EVALUATED') {
            var gaps = (obj.coverage || []).filter(function (c) { return c.status !== 'COVERED'; })
                .map(function (c) { return c.topic; });
            var a = obj.materialAssessment;
            if (a && Array.isArray(a.validationIssues) && a.validationIssues.length === 0) gaps = a.missingTopics || [];
            var terms = (obj.terms || []).map(function (t) { return t.term; });
            var assessment = summarizeAssessment(obj);
            return (assessment ? assessment + ' · ' : '') + '待补主题：' + (gaps.join('、') || '暂无') + ' · 原文术语：' +
                (terms.join('、') || '暂无') + ' · ' + (obj.note || '');
        }
        if (eventType === 'SOURCE_FAILED' || eventType === 'TASK_FAILED') {
            return '错误：' + (obj.error || '未记录');
        }
        if (eventType === 'ROUND_COMPLETED' || eventType === 'TASK_COMPLETED') {
            return '发现 ' + num(obj.found) + ' · 保存 ' + num(obj.saved) +
                ' · 重复 ' + num(obj.duplicate) + ' · 过滤 ' + num(obj.filtered) +
                ' · 失败 ' + num(obj.failed);
        }
        return null;
    }

    function summarizeAssessment(feedback) {
        var a = feedback && feedback.materialAssessment;
        if (!a) return '';
        var issues = Array.isArray(a.validationIssues) ? a.validationIssues : ['缺少校验记录'];
        if (issues.length) return '充分性评估未采纳：' + issues.join('；');
        var verdict = a.materialSufficient === true ? '材料充分' : a.materialSufficient === false ? '仍需补充' : '暂无法判断';
        return '模型判断：' + verdict + (a.reason ? '；' + a.reason : '');
    }

    return {
        eventTypeText: eventTypeText,
        phaseText: phaseText,
        memoryStatusText: memoryStatusText,
        taskTypeText: taskTypeText,
        safeParseJson: safeParseJson,
        emptyOr: emptyOr,
        fmtDateTime: fmtDateTime,
        num: num,
        summarizeEventInput: summarizeEventInput,
        summarizeEventOutput: summarizeEventOutput,
        summarizeAssessment: summarizeAssessment
    };
}));
