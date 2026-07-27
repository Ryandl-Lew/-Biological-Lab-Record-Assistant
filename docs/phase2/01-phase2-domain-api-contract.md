# 01：第二阶段统一领域、数据与 API 契约

本文件冻结第二阶段各模块之间的公共协议。实现批次不得改变这里的字段语义、权限或路径来减少局部代码量。若确需调整，必须先更新本文并通知所有并行分支。

## 1. 统一术语

| 术语 | 定义 |
|---|---|
| Working Copy | `experiment_records` 中当前可变工作副本；不是历史 revision |
| Record Revision | 每次提交审核产生的不可变快照，展示为 R1、R2、R3 |
| Revision Summary | 列表使用的轻量投影，不含完整 snapshot 和大正文 |
| Revision Detail | 单一 revision 的完整可见快照、附件、提交和审核信息 |
| Revision Diff | 两个 snapshot source 之间的结构化领域差异 |
| Restore Preview | 基于当前工作副本即时计算的恢复影响，不产生写入 |
| Restore Operation | 已确认并成功提交的恢复动作，只追加保存 |
| Agent Run | 一次有界 Agent 执行实例 |
| Agent Step | Run 中可观测的模型、工具、验证、输出或错误步骤 |
| Agent Artifact | 验证通过并持久化的记录总结或项目进展报告 |
| Evidence Reference | Artifact 中指向真实项目对象或 Diff 的稳定证据引用 |
| Trace Replay | 读取并重现已存 step 时间线，不重新调用模型或工具 |

## 2. 既有不变量

- 项目角色仍为 `OWNER`、`MEMBER`、`REVIEWER`。
- 项目状态仍为 `ACTIVE`、`ARCHIVED`，归档不可逆。
- 记录状态仍为 `IN_PROGRESS`、`IN_REVIEW`、`CHANGES_REQUESTED`、`COMPLETED`。
- 只有记录创建者可编辑、提交、删除或恢复自己的可编辑记录。
- `IN_REVIEW` 与 `COMPLETED` 不可编辑或恢复。
- revision、review、revision attachment 和 audit event 不被更新正文或物理删除。
- 项目参与者可查看项目内 revision 和 diff；非项目成员不可查看。
- 已归档项目允许查看 revision、diff、Agent 历史报告，不允许恢复或生成依赖写入项目状态的动作。

## 3. 新增枚举

### 3.1 Diff source

- `REVISION`
- `WORKING_COPY`

`WORKING_COPY` 只能作为同一 record 的 source，且返回调用时的 `recordVersion`。

### 3.2 Diff section kind

- `SCALAR`
- `TEMPLATE_FIELD`
- `RICH_TEXT`
- `ATTACHMENT_SET`
- `REVIEW_METADATA`

### 3.3 Diff status

- `UNCHANGED`
- `ADDED`
- `REMOVED`
- `MODIFIED`

默认 API 可以省略 `UNCHANGED` section；请求 `includeUnchanged=true` 时返回。

### 3.4 Text operation

- `EQUAL`
- `INSERT`
- `DELETE`

### 3.5 Agent artifact kind

- `RECORD_SUMMARY`
- `PROJECT_PROGRESS`

### 3.6 Agent run status

- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `LIMIT_EXCEEDED`
- `INVALID_OUTPUT`

终态为 `SUCCEEDED`、`FAILED`、`CANCELLED`、`LIMIT_EXCEEDED`、`INVALID_OUTPUT`。

### 3.7 Agent step type

- `RUN_STARTED`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `VALIDATION`
- `ARTIFACT_SAVED`
- `RUN_FAILED`

不得增加 `CHAIN_OF_THOUGHT`、`REASONING` 等用于保存隐藏推理的类型。

### 3.8 Agent trigger type

- `MANUAL`
- `DOMAIN_EVENT`
- `RERUN`

第二阶段默认只开放 `MANUAL` 和 `RERUN`；`DOMAIN_EVENT` 由 feature flag 控制。

### 3.9 Evidence type

- `PROJECT`
- `RECORD`
- `REVISION`
- `REVIEW`
- `AUDIT_EVENT`
- `REVISION_DIFF`

## 4. 数据库迁移编号与所有权

V1-V8 已应用，禁止修改。第二阶段固定分配：

| Migration | 所有者 | 内容 |
|---|---|---|
| `V9__phase2_revision_metadata.sql` | Revision 模块 | revision schema version、历史查询索引 |
| `V10__create_record_restore_operations.sql` | Restore 模块 | 恢复操作、幂等和来源追溯 |
| `V11__create_agent_runtime.sql` | Agent Runtime | prompt、run、step、artifact 表和索引 |
| `V12__phase2_integration_indexes.sql` | 集成负责人 | 经实际查询计划验证后补充的跨模块索引；无必要则不创建，但编号继续保留 |

不得为了分支方便使用未分配的 V9/V10/V11 文件名，也不得在两个 migration 中重复创建相同对象。

## 5. 数据模型

### 5.1 `record_revisions` 增量字段

V9 新增：

- `snapshot_schema_version INT NOT NULL DEFAULT 1`

现有 revision 均视为 schema version 1。新快照仍保持当前字段语义；如果未来升级，Normalizer 必须根据版本转换成统一内部模型。

索引建议：

- `(record_id, revision_no DESC)`
- 不为 `snapshot_json` 建普通索引。

### 5.2 `record_restore_operations`

| 字段 | 规则 |
|---|---|
| `id CHAR(36)` | 主键 |
| `record_id CHAR(36)` | 目标记录，外键 |
| `source_revision_id CHAR(36)` | 来源 revision，外键 |
| `actor_id CHAR(36)` | 执行创建者，外键 |
| `before_record_version BIGINT` | 执行前乐观锁版本 |
| `after_record_version BIGINT` | 执行后版本，必须大于 before |
| `before_content_hash CHAR(64)` | 执行前规范化工作副本哈希 |
| `after_content_hash CHAR(64)` | 恢复后规范化工作副本哈希 |
| `diff_summary_json TEXT` | 实际执行时重新计算的差异摘要 |
| `idempotency_key VARCHAR(160)` | 非空 |
| `payload_hash CHAR(64)` | 规范化执行请求哈希，用于识别同 key 不同 payload |
| `restored_at TIMESTAMP(6)` | UTC |

约束：

- `UNIQUE(record_id, idempotency_key)`。
- 表只提供 INSERT 和查询，不提供 UPDATE/DELETE。
- 来源 revision 必须属于同一 record，由服务校验；可选数据库触发器不作为主要保证。

### 5.3 `prompt_versions`

| 字段 | 规则 |
|---|---|
| `id CHAR(36)` | 主键 |
| `prompt_name VARCHAR(100)` | 例如 `project-progress` |
| `version_no INT` | 从 1 递增 |
| `template_text TEXT` | 完整 system/template 文本，不含 secret |
| `output_schema_json TEXT` | 该版本要求的 JSON Schema |
| `tool_policy_json TEXT` | 允许的 tool name 和限制 |
| `content_hash CHAR(64)` | 规范化模板与 schema 的 SHA-256 |
| `active BOOLEAN` | 是否为新 run 默认版本 |
| `active_name_key VARCHAR(100) NULL` | active 行写 prompt_name，inactive 写 NULL，用唯一键保证单 active |
| `created_at TIMESTAMP(6)` | UTC |

约束：

- `UNIQUE(prompt_name, version_no)`。
- 同名 prompt 最多一个 active；由事务和 active key 保证。
- 已被 run 引用的 prompt version 不更新或删除；新内容创建新版本。
- 第二阶段不提供普通用户编辑 prompt 的 UI。

### 5.4 `agent_runs`

| 字段 | 规则 |
|---|---|
| `id CHAR(36)` | 主键 |
| `artifact_kind VARCHAR(30)` | `RECORD_SUMMARY`/`PROJECT_PROGRESS` |
| `subject_type VARCHAR(20)` | `RECORD`/`PROJECT` |
| `subject_id CHAR(36)` | 目标 ID |
| `project_id CHAR(36)` | 授权与查询范围，外键 |
| `record_id CHAR(36) NULL` | record run 时非空，外键 |
| `requested_by CHAR(36)` | 发起用户，外键 |
| `trigger_type VARCHAR(20)` | 统一枚举 |
| `status VARCHAR(30)` | run 状态 |
| `provider VARCHAR(60)` | 实际 provider |
| `model VARCHAR(120)` | 实际 model |
| `prompt_version_id CHAR(36)` | 外键，创建时固定 |
| `parent_run_id CHAR(36) NULL` | rerun 来源 |
| `idempotency_key VARCHAR(160)` | 非空 |
| `request_json TEXT` | 经过长度校验的原始 run 参数，如 focus/period；不含 secret |
| `payload_hash CHAR(64)` | 规范化 run 请求哈希，用于幂等冲突判断 |
| `input_cursor_json TEXT` | 时间窗、截止 audit cursor、subject version 等 |
| `limits_json TEXT` | 本 run 固定限制快照 |
| `step_count INT` | 默认 0 |
| `tool_call_count INT` | 默认 0 |
| `input_tokens BIGINT` | 默认 0 |
| `output_tokens BIGINT` | 默认 0 |
| `error_code VARCHAR(80) NULL` | 稳定机器码 |
| `error_message VARCHAR(1000) NULL` | 脱敏消息 |
| `cancel_requested_at TIMESTAMP(6) NULL` | RUNNING run 的协作式取消标记 |
| `created_at/started_at/finished_at` | UTC |
| `version BIGINT` | worker 领取与状态更新乐观锁 |

约束：

- `UNIQUE(requested_by, idempotency_key)`。
- `record_id` 与 `subject_type` 组合由服务校验。
- 终态不可回到运行态；rerun 创建新行。

### 5.5 `agent_steps`

| 字段 | 规则 |
|---|---|
| `id CHAR(36)` | 主键 |
| `run_id CHAR(36)` | 外键 |
| `step_no INT` | run 内从 1 递增 |
| `step_type VARCHAR(30)` | 统一枚举 |
| `tool_name VARCHAR(100) NULL` | tool step 时使用 |
| `request_json TEXT NULL` | 裁剪、脱敏后的参数或模型 envelope |
| `response_json TEXT NULL` | 裁剪、脱敏后的结果摘要 |
| `content_hash CHAR(64)` | step 可见内容哈希 |
| `latency_ms BIGINT` | 非负 |
| `input_tokens/output_tokens BIGINT` | 可空或 0 |
| `created_at TIMESTAMP(6)` | UTC |

约束：

- `UNIQUE(run_id, step_no)`。
- 不保存 API key、Authorization、完整附件、任意大正文和 hidden reasoning。

### 5.6 `agent_artifacts`

| 字段 | 规则 |
|---|---|
| `id CHAR(36)` | 主键 |
| `run_id CHAR(36)` | 唯一外键，一次成功 run 一个 artifact |
| `artifact_kind VARCHAR(30)` | 与 run 一致 |
| `project_id CHAR(36)` | 外键 |
| `record_id CHAR(36) NULL` | record summary 时非空 |
| `content_json TEXT` | 通过 Schema 的结构化输出 |
| `evidence_json TEXT` | 规范化证据索引，也可从 content 派生 |
| `content_hash CHAR(64)` | artifact 规范化哈希 |
| `created_at TIMESTAMP(6)` | UTC |

artifact 只追加。重新生成创建新 run 和新 artifact。

## 6. Revision DTO 契约

### 6.1 Revision summary

```json
{
  "id": "uuid",
  "recordId": "uuid",
  "revisionNo": 2,
  "label": "R2",
  "submittedBy": "uuid",
  "submitterName": "用户",
  "submittedAt": "2026-07-26T10:00:00Z",
  "submitNote": "补充对照组",
  "contentHash": "...",
  "review": {
    "id": "uuid",
    "reviewerId": "uuid",
    "reviewerName": "审核者",
    "status": "APPROVED",
    "decisionComment": "数据完整",
    "decidedAt": "..."
  },
  "attachmentCount": 2,
  "current": false,
  "final": true
}
```

列表不返回 `snapshot`、`contentHtml`、完整附件数组。

### 6.2 Snapshot source reference

```json
{
  "type": "REVISION",
  "revisionId": "uuid",
  "revisionNo": 1,
  "recordVersion": null,
  "contentHash": "..."
}
```

工作副本：

```json
{
  "type": "WORKING_COPY",
  "revisionId": null,
  "revisionNo": null,
  "recordVersion": 7,
  "contentHash": "..."
}
```

### 6.3 Diff result

```json
{
  "recordId": "uuid",
  "from": { "type": "REVISION", "revisionId": "...", "revisionNo": 1 },
  "to": { "type": "REVISION", "revisionId": "...", "revisionNo": 2 },
  "summary": {
    "added": 2,
    "removed": 1,
    "modified": 4,
    "unchanged": 8,
    "attachmentAdded": 1,
    "attachmentRemoved": 1
  },
  "sections": [
    {
      "key": "field:ct_summary",
      "label": "Ct 值汇总",
      "kind": "TEMPLATE_FIELD",
      "valueType": "MULTI_LINE_TEXT",
      "status": "MODIFIED",
      "before": "...",
      "after": "...",
      "textHunks": []
    }
  ],
  "truncated": false,
  "generatedAt": "..."
}
```

## 7. Restore DTO 契约

### 7.1 Preview request

```json
{
  "sourceRevisionId": "uuid",
  "expectedRecordVersion": 7,
  "restoreAttachments": true
}
```

### 7.2 Preview response

```json
{
  "recordId": "uuid",
  "sourceRevision": { "id": "uuid", "revisionNo": 1 },
  "expectedRecordVersion": 7,
  "previewToken": "signed-or-random-server-token",
  "expiresAt": "2026-07-26T10:05:00Z",
  "diff": { "summary": {}, "sections": [] },
  "warnings": [
    "将重新启用 1 个历史附件",
    "将软删除当前工作副本新增的 2 个附件"
  ],
  "capabilities": { "canExecute": true }
}
```

preview token 必须绑定用户、record、source revision、expected version、restoreAttachments 和过期时间。可以使用短期服务端表/缓存，也可以使用服务端签名 token；不得信任客户端回传的 Diff。

### 7.3 Execute request

```json
{
  "sourceRevisionId": "uuid",
  "expectedRecordVersion": 7,
  "restoreAttachments": true,
  "previewToken": "..."
}
```

成功返回更新后的 `RecordDtos.View` 和 restore operation summary；重复幂等请求返回同一 operation 的结果。

## 8. Agent DTO 契约

### 8.1 Create run

记录总结：

```http
POST /api/v1/records/{recordId}/agent-runs
Idempotency-Key: ...

{
  "artifactKind": "RECORD_SUMMARY",
  "focus": "重点总结审核反馈和版本变化"
}
```

项目进展：

```http
POST /api/v1/projects/{projectId}/agent-runs
Idempotency-Key: ...

{
  "artifactKind": "PROJECT_PROGRESS",
  "periodStart": "2026-07-20T00:00:00Z",
  "periodEnd": "2026-07-26T23:59:59Z",
  "focus": "课程周报"
}
```

`focus` 最大 500 字，只作为用户目标，不改变工具权限和系统限制。

创建成功立即返回 202：

```json
{
  "data": {
    "id": "uuid",
    "status": "QUEUED",
    "artifactKind": "PROJECT_PROGRESS",
    "createdAt": "..."
  }
}
```

### 8.2 Artifact Schema

公共结构：

```json
{
  "schemaVersion": 1,
  "headline": "一句话标题",
  "executiveSummary": "简要总结",
  "progress": [
    {
      "id": "p1",
      "statement": "完成了 R2 审核",
      "evidenceRefs": ["e1"]
    }
  ],
  "risks": [
    {
      "id": "r1",
      "statement": "两条记录仍需修改",
      "severity": "MEDIUM",
      "evidenceRefs": ["e2"]
    }
  ],
  "nextActions": [
    {
      "id": "a1",
      "statement": "补充对照组说明",
      "basis": "REVIEW_FEEDBACK",
      "evidenceRefs": ["e3"]
    }
  ],
  "evidence": [
    {
      "ref": "e1",
      "type": "REVISION",
      "id": "uuid",
      "recordId": "uuid",
      "label": "EXP-001 R2"
    }
  ],
  "limitations": [],
  "period": { "start": "...", "end": "..." }
}
```

约束：

- `headline`、`executiveSummary` 非空且有长度上限。
- progress/risk 中的事实性 statement 至少一个 evidenceRef。
- next action 若是模型建议而非现有审核要求，`basis` 必须为 `MODEL_SUGGESTION`，UI 明确标记建议。
- evidence ref 唯一，引用必须存在。
- 不允许模型凭空生成 owner、截止日期、实验结果或审核结论。

## 9. API 路径

### 9.1 Revision 与 Diff

- `GET /api/v1/records/{recordId}/revisions?page=0&size=20`
- `GET /api/v1/records/{recordId}/revisions/{revisionId}`
- 兼容现有 `GET /api/v1/revisions/{revisionId}`，但新前端使用嵌套路径。
- `GET /api/v1/records/{recordId}/revision-diff?fromRevisionId=...&toRevisionId=...`
- `GET /api/v1/records/{recordId}/revision-diff?fromRevisionId=...&to=WORKING_COPY`

两个 revision 必须属于路径 record，否则返回 404，不能跨记录比较。

### 9.2 Restore

- `POST /api/v1/records/{recordId}/restore-preview`
- `POST /api/v1/records/{recordId}/restore`
- `GET /api/v1/records/{recordId}/restore-operations?page=0&size=20`

执行接口要求 `Idempotency-Key`。

### 9.3 Agent

- `POST /api/v1/records/{recordId}/agent-runs`
- `POST /api/v1/projects/{projectId}/agent-runs`
- `GET /api/v1/agent-runs/{runId}`
- `GET /api/v1/agent-runs/{runId}/steps?page=0&size=50`
- `POST /api/v1/agent-runs/{runId}/cancel`
- `POST /api/v1/agent-runs/{runId}/rerun`
- `GET /api/v1/records/{recordId}/agent-artifacts?page=0&size=20`
- `GET /api/v1/projects/{projectId}/agent-artifacts?page=0&size=20`
- `GET /api/v1/agent-artifacts/{artifactId}`

Replay 直接使用 run 与 steps 查询，不需要单独写接口。

## 10. 权限矩阵

| 动作 | OWNER | MEMBER | REVIEWER |
|---|:---:|:---:|:---:|
| 查看项目内 revision | 是 | 是 | 是 |
| 比较项目内同记录 revision | 是 | 是 | 是 |
| 比较 revision 与工作副本 | 是 | 是 | 是，仅读取可见工作副本 |
| 执行恢复 | 仅自己创建的可编辑记录 | 仅自己创建的可编辑记录 | 否 |
| 查看恢复历史 | 是 | 是 | 是 |
| 生成自己的记录总结 | 是 | 是 | 否 |
| 查看项目内记录总结 | 是 | 是 | 是 |
| 生成项目进展报告 | 是 | 否 | 否 |
| 查看项目进展报告 | 是 | 是 | 是 |
| 查看自己有权 subject 的 run trace | 是 | 是 | 是 |
| 取消 run | run 发起人；OWNER 可取消本项目 run | run 发起人 | run 发起人 |
| rerun | 按“新建同类 run”权限重新判断 | 同左 | 同左 |

建议第一版只允许记录创建者生成 `RECORD_SUMMARY`，避免任意成员消耗模型额度。查看仍按项目参与者权限。

项目归档后允许查看和手动生成只读报告，由产品选择控制；本契约默认允许 OWNER 在归档项目生成 `PROJECT_PROGRESS`，因为不修改项目事实。若实现团队希望禁止，必须在开始开发前统一修改本文，不能前后端各自决定。

## 11. 错误码

新增稳定错误码：

| HTTP | Code | 含义 |
|---|---|---|
| 400 | `INVALID_DIFF_SOURCE` | source 参数非法或组合不支持 |
| 404 | `REVISION_NOT_FOUND` | revision 不存在、非本记录或不可见 |
| 409 | `RESTORE_NOT_ALLOWED` | 当前项目/记录状态不允许恢复 |
| 409 | `RESTORE_PREVIEW_STALE` | preview 过期或 record version 已变化 |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | expected version 不一致 |
| 409 | `IDEMPOTENCY_CONFLICT` | 相同 key 被用于不同 payload |
| 400 | `AGENT_INVALID_REQUEST` | run 请求参数非法 |
| 409 | `AGENT_ALREADY_TERMINAL` | 对终态 run 取消等非法动作 |
| 503 | `AGENT_DISABLED` | Agent 功能未启用 |
| 503 | `MODEL_PROVIDER_UNAVAILABLE` | provider 不可用或未配置 |
| 504/500 | `AGENT_TIMEOUT` | 超时 |
| 422 | `AGENT_INVALID_OUTPUT` | 输出不符合 Schema |
| 422 | `AGENT_EVIDENCE_INVALID` | evidence 无效、越权或无法支撑结构 |
| 429 | `AGENT_RATE_LIMITED` | 频率或并发额度超限 |
| 409 | `AGENT_RUN_NOT_CANCELLABLE` | 当前状态不能取消 |

无权 subject 继续使用现有 404 不可见策略。

## 12. 幂等规则

### 12.1 Restore

- 幂等范围为 `(record_id, idempotency_key)`。
- 首次请求保存 payload hash。
- 相同 key、相同 payload 返回原 operation 和当前 record 结果。
- 相同 key、不同 payload 返回 `IDEMPOTENCY_CONFLICT`。

### 12.2 Agent run

- 幂等范围为 `(requested_by, idempotency_key)`。
- 同 key 同 payload 返回原 run，无论其是否终态。
- rerun 必须使用新 key，并设置 `parent_run_id`。

## 13. 分页、排序和时间

- revision 默认按 `revision_no DESC`。
- restore operation 默认按 `restored_at DESC, id DESC`。
- agent run/artifact 默认按 `created_at DESC, id DESC`。
- step 固定按 `step_no ASC`。
- 默认 size 20；step 默认 50；最大 100。
- 数据库存 UTC，API 返回 ISO-8601 UTC，前端本地化显示。

## 14. OpenAPI 与兼容性

- 新 API 必须在 springdoc 中可见，并说明权限、状态、幂等 header 和错误码。
- 不删除第一阶段现有 revision endpoint；先添加嵌套路径并让旧路径委托同一 service。
- `ReviewDtos.RevisionView` 可以保留给旧端点，但新代码使用独立 `RevisionDtos`，避免审核模块继续承担版本查询职责。
- 前端 API 新建 `revisions.js`、`restore.js`、`agentRuns.js`，不得继续把功能塞入名称不匹配的 `assist.js`。
