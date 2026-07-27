# BioNote 第二阶段实施状态

最后更新：2026-07-27（Asia/Shanghai）

## 总体状态

- 当前阶段：第二阶段第 0–7 批全部完成并通过最终验收。
- 工作区起始状态：Git 工作区干净；现有内容视为用户所有，未执行清理、回退、stash、commit、push 或 PR。
- 迁移状态：V1-V8 均未修改；V9-V12 已在 H2 验证，V9-V12 已实际应用到 MySQL 8.4；MySQL `flyway_schema_history` 的 V12 记录为 `success=1`。
- 安全基线：根目录 `llm` 的值从未输出；精确路径 `/llm` 已加入 `.gitignore`，并已从 Git 索引取消跟踪且保留本地文件。密钥未复制到配置、测试 fixture、日志、Trace、截图或文档。

## 第 0 步：基线检查

### 已完成

- 完整阅读公共文档：
  - `docs/01-product-overview.md`
  - `docs/agent-guidelines/00-master-contract.md`
  - `docs/agent-guidelines/01-domain-api-contract.md`
  - `docs/phase2/README.md`
  - `docs/phase2/00-phase2-master-contract.md`
  - `docs/phase2/01-phase2-domain-api-contract.md`
  - `docs/phase2/09-team-ownership-merge-plan.md`
- 检查 Git 状态、应用目录、V1-V8 migration、后端与前端测试结构、Playwright 与 compose 配置。
- 启动 Docker Desktop 和仓库 MySQL 8.4 容器；容器健康检查通过。

### 实际测试结果

- `backend\\.\\mvnw.cmd test`：通过，31 tests，0 failures，0 errors，0 skipped。
- `backend\\.\\mvnw.cmd verify`：通过，31 tests，Jar 构建成功。
- `frontend\\npm.cmd run lint`：通过。
- `frontend\\npm.cmd test -- --run`：通过，15 files / 21 tests。
- `frontend\\npm.cmd run build`：通过；Vite 报告现有主 chunk 约 697 kB 的警告。
- `frontend\\npm.cmd run test:e2e`：通过，Chromium 4 tests。

### 环境诊断

- 普通沙箱无法访问 Maven 中央仓库，也不能写现有 `frontend/node_modules/.vite-temp`；经正常权限批准后测试与构建通过。这是执行环境权限差异，不是产品测试失败。
- E2E 输出存在 React Router v7 future flag 警告，不影响当前通过结果。

## 批次状态

| 批次 | 状态 | 迁移 | 备注 |
|---|---|---|---|
| 1 状态策略与类型化事件 | 已完成 | 无 | 45 项后端全量测试通过 |
| 2 Revision 与 Diff | 已完成 | V9 | 55 项后端全量测试与 4 项 E2E 通过 |
| 3 Restore Preview 与执行 | 已完成 | V10 | 63 项后端全量测试与 4 项 E2E 通过 |
| 4 Agent Harness Runtime | 已完成 | V11 | 76 项后端全量测试与 4 项 E2E 通过 |
| 5 Agent Tools 与报告 | 已完成 | 无 | 86 项后端全量测试、27 项前端测试通过 |
| 6 前端整合 | 已完成 | 无 | 38 项前端测试与 7 条 E2E 路径通过 |
| 7 集成与交付 | 已完成 | V12 | MySQL EXPLAIN、全量回归、8 条 E2E、真实 provider smoke 与交付文档均完成 |

## 第 1 批：状态策略与类型化事件

### 已完成

- 新增纯领域 `RecordActionPolicy`、`RecordContext`、`Decision`，集中表达编辑、提交、恢复、revision 查看和记录总结权限。
- `canRestore` 覆盖成员、ACTIVE、不可变 creator、provisional 和可编辑状态的固定判定顺序与稳定错误码。
- `RecordService` 的编辑/能力判断、`ReviewService` 的提交判断已委托策略；第一阶段其他服务保持兼容逻辑。
- 新增 `DomainEvent`、`DomainEventPublisher`、`DomainEventHandler`、`HandlerPhase`。
- 新增进程内同步 registry：事务 handler 有序执行且异常向上传播；after-commit best-effort handler 在事务提交后执行，失败只写脱敏警告。
- 新增类型化事件：`RecordRevisionRestoredEvent`、`AgentRunRequestedEvent`、`AgentRunSucceededEvent`、`AgentRunFailedEvent`、`AgentArtifactViewedEvent`、`RevisionDiffViewedEvent`。
- 新增 `AuditEventWriter` 与 `AuditEventHandler`；类型化事件使用 event ID 作为 audit 主键，重复投影幂等。
- 现有 `EventService.audit/notify` 签名保持不变；legacy audit 委托共享 writer。
- 新增 metadata 严格白名单、字符串/集合裁剪；禁止正文、prompt、token、storage key 和 preview token。
- 项目时间线允许显示 `RECORD_REVISION_RESTORED` 与 `AGENT_RUN_SUCCEEDED`，失败事件不进入默认时间线。

### 修改文件

- `backend/src/main/java/com/bionote/domain/record/**`
- `backend/src/main/java/com/bionote/collaboration/event/**`
- `backend/src/main/java/com/bionote/collaboration/EventService.java`
- `backend/src/main/java/com/bionote/audit/AuditService.java`
- `backend/src/main/java/com/bionote/record/RecordService.java`
- `backend/src/main/java/com/bionote/review/ReviewService.java`
- `backend/src/test/java/com/bionote/domain/record/**`
- `backend/src/test/java/com/bionote/collaboration/event/**`

### 实际测试结果

- `backend\\.\\mvnw.cmd "-Dtest=*RecordActionPolicyTest,*DomainEvent*Test" test`：通过，14 tests。
- `backend\\.\\mvnw.cmd test`：通过，45 tests，0 failures，0 errors，0 skipped。

## 第 2 批：Revision 与 Domain-aware Diff

### 已完成

- 新增 `V9__phase2_revision_metadata.sql`：`snapshot_schema_version` 和 revision history 索引。
- Revision 分页摘要使用一次主查询加 count，避免逐 revision 详情 N+1；详情使用一次 revision/review/user 查询加一次附件查询。
- 支持新嵌套详情路径和旧 `/revisions/{id}` 兼容委托。
- 实现 schema v1 Snapshot Normalizer、稳定 canonical SHA-256、固定字段、模板字段、TipTap 正文块、附件和审核元数据结构化 Diff。
- 支持 Revision vs Revision 与 Revision vs Working Copy；Working Copy 返回准确乐观锁版本。
- 不用原始 HTML 做 Diff；二进制附件只比较安全元数据和 ID。
- 实现大文本、hunk、section 截断与 warning。
- 新增统一前端 `revisions.js` API，并将现有 revision 消费者切换到分页摘要。
- 修复 Revision Summary 丢失 `canDecide` 导致审核表单不显示的 E2E 回归，并增加 reviewer/owner API 契约测试。

### 实际测试结果

- Revision 定向：10 tests 通过。
- 后端全量：55 tests 通过。
- 前端单元：16 files / 23 tests 通过。
- 前端 build：通过；仍有既有约 697 kB chunk 警告。
- 审核流程定向 Playwright：1 test 通过。
- Playwright 全量：4 tests 通过。
- V9：空 H2、既有 V8 H2、真实 MySQL 8.4 迁移均通过。

## 第 3 批：Restore Preview 与执行

### 已完成

- 新增 `V10__create_record_restore_operations.sql`，包含来源追溯、前后版本/哈希、Diff 摘要、payload hash、唯一 `(record_id,idempotency_key)` 和查询索引。
- HMAC-SHA256 Preview Token 使用规范 JSON、base64url、5 分钟有效期和随机 nonce，绑定 actor、record、project、source revision、expected version、附件策略与前后 canonical hash。
- Preview 完整执行对象级权限、状态、provisional、版本和来源 revision 校验，只读计算 Working Copy -> Revision Diff、附件计划、warning 与 `canExecute`。
- 执行在单一短事务内规范化 UUID 幂等键、校验 payload hash 和 token、锁定 record 行、重新授权、重新计算 Diff/附件计划，并以 `WHERE version=?` 更新 Working Copy。
- 恢复保持 `IN_PROGRESS`/`CHANGES_REQUESTED` 状态，不修改历史 revision、review、审核结论、身份字段或 revision 编号。
- 完整附件恢复精确激活/软删除；内容模式保留当前活动附件并过滤历史 FILE 引用；物理文件缺失禁止完整恢复。
- 写入只追加 restore operation，发布 `RECORD_REVISION_RESTORED` 类型化事件，同步 audit 与业务事务一致。
- Record capability 增加 `canRestore`；新增统一前端 `restore.js` API。

### 实际测试结果

- Restore 定向：8 tests 通过。
- 后端全量：63 tests 通过。
- 前端单元：17 files / 25 tests 通过。
- 前端 build：通过；仍有既有约 697 kB chunk 警告。
- Playwright 全量：4 tests 通过。
- V10：空 H2、回归 H2 和真实 MySQL 8.4 均迁移成功；MySQL Flyway 最新记录为 V10 success。
- 覆盖 token 篡改/过期、权限与跨记录隐藏、版本过期、附件计划/缺失文件、相同 key 重试、payload 冲突、两个并发恢复仅一成功、审计失败整体回滚、历史不可变和恢复后提交生成 R3。

## 第 4 批：Agent Harness Runtime

### 已完成

- 新增 `V11__create_agent_runtime.sql`，创建 append-only `prompt_versions`、数据库队列 `agent_runs`、顺序 Trace `agent_steps` 与一对一成功产物 `agent_artifacts`，包含领取、上下文列表和唯一性索引。
- 实现 `AgentHarness`、`AgentPlanner`、`AgentContextBuilder`、`AgentTool` SPI、自动注册表、执行器、run-scoped memory、结果校验、Trace recorder 与 provider-neutral `AgentModelClient`。
- 新工具只需新增只读 Bean 和测试，无需修改 Harness 主循环；启动时拒绝重名、非法 schema、空描述和非只读工具。
- 实现 QUEUED 原子领取、RUNNING stale 失败、终态保护、取消、deadline、step/tool/model/repair 限制以及 artifact 与 SUCCEEDED 同事务保存。
- 实现 PromptCatalog append-only 注册、内容 hash 防静默篡改、active version 和 run 固定 prompt version。
- 实现 deterministic fake provider 与 OpenAI-compatible 真实 adapter；未启用或未配置真实 key 时应用仍可启动。
- Trace 仅保存脱敏参数/结果摘要、hash、验证、耗时、token 和 artifact 引用；集中移除 secret、Authorization、reasoning 与 chain-of-thought，并裁剪大文本/数组。
- 修复共享 H2 测试隔离：新增 Agent 外键数据在每个 Runtime 集成测试结束后精确清理，不污染第一阶段回归。

### 实际测试结果

- Agent Runtime 定向：13 tests 通过。
- 后端全量：76 tests，0 failures，0 errors，0 skipped。
- Playwright 全量：4 tests 通过。
- V11：空 H2 与真实 MySQL 8.4 迁移成功；MySQL Flyway 最新记录为 `11 / create agent runtime / success=1`。
- 覆盖多工具注册、重复/非法工具拒绝、tool cache、顺序调用、一次修复、非法输出、provider timeout、取消、limits、双 Worker 唯一领取、stale RUNNING、prompt 幂等/篡改/v2 和 Trace 脱敏。

## 第 5 批：Agent Tools 与报告

### 已完成

- 新增九个 BioNote 白名单只读工具：`get_project_overview`、`list_project_records`、`get_record_overview`、`list_record_revisions`、`get_revision_summary`、`compare_record_revisions`、`list_review_feedback`、`list_project_activity`、`get_latest_project_report`。
- 工具每次调用重新校验当前项目成员关系和 subject 范围；actor/project/record 仅来自 `AgentToolContext`，不接受模型覆盖。
- 工具输出统一包含 data、evidence candidates、分页/截断/warning；正文限制 4,000 字、Diff 最多 50 sections、集合默认 20/最大 50、单工具序列化结果限制 20 KB。
- Revision 查询与 Diff 分别复用 `RevisionQueryService` 和 `RevisionDiffService`；不返回原始 HTML、TipTap JSON、storage key、邮箱、密码或未白名单 audit metadata。
- 新增 `ProjectProgressReadModelService`，从当前项目、记录、revision、review 和 audit 表构建状态分布、活动窗口与阻塞项，不建立平行业务事实表。
- 新增 `record-summary/v1` 与 `project-progress/v1` Prompt、严格 JSON Schema 和逐工具调用策略；明确工具数据不可信、`COMPLETED` 不等于实验成功、不知道时写 limitation，并区分 `REVIEW_FEEDBACK` 与 `MODEL_SUGGESTION`。
- 新增 run-scoped Evidence Candidate 收集与 `EvidenceValidator`：校验 ref、事实性 progress/risk、review-driven action、当前成员权限、对象归属、run 内工具来源及 revision diff source hash；拒绝邮箱、禁止工具声明和幻觉 ID。
- Evidence 首次失败允许一次 repair；再次失败稳定进入 `INVALID_OUTPUT` / `AGENT_EVIDENCE_INVALID`，不保存 artifact。
- 新增 Record Summary / Project Progress Run API、Run/Trace 查询、取消、rerun、record/project artifact 列表与 artifact 详情；创建接口返回 202，HTTP 线程不调用模型。
- Record Summary 仅 record creator 创建；Project Progress 仅 OWNER 创建；归档项目 OWNER 可生成只读进展报告；普通成员可读项目 artifact，但 trace 仅请求者或 OWNER 可见。
- 实现 `(requested_by,idempotency_key)` 幂等、payload hash、同用户/项目 active run 上限、同 subject cooldown、`parent_run_id`、取消边界和读取时重新授权。
- Run/step/artifact 分页已统一为公共 `PagedResponse` 顶层 `data + meta` 契约；前端统一 client 可直接解包为 `{items, meta}`。
- 新增前端 `agentRuns.js` API 封装，所有请求通过统一 client，不向浏览器暴露 provider 或 API key。
- 自动触发保持未实现且默认关闭；定时周报不在第二阶段范围。

### 实际测试结果

- 第 5 批定向：10 tests，0 failures，0 errors，覆盖创建权限、幂等、限流、取消/rerun、失权、Prompt injection、repair、非法 Evidence、Diff source hash 和逐工具调用上限。
- Agent Runtime 回归：13 tests 通过。
- 后端全量：86 tests，0 failures，0 errors，0 skipped。
- 前端全量：18 files / 27 tests 通过。
- 前端 build：通过；仍有既有主 chunk 约 697 kB 警告。

## 第 6 批：前端版本治理与 Agent 体验

### 已完成

- 记录详情新增“当前内容 / 版本历史 / AI 记录总结”上下文标签；项目详情新增“智能进展”，未增加顶级 `/ai` 路由或侧栏聊天入口。
- Revision History 分页展示 Rn、提交人/时间、提交说明、审核状态、附件数以及当前审核/最终批准标记；版本详情延迟加载并在可关闭抽屉中展示固定字段、模板字段、正文、附件和审核元数据。
- 提供明确的基准版本与目标版本选择，支持 R1/R2 和当前工作副本；相同 source 禁用比较并保留用户选择到 query 参数。
- 结构化 Diff Viewer 覆盖 scalar、template/text hunk、attachment set 与 review metadata；插入/删除同时使用 `+`/`−`、文本和 aria label，不只依赖颜色；大内容和 warning 有明确状态。
- Restore Preview Dialog 实现两阶段交互：附件策略切换会重新请求 preview；执行复用同一幂等 key，提交 token/version/附件策略；stale/乐观锁冲突保留弹窗并允许重新预览。
- 恢复成功跳转记录编辑器并显示“历史版本未改变”；UI 不宣称恢复即创建 revision，E2E 验证后续提交生成 R3 且 R1/R2 保持不变。
- Record Summary 仅 creator 显示生成入口；Project Progress 仅 OWNER 显示生成入口；其他成员可查看已有 artifact。
- Agent 创建后显示 QUEUED/RUNNING，按 2/3/5 秒退避轮询；终态停止，组件卸载清理 timer；支持 cancel、rerun、失败/invalid output/rate limit/provider unavailable/disabled 的稳定文案。
- Artifact 按 headline、executive summary、progress、risk、next action、evidence、limitations 展示；`REVIEW_FEEDBACK` 与 `MODEL_SUGGESTION` 使用不同标签。
- Evidence chip 支持跳转记录、revision、review 和 revision diff；Diff Evidence 自动恢复 from/to 并加载结构化 Diff。
- Trace Viewer 展示 run 元数据、token、step/tool/validation 摘要；前端再次过滤 secret、Authorization、reasoning/COT；Replay 只逐步展开已保存 step，不重新调用模型或工具。
- 新增确定性、工具驱动的 fake provider 默认行为：项目报告调用 overview/records/activity，记录总结调用 record/revisions/diff；输出 Evidence 来自真实工具候选，不依赖固定业务成功接口。
- Revision、Record Summary、Project Progress 与 Trace 面板使用 `React.lazy`；生产主 chunk 约 701 kB，新增能力拆分为约 2–17 kB 独立 chunks，基本维持基线体积。

### 实际测试结果

- 第 6 批新增组件测试：11 tests，覆盖 Diff section、版本选择、延迟详情、Restore Preview/执行/stale、Artifact/Evidence、轮询停止、timer 清理、cancel/rerun、Trace 脱敏/Replay、creator/OWNER 生成权限。
- 前端全量：23 files / 38 tests 通过。
- 前端 lint：通过。
- 前端 build：通过；主 chunk 约 701 kB，仍有既有 500 kB 警告。
- 既有 Playwright 回归：4 tests 通过。
- 新增 `revision-diff-restore.spec.js`：通过，覆盖 R1/R2 Diff、Preview、恢复、历史不可变和 R3。
- 新增 `agent-progress.spec.js`：通过，覆盖工具驱动 fake provider、Project/Record Artifact、Evidence 跳转、Trace 与 Replay。
- 新增 `phase2-authorization.spec.js`：通过，覆盖 outsider revision/artifact/trace、OWNER 不能恢复他人记录、MEMBER 不能生成项目报告。

## 当前失败和限制

- 当前无失败测试。
- 当前无新失败。
- 自动化测试默认使用 deterministic fake provider，不读取真实密钥或联网。
- OpenAI-compatible 真实 provider adapter 已完成合成数据联网 smoke；显式 smoke 测试默认跳过，只有设置 `RUN_REAL_AGENT_SMOKE=true` 并注入 `AGENT_*` 时才联网。
- Playwright 仅有 React Router v7 future flag 警告，不影响通过结果。

## 下一步

第二阶段无剩余实现或验收事项。交付摘要见 `docs/phase2/FINAL_REPORT.md`，演示步骤见 `docs/phase2/DEMO_SCRIPT.md`。

## 第 7 批：集成、质量、安全与演示交付

### 已完成

- 统一前端 API export、Agent 状态枚举、后端 Agent/Restore 配置、OpenAPI annotations 与三份 README。
- 根据真实 MySQL 查询计划新增 `V12__optimize_phase2_history_pagination.sql`，仅增加：
  - `idx_restore_record_time_id`
  - `idx_agent_artifacts_project_time_id`
  - `idx_agent_artifacts_record_time_id`
- V12 已通过 Flyway 应用到 MySQL 8.4；迁移后 EXPLAIN 显示 Restore、Project Artifact、Record Artifact 三类分页查询命中新索引且无 filesort。Revision、Worker claim 与 Step 查询沿用既有有效索引，因此未重复创建。
- 修复 Agent 取消与 Artifact 保存竞争：模型返回后再次检查取消；Artifact 事务内锁定 Run 并再次检查，取消获胜时不保存 Artifact。
- 补齐 provider 工具后失败、Artifact 事务失败、取消竞争、成功/失败 Artifact 数量、连续 step_no 等故障注入和数据完整性测试。
- 增加 deterministic fake 的 invalid output、unknown tool、timeout、invalid evidence 场景及 `agent-failure.spec.js`。
- Demo seed 提供 R1→退回→R2→CHANGES_REQUESTED 的四类 Diff、成功 Project Progress Artifact 和 Evidence 失败 Run，均通过正式服务、Run API 与 Worker 幂等生成。
- 完成 `ARCHITECTURE.md`、`DEMO_SCRIPT.md` 和本最终状态文件。
- 真实 provider smoke 发现并修复两个 adapter 兼容问题：将严格输出 Schema 注入 provider system instruction；仅对“整个响应恰好为一个 JSON 代码围栏”的情况安全解包，仍拒绝夹带解释文本。

### 最终实际测试结果

- 后端 `mvnw test`：94 tests，0 failures，0 errors，1 skipped。跳过项是默认禁用的显式联网 smoke；其余测试包括第一阶段回归、第二阶段权限/并发/故障注入/数据完整性。
- 后端 `mvnw verify`：94 tests，0 failures，0 errors，1 skipped；Jar 构建成功。
- 真实 provider `RealProviderSmokeTest`：1 test，0 failures，0 errors；实际验证 OpenAI-compatible adapter、合成只读工具调用、Schema 传递和结构化 JSON 规范化，未发送 BioNote 数据。
- 前端 `npm run lint`：通过。
- 前端 `npm test -- --run`：24 files / 39 tests 全部通过。
- 前端 `npm run build`：通过；1955 modules，主入口 chunk 484.12 kB。
- Playwright `npm run test:e2e`：Chromium 8/8 通过，最终重跑 42.5 秒。
- `git diff --check`：通过，无 whitespace error。

### 修改与交付重点

- 迁移：`backend/src/main/resources/db/migration/V9__*.sql` 至 `V12__*.sql`。
- 后端：`revision/**`、`restore/**`、`domain/record/**`、`collaboration/event/**`、`agent/**`、Demo seed 与对应测试。
- 前端：`api/revisions.js`、`api/restore.js`、`api/agentRuns.js`，Revision/Restore/Agent 组件、记录/项目页面和 E2E。
- 文档：根/后端/前端 README、`ARCHITECTURE.md`、`DEMO_SCRIPT.md`、`FINAL_REPORT.md`。

## 2026-07-27 Agent LIMIT_EXCEEDED 运行时修复

- 根因：`agent_runs.step_count` 是 Trace 条目计数，但旧 Harness 将其误用于 `AGENT_MAX_STEPS`；5 次模型请求与 10 次工具请求会产生 30 多条 Trace，从而把正常 Run 提前标记为 `LIMIT_EXCEEDED`。
- 修复：Run memory 独立统计模型轮次与工具执行动作；Trace 条目继续完整保存，但不再消耗执行 step 预算。真实模型请求由 `AGENT_MAX_MODEL_CALLS=6` 独立硬限制。
- 缓存：相同 tool name + arguments hash 在逐工具限额判断前命中缓存，不重复执行领域查询；缓存结果向模型加入停止重复的 runtime notice。不同参数仍受 Prompt 逐工具策略和全局工具限额约束。
- Prompt：新增 append-only `record-summary/v2` 与 `project-progress/v2`，保留 v1 不变；v2 要求合并独立工具调用、禁止相同参数重复请求，并在证据足够后立即返回结构化结果。
- 本机 `.env` 与 `.env.example` 已将 `AGENT_MAX_MODEL_CALLS` 从 10 收紧为 6。旧 Run 的 limits/prompt 已冻结，重启后端并 rerun 才会应用新配置。
- 定向测试：19 tests，0 failures，0 errors。
- 后端全量：99 tests，0 failures，0 errors，1 skipped（默认禁用的联网 smoke）。
- 真实 provider 合成数据 smoke：1 test，0 failures，0 errors；只发送合成工具和结果，未发送 BioNote 数据，未输出密钥。
