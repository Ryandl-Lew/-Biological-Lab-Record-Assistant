# BioNote 第二阶段最终交付报告

完成日期：2026-07-27（Asia/Shanghai）

## 结论

第二阶段第 0–7 批已全部实现并通过最终验收。系统可实际启动和演示完整闭环：不可变 Revision → 领域感知 Diff → Restore Preview → 事务/幂等/乐观锁安全恢复 → 类型化事件与审计 → 可扩展 Agent Harness → 白名单只读工具 → Record Summary / Project Progress → Evidence → Trace / Replay。

第一阶段的角色、对象级授权、记录状态机、项目归档不可逆、完成记录不可变、软删除和乐观锁规则保持有效。未新增开放聊天、Agent 写工具、任意 SQL/HTTP/文件系统、向量数据库、多 Agent 或自动审核/恢复入口。

## 实际实现

### Revision 与 Diff

- `record_revisions` 是唯一用户历史模型，历史快照不可修改、不覆盖、不重编号。
- V9 增加 `snapshot_schema_version` 和历史查询索引。
- Revision 分页摘要与详情消除逐 revision N+1。
- Snapshot Normalizer 生成稳定 canonical JSON/SHA-256。
- Diff 按固定字段、模板字段、TipTap 正文块、附件安全元数据和审核元数据对齐；支持 Revision/Revision 和 Revision/Working Copy。
- 不比较原始 HTML 或二进制附件内容；大文本、hunk 和 section 有确定性截断与 warning。

### Restore

- V10 创建只追加 `record_restore_operations`。
- Preview token 使用 HMAC-SHA256、base64url、nonce 和 5 分钟有效期，绑定 actor、record/project、source revision、expected version、附件策略和前后 hash。
- 执行在单个短事务内完成行锁、重新授权、payload hash、幂等键、乐观锁、Working Copy 更新、附件激活/软删除、operation 与类型化审计事件。
- 恢复只修改 Working Copy；R1/R2、Review、审核结论和 revision 编号不变。恢复后再次提交生成下一个 Rn。
- 同 key 重试返回同一结果；不同 payload 冲突；并发恢复只有一个成功；审计失败整体回滚。

### 类型化事件与审计

- `RecordActionPolicy` 集中表达编辑、提交、Revision、Restore 和 Record Summary 权限。
- 同步事务 handler 失败会回滚；after-commit handler 为 best effort。
- 第二阶段事件包括 Revision restored、Agent requested/succeeded/failed、Artifact viewed、Diff viewed。
- Audit metadata 使用安全白名单和裁剪，不保存正文、prompt、token、storage key 或 preview token。审计表不是 Event Sourcing 事实库。

### Agent Runtime、工具与报告

- V11 创建 append-only Prompt、数据库 Run 队列、顺序 Step Trace 和一对一 Artifact。
- Harness 包含 Planner、ContextBuilder、Tool SPI/Registry/Executor、RunMemory、Validator、TraceRecorder 和 provider-neutral ModelClient。
- 新只读工具通过 SPI 注册，无需修改 Harness 主循环；重名、非法 schema、写工具会在启动时拒绝。
- Run 支持 QUEUED/RUNNING/终态、原子领取、双 Worker 竞争、stale、取消、timeout、step/tool/model/token/duration/repair 限制。
- Artifact 与 SUCCEEDED 同事务保存；模型返回后及 Artifact 行锁事务内再次检查取消。
- 九个 BioNote 工具只读且逐调用重新授权，不接受模型覆盖 actor/project/record。
- Record Summary 与 Project Progress 使用版本化 Prompt、严格 JSON Schema、Evidence Candidate/Validator；事实性 progress/risk 必须引用本 Run 内真实工具证据。
- Trace 只保存脱敏参数/结果摘要、hash、验证、耗时、token 和最终 Artifact 引用，不保存 chain-of-thought。
- deterministic fake 是自动化与离线演示默认 provider；OpenAI-compatible adapter 是真实 provider 实现。

### 前端

- 记录内提供 Revision History、详情、R1/R2/Working Copy 选择、结构化 Diff、Restore Preview、Record Summary。
- 项目内提供 Project Progress；没有顶级 `/ai` 或侧栏 AI 聊天入口。
- Run 使用 2/3/5 秒退避轮询，支持 cancel、rerun、Trace Viewer 和已保存步骤 Replay。
- Evidence 可跳转记录、revision、review 和预填 source 的 Diff。
- Diff 不只依赖颜色，包含文本符号与 aria label；包含加载、空、失败、过期和冲突状态。

## 数据库迁移

- V1–V8：未修改。
- V9：Revision schema version 与历史索引。
- V10：Restore operation、幂等和历史查询结构。
- V11：Prompt、Run、Step、Artifact。
- V12：经真实 MySQL EXPLAIN 后新增三个精确分页索引：
  - `idx_restore_record_time_id`
  - `idx_agent_artifacts_project_time_id`
  - `idx_agent_artifacts_record_time_id`

V12 已实际应用到 MySQL 8.4，`flyway_schema_history.success=1`。迁移后 Restore/Project Artifact/Record Artifact 查询命中新索引且无 filesort；其余查询沿用已有有效索引。

## Demo 数据与材料

- `HepG2 缺氧 12 h VEGFA 表达分析（R2 复核）`：R1→退回→R2→CHANGES_REQUESTED，固定字段、模板字段、正文和附件均有 Diff。
- 另有已完成 R1/R2 记录，用于展示“可比较、不可恢复”。
- fake Runtime 通过正式 Run API 和 Worker 幂等生成一个成功 Project Progress Artifact 与一个 Evidence 失败 Run。
- 架构材料：`docs/phase2/ARCHITECTURE.md`。
- 8–12 分钟演示：`docs/phase2/DEMO_SCRIPT.md`。

## 最终实际测试

| 验收 | 实际结果 |
|---|---|
| `backend\\.\\mvnw.cmd test` | 94 tests，0 failures，0 errors，1 skipped |
| `backend\\.\\mvnw.cmd verify` | 94 tests，0 failures，0 errors，1 skipped；Jar 成功 |
| `RealProviderSmokeTest`（显式联网） | 1 test，0 failures，0 errors |
| `frontend\\npm.cmd run lint` | 通过 |
| `frontend\\npm.cmd test -- --run` | 24 files / 39 tests 通过 |
| `frontend\\npm.cmd run build` | 通过；1955 modules；主入口 484.12 kB |
| `frontend\\npm.cmd run test:e2e` | Chromium 8/8 通过；最终重跑 42.5 秒 |
| `git diff --check` | 通过 |

默认全量测试中跳过的 1 项正是 `RealProviderSmokeTest`，因为默认测试必须离线且不能读取真实密钥。该测试已在显式注入本机 `llm` 配置后单独实际通过。

关键覆盖包括 R1/R2 不可变、五类 Diff、preview 过期、Restore 幂等/并发/回滚/附件/R3、双 Worker、provider/tool/artifact 故障注入、timeout/cancel/limits、unknown tool/非法参数、Prompt injection、幻觉 Evidence、创建后失权、outsider Revision/Diff/Restore/Artifact/Trace，以及第一阶段认证、审核、附件、搜索、导出和归档回归。

## 真实 provider smoke

- 使用仓库真实 `OpenAiCompatibleModelClient` 和 `llm` 提供的 base URL/API key/model；所有值仅在进程环境中使用，未输出或落盘。
- 为避免外部数据泄露，smoke 只发送合成工具定义、合成查询和合成工具结果，不发送 BioNote 项目、记录、附件或用户数据。
- 实际验证：provider 返回工具调用 → adapter 规范化工具名/参数 → 回传合成工具结果 → provider 返回满足枚举约束的 JSON → adapter 解析并断言 schemaVersion/status/evidenceRef。
- Smoke 暴露并修复：错误 base URL 候选、Schema 未传入 provider prompt、provider 返回整段 `json` 围栏。最终 1/1 通过。
- `llm` 已精确忽略并从 Git 索引取消跟踪；本地文件保留。

## 安全检查

- 主实现未发现 `record_versions`、`save_history`、`revision_diff_v2`、`ai_service`、`chat_service`、`mock_agent`、`Thread.sleep` 或 `fake_success` 平行实现。
- 命中项仅来自契约文档、测试攻击数据、历史参考目录或正常环境变量/Authorization 代码。
- 未发现 API key 字面量、JWT、数据库密码、完整大正文、attachment storage key 或隐藏推理进入 Trace/Artifact/文档。
- 所有写操作在 service/domain 层重新授权；Controller 只做协议转换。

## 从零启动与演示

最短本地演示：

```powershell
cd backend
.\\mvnw.cmd spring-boot:run

cd ..\\frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1
```

打开 `http://127.0.0.1:5173`，使用 README 中的 seed 账号，按 `docs/phase2/DEMO_SCRIPT.md` 依次演示 Revision/Diff、Restore/R3、Project Progress/Evidence/Trace 和失败/越权场景。

Docker/MySQL 启动见根 README。Agent 默认关闭；离线演示设置 `AGENT_ENABLED=true`、`AGENT_PROVIDER=fake`。真实 provider 通过 `AGENT_BASE_URL`、`AGENT_API_KEY`、`AGENT_MODEL` 注入，不能写入 YAML、前端、migration 或文档。

## 已知限制

- 单审核人串行审核；无多级/并行会签。
- 无开放聊天、多 Agent、向量数据库、长期记忆、WebSocket、定时周报或 Agent 写工具。
- 无二进制附件内容 Diff、OCR、Office 内容解析或附件全文检索。
- Agent Artifact 是派生报告，不是项目事实；模型质量仍取决于配置的真实 provider。
- Playwright 输出有 React Router v7 future flag 警告；不影响 8/8 通过。

## 交付后 Agent 限额修复（2026-07-27）

真实模型运行曾出现 `LIMIT_EXCEEDED`：实际仅 5 次 provider 请求，但 5 组 MODEL request/response、10 组 TOOL call/result 与启动 Trace 共生成超过 30 条 `agent_steps`，旧 Harness 错把 Trace 行数当成 `maxSteps`。现已将执行动作预算与 Trace 计数分离，保留完整 Trace/Replay 的同时，不再因审计条目数量提前失败。

同时新增不可变 Prompt v2，要求批量请求独立工具、禁止相同参数重复调用、限制 Record Summary 的 revision detail/diff 深挖并在证据足够后立即结束；完全相同的工具参数优先命中 Run 内缓存并返回停止重复 notice。真实 provider 请求数由独立的 `AGENT_MAX_MODEL_CALLS=6` 硬限制，避免通过单纯放大 step 额度掩盖循环。

修复后实际验证：Agent 定向 19/19 通过，后端全量 99 tests（0 failures、0 errors、1 个默认跳过的联网 smoke），显式真实 provider 合成数据 smoke 1/1 通过。历史 Run 保持不可变；后端重启后，新建或 rerun 才会固定 Prompt v2 与新 limits。
