# BioNote Backend

Spring Boot REST API，负责认证、对象级权限、记录状态机、不可变 Revision、结构化 Diff、安全 Restore、类型化事件、Agent Runtime、审计、附件和导出。完整启动、环境变量、演示账号和测试说明见根目录 `README.md`。

主要模块：

- `domain/record`：集中式 `RecordActionPolicy`。
- `revision`：分页查询、Snapshot Normalizer、canonical hash 与领域 Diff。
- `restore`：HMAC preview、附件计划、事务/幂等/乐观锁恢复。
- `collaboration/event`：事务型与 after-commit 类型化事件 handler。
- `agent/runtime`：Harness、Planner、Context、Memory、数据库队列和 Worker。
- `agent/tool`：只读 Tool SPI、Registry、Executor 与九个 BioNote 工具。
- `agent/prompt`、`validation`、`trace`：Prompt 版本、JSON Schema、Evidence 与脱敏 Trace。

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

API 前缀 `/api/v1`；健康检查 `/actuator/health`；OpenAPI `/swagger-ui/index.html`。

V9 增加 Revision metadata；V10 创建恢复操作；V11 创建 Prompt/Run/Step/Artifact；V12 是 MySQL EXPLAIN 驱动的 Restore/Artifact 分页索引。V1–V8 和其他已应用迁移不得修改。

HTTP 创建 Agent Run 返回 202；Worker 原子领取。成功 Artifact 与 `SUCCEEDED` 同事务保存，取消在模型响应后及 Artifact 行锁事务内再次检查。测试 profile 使用 deterministic fake provider，不读取真实密钥。

Runtime 将执行预算与 Trace 分开：`maxSteps` 统计模型轮次和工具动作，数据库 `step_count` 统计可回放 Trace 条目；`maxModelCalls` 独立限制真实 provider 请求数，默认 6。完全相同的工具参数优先命中 Run 内缓存且不消耗逐工具唯一调用额度。Record Summary / Project Progress active Prompt 为 v2，要求合并独立工具请求、禁止重复参数并在证据充分后尽快结束。

真实 provider smoke 默认禁用。显式设置 `RUN_REAL_AGENT_SMOKE=true`、`AGENT_BASE_URL`、`AGENT_API_KEY`、`AGENT_MODEL` 后运行 `mvnw -Dtest=RealProviderSmokeTest test`；该测试只发送合成数据。OpenAI-compatible adapter 会把严格输出 Schema 加入 system instruction，并只兼容整个响应为单个 `json` 代码围栏的情况，不接受围栏外解释文本。
