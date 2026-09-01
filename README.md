# Policy Monthly Report Agent

面向山东省工业和信息化厅月度汇报场景的人机协同政策月报系统。核心流程是：

```text
政策检索 → 候选政策池 → 人工采纳/不采纳/暂缓 → Agent 分析
→ 月报内容人工编辑确认 → 原始 Word 模板填充 → 正式月报
```

搜索结果不会自动进入月报。只有人工审核状态为 `ACCEPTED` 的政策才允许触发 Agent 分析；正式 Word 只读取 `CONTENT_CONFIRMED` 月报中的 `CONFIRMED` 内容项。

## 当前重构进度

已完成 Phase 1—6 的可操作闭环，以及政策附件全文采集质量门禁：

- `SearchTask` 月报搜索任务，替代日报任务业务入口
- 候选政策四态审核：`PENDING / ACCEPTED / REJECTED / DEFERRED`
- 不可变的 `policy_review` 审核历史
- 政策分析版本、月报、栏目、内容项、修订和来源快照数据模型
- Flyway 迁移；旧 `daily_task` 数据会迁入 `search_task`，旧表暂不破坏性删除
- 可按年月创建空月报工作区
- Word 生成服务端门禁：生成阶段不调用 LLM
- 月报模板移除附件统计表，栏目括号提示作为 Agent 写作规则保留
- 一个搜索任务可同时选择多个固定政府信源
- `search_task_policy` 记录每次发现，同一政策可关联多个搜索任务而不重复存正文
- 固定信源统一经过栏目页解析、正文抓取、日期过滤、URL/正文哈希去重和基础相关性计算
- 前端包含“政策搜索”“候选政策审核”“已采纳政策”“月报内容编辑”“生成月报”五个视图
- 候选池支持按任务、审核状态、关键词和发现来源筛选，并提供原文、正文和审核历史
- 只有 `ACCEPTED` 政策可以触发 Agent 分析；分析结果按版本保存，失败也保留记录
- 栏目推荐采用“规则初判 + 模型结合全文和模板写作提示复核”，模型只能从可写入栏目中选择
- 人工可修改推荐栏目、标题和正文，再确认进入指定月份的月报内容池
- 入池时同时保存 Agent 初稿、人工终稿、首版修订和政策正文快照
- 月报编辑页支持继续改写、移动栏目、栏目内排序、软删除、恢复、来源查看和版本历史
- 单条内容人工确认与整月内容确认分层；整月确认后编辑接口写保护，必须显式重新打开编辑
- 只有校验通过且状态为 `CONTENT_CONFIRMED` 的月报可以进入确定性 Word 生成
- 生成时直接打开 monthly_report_template.docx 替换明确插槽，保留分节、页眉页脚、页码域、浮动图形和固定横线
- 每次生成保存不可变档案：版本号、生成参数、操作人、文件名、大小、SHA-256 和原始 DOCX 字节，可查看历史并重新下载
- 未配置 LLM 时应用仍可启动，搜索、审核和月报工作区不受影响
- 自动发现政策网页中的 PDF、DOCX、DOC、OFD 附件；PDF/DOCX 提取正文，暂不支持格式仍保留链接和失败原因
- 页面正文与成功解析的附件按来源分段合并，正文完整性分为 UNKNOWN / COMPLETE / PARTIAL / PAGE_ONLY / FAILED
- PDF/OFD 等同名备用格式按同一附件组判定；每组至少一种格式解析成功才视为完整
- 只有正文状态为 COMPLETE 的已采纳政策可以进入 Agent 分析

完整重构路线见 [当前项目现状分析与重构方案.md](./当前项目现状分析与重构方案.md)。

## 技术栈

- Java 17 / Spring Boot 3.5
- Spring Data JPA / PostgreSQL / Flyway
- Spring AI / DeepSeek
- Jsoup / Apache POI / Apache PDFBox
- HTML、CSS、JavaScript / Nginx

## 配置

推荐环境变量：

```text
POLICY_MONTHLY_DB_URL=jdbc:postgresql://localhost:5432/policy_daily_agent
POLICY_MONTHLY_DB_USERNAME=opspilot
POLICY_MONTHLY_DB_PASSWORD=你的数据库密码
POLICY_AGENT_MODEL=deepseek
DEEPSEEK_API_KEY=你的DeepSeekKey
```

不配置 `POLICY_AGENT_MODEL` 和 `DEEPSEEK_API_KEY` 时，系统会以“Agent 未启用”模式正常启动；需要深度分析时必须同时配置这两个变量并重启。

为便于原项目平滑迁移，数据库仍可读取旧的 `POLICY_DAILY_DB_URL`、`POLICY_DAILY_DB_USERNAME` 和 `POLICY_DAILY_DB_PASSWORD`。

千帆搜索为可选能力，需要时配置 `QIANFAN_API_KEY`。

## 启动

1. 准备 PostgreSQL 数据库。
2. 运行 `PolicyMonthlyReportAgentApplication`，Flyway 会自动执行迁移。
3. 启动前端代理：

```powershell
docker compose up -d
```

4. 打开 `http://localhost:8081/`。

## 核心 API

- `POST /api/search-tasks/run`：按月份和多个固定信源执行候选搜索
- `GET /api/agent/status`：Agent 可用状态，不返回密钥
- `GET /api/accepted-policies`：已采纳政策列表
- `GET /api/accepted-policies/{policyId}`：完整正文、附件提取状态和全部分析版本
- `POST /api/accepted-policies/{policyId}/refresh-content`：重新抓取网页与政策附件并评估正文完整性
- `POST /api/accepted-policies/{policyId}/analyses`：仅对完整正文执行或重新执行版本化分析
- `GET /api/accepted-policies/sections`：模板中允许写入的栏目及写作提示
- `POST /api/accepted-policies/{policyId}/analyses/{analysisId}/confirm`：人工确认并进入指定月份内容池
- `GET /api/search-tasks/recent`：最近搜索任务
- `GET /api/search-tasks/{id}`：搜索任务详情和失败原因
- `GET /api/review/search-tasks/{id}/policies?status=&keyword=&sourceId=`：筛选候选政策
- `GET /api/review/search-tasks/{id}/policies/{policyId}`：候选政策正文详情
- `POST /api/review/policies/{id}/accept|reject|defer|pending`：人工审核
- `GET /api/review/policies/{id}/history`：审核历史
- `POST /api/search/discover-and-save`：旧单栏目页兼容入口，仍会写入统一候选关联
- `POST /api/monthly-reports`：按年月创建或获取月报工作区
- `GET /api/monthly-reports/{reportId}/editor`：按模板栏目读取月报编辑工作区、来源和修订历史
- `PUT /api/monthly-reports/{reportId}/items/{itemId}`：人工修改标题、正文或栏目，带乐观锁版本
- `POST /api/monthly-reports/{reportId}/sections/{sectionCode}/reorder`：栏目内排序
- `POST /api/monthly-reports/{reportId}/items/{itemId}/remove|restore`：软删除或恢复内容
- `GET /api/monthly-reports/{reportId}/items/{itemId}/trace`：读取 Agent 初稿、原始正文快照和全部修订
- `POST /api/monthly-reports/{reportId}/confirm-content`：整月内容人工确认
- `POST /api/monthly-reports/{reportId}/reopen`：重新打开已确认或已生成月报进行编辑
- `POST /api/reports/monthly`：只允许已确认内容生成正式 Word
- GET /api/reports/monthly/{reportId}/generations：读取该月报全部历史生成档案
- GET /api/reports/monthly/{reportId}/generations/{generationId}/download：重新下载指定不可变 Word 版本

## 测试

```powershell
.\mvnw.cmd test
```

测试配置使用内存 H2，不依赖本机 PostgreSQL。
