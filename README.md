# Policy Daily Agent

面向政府政策与行业信息跟踪场景的政策日报智能体。系统将政策采集、日期过滤、去重、人工审核和 Word 日报生成拆分为可追溯的流水线。

## 当前能力

- 从配置的政府固定信源采集候选政策
- 按政策实际发布日期过滤内容
- 保存来源 URL、正文和结构化信息
- 提供可复用政策素材工作区，支持追加采集、删除任务和链接勾选
- Agent 基于当前工作区人工勾选的多条政策生成结构化月报内容，Word Tool 负责填充原始模板并保留固定版式
- 使用 Nginx 提供独立前端并反向代理 Spring Boot API

## Agent 架构

项目通过持久化 Orchestrator、类型化 Tool Calling、任务级 Memory/State 和统一失败重试驱动采集到报告生成流程。架构与接口说明见 [Agent Architecture](docs/agent-architecture.md)。

## 技术栈

- Java 17+ / Spring Boot 3
- PostgreSQL
- DeepSeek Chat API
- HTML、CSS、JavaScript
- Nginx / Docker Compose

## 配置

复制 `.env.example` 中的配置到本机环境变量。不要将真实密钥提交到仓库。

必需配置：

```text
POLICY_DAILY_DB_PASSWORD=你的数据库密码
DEEPSEEK_API_KEY=你的DeepSeekKey
```

默认数据库连接为：

```text
jdbc:postgresql://localhost:5432/policy_daily_agent
```

可通过 `POLICY_DAILY_DB_URL` 和 `POLICY_DAILY_DB_USERNAME` 覆盖默认值。千帆搜索为可选能力，需要时配置 `QIANFAN_API_KEY`。

## 启动

1. 在 PostgreSQL 中创建 `policy_daily_agent` 数据库。
2. 在 IDEA 中运行 `PolicyDailyAgentApplication`，后端监听 `8080`。
3. 在项目根目录启动 Nginx：

```powershell
docker compose up -d
```

4. 打开 `http://localhost:8081/`。

浏览器只访问 Nginx。所有 `/api/*` 请求会被转发到 `http://host.docker.internal:8080`。

## 测试

```powershell
.\mvnw.cmd test
```

停止前端容器：

```powershell
docker compose down
```
