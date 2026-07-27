# 00：第二阶段总契约

## 1. 阶段目标

第二阶段在已完成的 BioNote MVP 上增加“版本治理”和“证据驱动 Agent Runtime”。目标是形成一套可以解释、恢复和总结实验协作过程的工程系统，而不是增加一个聊天入口。

核心价值：

1. 让用户明确知道两个审核提交版本之间改了什么。
2. 让创建者可以在不破坏历史的前提下安全回到某个旧版本。
3. 让系统通过领域工具收集证据，生成可验证的记录总结和项目进展报告。
4. 让 Agent 执行过程可追踪、可限制、可重放查看、可测试和可审计。
5. 通过清晰的模块边界支持多人并行开发和低冲突合并。

## 2. 需求优先级与冲突处理

第二阶段相关冲突按以下顺序处理：

1. 本文和 `01-phase2-domain-api-contract.md` 对第二阶段能力的明确约束。
2. `docs/01-product-overview.md` 中的领域规则、历史版本语义和 MVP 不变量。
3. 第一阶段 `00-master-contract.md`、`01-domain-api-contract.md` 中未被第二阶段明确覆盖的约束。
4. 当前批次文档。
5. 现有代码和页面行为。

第二阶段只覆盖第一阶段中的范围排除，不自动放宽权限、状态机、安全、软删除、归档、乐观锁和不可变快照规则。

## 3. 总体功能范围

### 3.1 Revision 浏览

- 分页查看记录的提交修订摘要。
- 查看单一 revision 的完整快照、附件、提交信息和审核结果。
- 清楚区分当前工作副本、审核中的 revision 和最终批准 revision。
- revision 使用现有 R1、R2、R3 编号，不创建另一套版本号。

### 3.2 Domain-aware Diff

- 比较任意两个同记录 revision。
- 比较一个 revision 与当前可见工作副本。
- 固定字段按标量比较。
- 模板字段按 `fieldKey` 对齐，并保留标签、类型和顺序语义。
- 正文按规范化块和文本 hunk 比较，不直接 Diff HTML。
- 附件按 ID 和元数据做集合差异。
- 审核信息作为独立区段展示。
- 返回机器可读结构，供 Web UI 和 Agent Tool 共同复用。

### 3.3 Restore Preview 与执行

- 恢复前先生成服务器端预览，展示固定字段、模板字段、正文和附件将发生的变化。
- 只有记录创建者、活动项目、可编辑记录状态可以执行恢复。
- 使用 record 行锁、期望版本、幂等键和数据库事务。
- 恢复只修改工作副本和附件软删除状态，不改变 revision、review 或审核结论。
- 保存来源 revision、执行人、执行前后 record version、Diff 摘要和时间。
- 恢复后下一次提交生成新的 revision。

### 3.4 类型化领域事件

- 为关键第二阶段动作定义不可变事件对象。
- 用事件处理器投影到 audit、notification 和可选 Agent trigger。
- 核心审计仍与业务事务保持一致；Agent 自动触发不得反向破坏核心事务。
- 不要求把第一阶段全部服务改造成 Event Sourcing。

### 3.5 Agent Harness Runtime

- 框架包含 Planner、Context Builder、Tool Registry、Tool Executor、Run Memory、Result Validator、Trace Recorder 和 Model Client 抽象。
- 工具使用 SPI/自动注册，新增工具不修改 Harness 主循环。
- 每个 run、step、tool call、验证结果和 artifact 都持久化。
- prompt 具有稳定名称、整数版本和内容哈希；run 固定引用具体版本。
- 支持 fake model 和至少一个可配置真实 provider adapter。
- 使用后台 worker 执行，支持超时、最大步数、最大工具调用数和取消/失败状态。

### 3.6 记录总结与项目进展报告

- `RECORD_SUMMARY`：总结单条记录的目的、当前状态、重要变化、审核反馈和下一步。
- `PROJECT_PROGRESS`：总结时间窗口内的已完成进展、进行中工作、阻塞/风险和建议下一步。
- 输出是受控 JSON artifact，而不是任意 Markdown。
- 每个事实性条目关联 evidence reference。
- UI 支持跳转到对应记录、revision、review 或 diff。

### 3.7 Trace 查看与 Replay

- 用户可以查看 run 的状态、配置、步骤时间线、工具名、参数摘要、结果摘要和验证结果。
- Replay 在第二阶段表示“按已保存 trace 重新展示执行过程”，不重新执行工具或模型。
- “再次运行”必须创建新 run，并通过 `parent_run_id` 关联旧 run，不能覆盖原 trace。
- 不存储或展示隐藏 chain-of-thought。

## 4. 总体架构

```text
React UI
  |-- Revision History / Diff / Restore Preview
  |-- Record Summary / Project Progress / Agent Trace
  |
REST Controllers
  |-- RevisionController
  |-- RestoreController
  |-- AgentRunController
  |-- AgentArtifactController
  |
Application Services
  |-- RevisionQueryService
  |-- RevisionDiffService
  |-- RestorePreviewService
  |-- RestoreExecutionService
  |-- AgentRunService / AgentWorker
  |
Domain and Runtime
  |-- RecordActionPolicy
  |-- DomainEventPublisher
  |-- AgentHarness
      |-- Planner
      |-- ContextBuilder
      |-- ToolRegistry / ToolExecutor
      |-- RunMemory
      |-- ResultValidator
      |-- TraceRecorder
      `-- ModelClient
  |
Persistence
  |-- existing record_revisions / reviews / revision_attachments
  |-- record_restore_operations
  |-- prompt_versions
  |-- agent_runs / agent_steps / agent_artifacts
  `-- audit_events
```

## 5. 架构原则

### 5.1 单一版本事实源

`record_revisions` 是用户可见历史版本的唯一事实源。不得新增 `record_versions`、`save_history` 等平行表来保存相同含义。

### 5.2 Diff 是共享领域能力

恢复预览、前端版本对比和 Agent Diff Tool 必须调用同一个 `RevisionDiffService`，不得出现三套差异算法。

### 5.3 恢复是显式业务动作

恢复不是通用 `PUT /records/{id}` 的一种隐藏模式，必须有独立 API、权限、幂等、事务和审计记录。

### 5.4 Agent 默认只读

Agent 只能通过类型化工具读取已授权信息。工具内部调用领域查询服务，不暴露 JdbcTemplate、Repository、SQL、URL 或文件路径给模型。

### 5.5 AI 结果不是业务事实

Agent artifact 是派生报告，不得直接改变记录状态、审核结果、项目统计或审计事实。页面必须标记生成时间、时间窗口、模型和证据范围。

### 5.6 可观测但不泄露推理

trace 用于调试执行行为和验证输入输出，不保存模型隐藏推理。允许保存：step 类型、工具调用、经过裁剪/脱敏的参数与结果、token/耗时、错误码、最终 Schema 输出。

### 5.7 失败必须显式

模型不可用、非法输出、无证据、工具失败、权限变化、超时、步数超限和用户取消都必须产生明确终态，不得留下内容为空的 `SUCCEEDED` artifact。

## 6. 安全底线

- 所有 revision、diff、restore、agent run 和 artifact 接口执行对象级授权。
- 非成员访问项目资源统一使用现有不可见资源策略，不泄露对象是否存在。
- 恢复执行仅允许记录创建者；前端按钮不是授权依据。
- Agent 工具在每次执行时重新检查 run 发起者对 subject 的权限，不能只在创建 run 时检查一次。
- 记录正文、模板值、审核意见、附件名和用户输入均是不可信数据；不得提升为 system instruction。
- 模型密钥只从环境变量读取，不写数据库、trace、异常详情或前端。
- tool arguments 必须经过 DTO/Schema 校验；未知字段、过长文本和非法 ID 拒绝。
- artifact evidence 必须在保存前验证对象存在、属于 subject 范围且用户有权访问。
- 真实模型关闭时，除 Agent 生成接口返回明确不可用错误外，第一阶段与版本功能必须正常工作。

## 7. 数据一致性底线

- revision、review 和 revision attachment 继续只追加。
- restore operation 只追加，不提供更新和删除 API。
- 恢复使用 `SELECT ... FOR UPDATE` 或等价行锁，并校验 `expectedRecordVersion`。
- restore preview 不持有数据库锁；执行时必须重新计算或验证 preview token，防止过期预览直接执行。
- Agent run 和 step 是运行日志，不反向作为记录/项目的事实源。
- artifact 保存与 run 进入 `SUCCEEDED` 必须在同一事务完成。
- 同一个幂等键只能产生一个 restore operation 或 agent run。
- 后台 worker 领取任务使用原子状态更新，避免多实例重复执行同一 run。

## 8. 性能和容量边界

- revision history 默认每页 20，最大 100，只返回摘要，不携带完整 snapshot。
- diff 一次只比较两个版本；单个正文超过配置上限时采用裁剪/摘要策略并返回 `truncated=true`。
- Agent 工具结果必须有条数和文本长度上限；禁止一次把项目全部正文塞入上下文。
- Agent 默认最多 8 个 step、6 次 tool call、60 秒墙钟时间；配置可收紧，不允许由普通请求任意扩大。
- Agent run 列表和 artifact 列表分页。
- 前端运行状态轮询只在 `QUEUED/RUNNING` 时进行，并采用退避或 2 秒以上间隔。

## 9. 配置契约

建议环境变量：

```text
AGENT_ENABLED=false
AGENT_PROVIDER=fake
AGENT_BASE_URL=
AGENT_API_KEY=
AGENT_MODEL=
AGENT_CONNECT_TIMEOUT_SECONDS=5
AGENT_READ_TIMEOUT_SECONDS=45
AGENT_MAX_STEPS=8
AGENT_MAX_TOOL_CALLS=6
AGENT_MAX_OUTPUT_TOKENS=2000
AGENT_WORKER_POLL_MS=1000
AGENT_AUTO_TRIGGER_ENABLED=false
RESTORE_PREVIEW_SECRET=
```

生产默认不得使用 fake provider；测试 profile 必须固定使用 fake provider，且不能读取真实 `AGENT_API_KEY`。
`RESTORE_PREVIEW_SECRET` 在启用恢复执行的共享/生产环境中必须是独立的至少 32 字符随机值，不能等于 JWT secret。

## 10. Code Agent 工作纪律

每个批次必须：

1. 检查工作区和前置批次实际状态。
2. 遵守 `09-team-ownership-merge-plan.md` 的文件归属。
3. 先建立测试和 DTO 契约，再实现服务和 UI。
4. 只新增分配给本批次的 Flyway migration。
5. 共享热点文件若非当前批次所有，提供待集成说明，不做大范围整理。
6. Controller 只做协议转换；权限、状态、事务、幂等和验证位于 application/domain service。
7. 不为赶进度使用静态成功、睡眠模拟 Agent、硬编码报告或测试专用绕权接口。
8. 运行批次测试和受影响的第一阶段回归。
9. 报告真实完成项、命令输出和未解决风险。

## 11. 通用 Definition of Done

- 正常、加载、空、无权、冲突、过期预览、模型不可用、运行失败、超时和非法输出均有清晰语义。
- 新 API 有 OpenAPI 描述、统一错误结构和前端封装。
- 新增写操作有 happy path、权限失败、状态失败、乐观锁/重复请求测试。
- Agent 核心有不联网单元测试，真实 provider 不成为 CI 前提。
- 新表具备必要外键、唯一约束、索引和 UTC 时间字段。
- 不修改 V1-V8，不物理删除不可变数据。
- 不记录 secret、隐藏推理、完整大正文或未经脱敏的模型请求。
- 前端约 768px 宽度可用，交互期间防止重复点击。
- 后端测试、前端测试、lint、build 和指定 E2E 实际通过。
