// 最小可执行验证：node scripts/memory-format.test.js
// 仅依赖 Node 内置 assert，覆盖 JSON 解析、事件名称映射、空值兜底等纯函数。
'use strict';
const assert = require('assert');
const M = require('../frontend/memory-format.js');

// 事件类型中文映射 + 未知回退原值
assert.strictEqual(M.eventTypeText('TASK_STARTED'), '任务开始');
assert.strictEqual(M.eventTypeText('SOURCE_SEARCHED'), '信源搜索完成');
assert.strictEqual(M.eventTypeText('SOURCE_FAILED'), '信源搜索失败');
assert.strictEqual(M.eventTypeText('ROUND_COMPLETED'), '本轮搜索完成');
assert.strictEqual(M.eventTypeText('TASK_COMPLETED'), '任务完成');
assert.strictEqual(M.eventTypeText('TASK_FAILED'), '任务失败');
assert.strictEqual(M.eventTypeText('SOMETHING_NEW'), 'SOMETHING_NEW');
assert.strictEqual(M.eventTypeText(null), '');

// 阶段 / 状态 / 任务类型映射
assert.strictEqual(M.phaseText('INITIALIZED'), '初始化');
assert.strictEqual(M.phaseText('FAILED'), '失败');
assert.strictEqual(M.phaseText('UNKNOWN'), 'UNKNOWN');
assert.strictEqual(M.memoryStatusText('ACTIVE'), '进行中');
assert.strictEqual(M.taskTypeText('POLICY_SEARCH'), '政策搜索');

// safeParseJson：空 / 非法 → null；合法 → 对象
assert.strictEqual(M.safeParseJson(null), null);
assert.strictEqual(M.safeParseJson(''), null);
assert.strictEqual(M.safeParseJson('   '), null);
assert.strictEqual(M.safeParseJson('{bad json'), null);
assert.deepStrictEqual(M.safeParseJson('{"a":1}'), { a: 1 });

// emptyOr：空值兜底
assert.strictEqual(M.emptyOr(null, '未记录'), '未记录');
assert.strictEqual(M.emptyOr('', '未记录'), '未记录');
assert.strictEqual(M.emptyOr('   ', '未记录'), '未记录');
assert.strictEqual(M.emptyOr([], '暂无'), '暂无');
assert.strictEqual(M.emptyOr('有值', '未记录'), '有值');
assert.strictEqual(M.emptyOr(0, '暂无'), 0);

// 日期格式化
assert.strictEqual(M.fmtDateTime('2026-08-01T10:30:00.123456'), '2026-08-01 10:30:00');
assert.strictEqual(M.fmtDateTime(null), '未记录');

// 事件输入 / 输出摘要
assert.strictEqual(M.summarizeEventInput('SOURCE_SEARCHED', '{"sourceId":"gov","sourceName":"中国政府网","roundNo":1}'), '信源：中国政府网');
assert.strictEqual(M.summarizeEventInput('SOURCE_SEARCHED', 'bad json'), null);
assert.strictEqual(M.summarizeEventOutput('SOURCE_FAILED', '{"error":"连接超时"}'), '错误：连接超时');
assert.strictEqual(M.summarizeEventOutput('ROUND_COMPLETED', '{"found":2,"saved":1,"duplicate":0,"filtered":0,"failed":0}'), '发现 2 · 保存 1 · 重复 0 · 过滤 0 · 失败 0');
assert.strictEqual(M.summarizeEventOutput('TASK_STARTED', null), null);

console.log('memory-format 纯函数断言全部通过');
