# 政策月报 Agent 增强：实现、演示与项目讲解

本次实现三个能力：材料缺口驱动搜索、参与后续规划的任务 Memory、草稿检查与有限次数修订。沿用 Spring Boot 搜索编排器、人机审核门、确定性日期校验/去重/栏目匹配/评分/Word 生成。

## 1. 如何从简历快照讲清楚实现

> 我把不确定的语义理解交给 LLM，把执行调度和业务规则留在程序里。搜索编排器先执行检索，模型对实际发现的材料做主题分类、抽取原文术语，并评估材料是否足以支持研究目标。程序校验引用和判断一致性：评估充分时结束搜集，仍有缺口时根据历史计划选择下一轮关键词与信源；轮数和无增长上限始终由程序约束。每轮结果保存在轮次记录、任务 Memory 和事件流中，中断后能够重建状态并复用原计划。正式政策分析必须先通过人工审核；生成草稿后由程序检查原文证据、数字、日期和格式，检查失败时反馈给模型修订，达到上限则标记失败。最后仍由人工确认内容，程序填充 Word 模板。

| 简历描述 | 代码入口 | 增强后可展示的行为 |
| --- | --- | --- |
| 搜索编排器统一调度 | `PolicySearchOrchestrator`、`SearchRoundService` | 下一轮查询由规则根据反馈与历史生成，实际执行严格限制在计划选中的信源 |
| LLM 语义分析 | `SearchFeedbackService`、`PolicyAnalysisAgent` | 搜索主题分类/术语提取；审核后的结构化分析及草稿修订 |
| 任务级 Memory 与事件流 | `SearchTaskMemoryContext`、`AgentTaskMemoryService` | 保存缺口、证据 ID、原文术语、已执行计划，页面可查看反馈事件 |
| 租约抢占和轮次重试恢复 | `SearchRoundService.resume()` | 按原参数快照恢复，重试中断轮次原计划，避免重新执行已完成查询 |
| 确定性栏目匹配 | `PolicySectionRecommendationService` | 模型即使给出另一个合法栏目，最终仍采用程序匹配栏目 |
| 服务端人工审核门 | `PolicyAnalysisWorkflowService`、内容确认服务 | 未通过审核拒绝正式分析；修订耗尽的 FAILED 分析不能进入内容池 |
| 可追溯 | `search_task_round`、`policy_analysis` | 搜索保存反馈 JSON，分析保存每次程序检查问题及修订次数 |

需要准确区分搜索阶段的材料主题分类与正式政策分析：前者服务于发现候选，不创建 `policy_analysis` 记录，也不自动通过人工审核。正式分析、入内容池和报告生成继续执行原有门禁。

### 表数与测试数的统计口径

- 当前 V1～V14 建表脚本去重后定义 **18 张业务表**，其中包含历史 `daily_task`；`flyway_schema_history` 是迁移管理表，不计入业务表。本次 V15 只为既有两张表增加字段，没有新增业务表。
- 当前本地数据库还存在历史环境留下的 `agent_run`、`agent_step`，并且前次检查只迁移至 V12，因此数据库客户端显示的表数与迁移脚本定义数不同。
- 本次改动前，Git HEAD 中 Java 测试方法统计为 **108**。新增测试作为增强验证单独统计；简历中的 108 对应原项目快照，不应把增强后的测试总数也说成恰好 108。
- 简历正文无需改动；讲解时说明这是原项目快照，以及后续在同一架构下补充的能力。

## 2. 材料缺口驱动搜索

```text
原始关键词 + 用户选定信源
  → 执行本轮栏目检索与候选入库
  → 读取有限长度材料 + 已执行计划
  → LLM 输出主题证据、原文术语与材料充分性评估
  → 程序校验 topic、policyId、quote、term 及充分性引用
  → 记录覆盖统计（不单独代表材料充分）
  → 保存反馈、轮次记录与 Memory
  → 根据缺口选择尚未执行的 keyword/source 配对
  → 下一轮，或按程序停止条件结束
```

模型不输出检索动作、工具调用、栏目选择或相关性分数。它评估“材料是否充分”，编排器据此继续或结束。`SearchRoundPlanner.planFromFeedback()` 按模型提供的待补主题，优先使用有原文依据的术语，再使用程序主题词典；按用户配置的信源顺序选择未执行配对。相同关键词可以在另一个信源尝试，已执行的相同配对不会重复。即使关键词统计已覆盖，模型认为仍缺实质材料时也可以继续检索。

语义反馈每轮最多一次调用，输入最多 20 份材料，每份最多 1600 字符；每项引文最多 160 字符、术语最多 40 字符。输入还包含目标月份、日期范围、目标主题和执行历史。模型输出引用不存在、引文不在所读文本中、术语不在引文中时，该证据或术语被丢弃。阈值与覆盖状态由程序计算，仅作为统计。模型不可用或返回异常时保存明确的 `RULE_FALLBACK` 反馈，不采纳模型术语或宣称材料充分，继续受有界词典检索、轮数与无增长上限约束。

模型还需返回以下字段：

```json
{
  "materialSufficient": false,
  "missingTopics": ["人工智能"],
  "evidencePolicyIds": [123],
  "reason": "已有材料主要是背景表述，仍缺少实质应用进展"
}
```

`materialSufficient=true` 时，missingTopics 必须为空，全部目标主题必须有经过原文引文校验的材料支撑，充分性引用只能来自这些有效证据。`false` 时须列出任务范围内的待补主题。缺少字段、理由为空、引用无效、判断充分却仍列缺口等情况写入 validationIssues，不被采纳为“材料充分”。校验结果与模型原始充分性判断一起保存，页面显示理由及未采纳原因。

引用存在性不等于完整语义正确性；主题分类可能误判，最终候选仍需人工审核。语义覆盖只针对模型本轮实际读取的材料样本，不能解释成已经穷尽全部网页。

**检索边界**：当前 `GenericGovernmentSiteAdapter` 读取固定官方栏目 HTML/JSON 后筛选链接，不具备全站或全网索引。本次实现的是这些信源内的反馈驱动发现，尚未接入外部搜索 API。需要扩大到全网时，应另增搜索服务适配器，将结果交回现有爬虫和日期校验流程；不能把栏目筛选称为全网搜索。

新语义任务以有效模型充分性评估结束，原因记为 `LLM_MATERIAL_SUFFICIENT`。关键词数量阈值不再单独触发完成；模型判断不足时继续检索。最大轮数、连续无新增关联、无新计划、没有可用信源及执行错误仍由程序控制。达到预算而仍有缺口时，Memory 保留缺口，结束原因保留 `MAX_ROUNDS_REACHED` 等明确原因。规则模式及旧语义任务维持原阈值停止策略。

## 3. Memory 与恢复

新增信息在现有结构中保存：

- `search_task.run_params_json`：版本 3，保存 `semanticFeedbackEnabled` 和 `materialSufficiencyEnabled`。恢复采用原快照，不跟随修改后的默认值；版本 1 按原规则模式恢复，版本 2 的语义任务维持原阈值停止行为，即使新反馈中带有充分性判断也不改变其执行策略。
- `search_task_round.search_feedback_json`：本轮经校验的主题反馈及术语，是恢复事实来源。
- `agent_task_memory.context_json.searchFeedback`：最新反馈，包含 materialAssessment（判断、缺口、引用、理由、校验问题），加上既有 executedPlans、sourceFailures、topicCoverage 和计数。
- `agent_task_event`：新增 `SEARCH_FEEDBACK_EVALUATED` 事件；页面显示材料缺口、原文术语与降级说明。

恢复先抢占租约，按逻辑轮次取最后一次尝试，重建已完成计划与最新反馈。中断轮次复用已保存关键词和信源，以相同 round_no、递增 retry_no 重试。Memory 快照写入仍为尽力而为；恢复依据持久化轮次和业务关联重建，快照缺失不会让已完成计划丢失。

模型调用前后续约并校验执行权，轮次结果落库继续使用现有 ownership/fencing 机制。规划和正式执行均由编排器控制。

## 4. 草稿检查与修订

`PolicyAnalysisAgent.analyze()` 最多调用模型三次：首次生成 + 最多两次修订。每次把程序检查问题和上次草稿交回模型，要求重新输出完整 JSON。

`PolicyDraftValidator` 检查：

1. 至少一条原文证据；证据去空白后在正文中逐字存在且长度足够。
2. 标题与正文长度上限、空正文、Markdown/列表格式。
3. 阿拉伯数字、百分数、完整年月日及常见数量单位是否在正文或已校验元数据中有依据。
4. 基本信息发布日期是否等于已校验日期。
5. “已建成/已完成/已实现”等完成措辞没有出现在原文时，要求回查并修订。

这些检查是确定性异常筛查，不能证明所有数字都归属于正确主体，也不能验证所有中文数字或全部语义蕴含。未通过检查的草稿在两次修订后标记 FAILED；质量轨迹仍保存，服务端继续拒绝其进入内容池。人工审核与内容确认是最终质量环节。

`policy_analysis.quality_report_json` 保存是否通过、修订次数、每次检查问题；分析页面展示结果与次数，失败时展示失败原因。模型不能自己声明检查通过，报告由程序生成。栏目始终由程序规则匹配。

## 5. 启用与数据库迁移

启动服务前，在实际启动环境中设置（仅编辑 `.env.example` 不会自动加载到 Java 进程）：

```powershell
$env:POLICY_SEARCH_MULTI_ROUND_ENABLED = "true"
$env:POLICY_SEARCH_SEMANTIC_FEEDBACK_ENABLED = "true"
$env:POLICY_AGENT_MODEL = "deepseek"
# DEEPSEEK_API_KEY 使用你现有的安全配置方式提供
```

在搜索页面勾选“根据材料缺口继续检索”。API 请求也可以携带 `multiRoundEnabled: true`。两个搜索开关默认关闭，原单轮及规则多轮行为继续可用；草稿程序检查在正式分析时执行。

启用语义反馈后，新建任务自动采用 LLM 材料充分性评估来决定是否需要下一轮，无需增加一个单独的开关。旧任务保持其首次执行时保存的策略。充分性字段保存在原有 JSON 列中，本次停止判断改造没有增加数据库表或列。

V15 由 Flyway 在正常启动时执行：

```sql
ALTER TABLE search_task_round ADD COLUMN IF NOT EXISTS search_feedback_json TEXT;
ALTER TABLE policy_analysis ADD COLUMN IF NOT EXISTS quality_report_json TEXT;
```

本次开发未对已有业务数据库执行迁移。前次检查该库停在 V12，启动更新后的代码时应由 Flyway 顺序执行 V13、V14、V15，不手工跳过版本。

## 6. 可复现的演示与验证

运行完整离线测试：

2026-10-06 验证结果：完整 Java 测试运行 136 个用例，失败、错误及跳过均为 0；前端 Memory 断言及页面内联脚本语法检查通过。这里统计的是运行用例数，与原快照的 108 个 Java 测试方法口径不同。

```powershell
.\mvnw.cmd -o test
node scripts/memory-format.test.js
```

重点看以下场景：

- `SemanticSearchIntegrationTests.resultFeedbackChangesQueryAndActualSourceThenPersistsMemory`：首轮模型判断材料不足，原文术语“智能工厂”触发第二轮，仅访问计划选定的一个信源；模型随后判断充分，Memory 与反馈事件都保存。
- `modelCanFinishAfterFirstRoundBelowCoverageCountThreshold` / `coveredStatisticsDoNotOverrideModelRequestForAnotherRound`：验证模型可以在统计阈值未达时判断充分，也可在统计已覆盖时要求补充。
- `roundBudgetEndsSearchEvenWhenModelKeepsRequestingMore`：模型一直要求继续，程序仍在最大轮次强制结束。
- `recoveryHonorsSavedDecisionForNewTasksAndOriginalStoppingPolicyForVersionTwo`：新任务恢复后复用已保存的充分性判断，不重新调用模型或搜索；版本 2 任务仍按原阈值策略恢复。
- `SemanticSearchIntegrationTests.resumeReusesInterruptedPlanAndSavedFeedbackWithoutRepeatingCompletedQuery`：恢复时只重试“智能工厂”中断计划，不重新执行已完成的“人工智能”查询。
- `SearchFeedbackServiceTests`：虚构 policyId、引文和术语被拒绝；无模型、无材料和非法 JSON 有明确降级行为。
- `PolicyDraftRevisionTests`：虚构数量和证据触发修订；反复错误触及三次调用上限；日期重组、单位替换与完成措辞得到异常提示。
- `PolicyAnalysisWorkflowServiceTests`：失败质量报告保存，FAILED 分析不能进入内容池，未人工采纳的政策仍拒绝正式分析。

集成测试使用真实编排器、H2 仓储与 Memory，外部 HTTP 和 ChatModel 使用可控替身。它们证明程序闭环和恢复行为，不能替代真实政府网站可达性或真实模型输出质量验证。本次没有发起付费模型调用。
