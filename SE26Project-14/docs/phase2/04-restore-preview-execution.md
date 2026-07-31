# 04：批次二 B——恢复预览与事务恢复

## 本批次目标

实现“先预览、后确认”的安全恢复流程。恢复必须基于不可变 revision 创建新的工作副本内容，不修改任何历史 revision/review，并使用对象级权限、乐观锁、行锁、幂等键和审计保证可靠性。

## 1. 前置依赖

- 批次一的 `RecordActionPolicy` 和类型化事件已合并。
- 批次二 A 的 DTO 与 `RevisionDiffService` 契约已冻结。
- 可以在 Diff 实现尚未合并时基于接口写 fake 和测试，但最终不得保留本模块私有 Diff。

## 2. 文件归属

本批次独占：

```text
backend/src/main/java/com/bionote/restore/**
backend/src/test/java/com/bionote/restore/**
backend/src/main/resources/db/migration/V10__create_record_restore_operations.sql
frontend/src/api/restore.js
```

原则上不修改：

```text
backend/src/main/java/com/bionote/record/RecordService.java
backend/src/main/java/com/bionote/review/ReviewService.java
frontend/src/pages/RecordDetailMvpPage.jsx
```

恢复通过独立 Controller/Service 完成。记录页面由批次七统一修改。

## 3. 后端组件

```text
restore/
  RestoreController.java
  RestoreDtos.java
  RestorePreviewService.java
  RestoreExecutionService.java
  RestoreOperationRepository.java
  RestorePreviewTokenService.java
  AttachmentRestorePlanner.java
  WorkingCopySnapshotWriter.java
```

职责：

- `RestorePreviewService`：权限预检、加载 source/current、调用 Diff、规划附件、签发 token。
- `RestoreExecutionService`：短事务内重新校验、锁定、写入工作副本和操作日志。
- `AttachmentRestorePlanner`：只计算需要激活/软删除/保留的附件 ID。
- `WorkingCopySnapshotWriter`：将规范化/原始 revision snapshot 安全映射回 experiment_records 字段。
- `RestorePreviewTokenService`：防止客户端篡改 preview 参数。

## 4. 恢复语义

### 4.1 可恢复内容

从来源 revision 恢复：

- title
- experiment_type
- experiment_date
- purpose
- template_snapshot_json
- field_values_json
- content_json
- content_html_sanitized
- content_plain_text
- 选择恢复附件时的活动附件集合

来源 snapshot 中的 identity：

- record id
- code
- project id
- creator id

只能用于一致性校验，绝不能写回或改变。

以下字段不从历史复制：

- status
- current_revision_no
- current_review_id
- final_revision_id
- created_at
- deleted_at
- creator_id/project_id/code
- 当前 record version

### 4.2 状态

- 当前 `IN_PROGRESS` 恢复后仍为 `IN_PROGRESS`。
- 当前 `CHANGES_REQUESTED` 恢复后仍为 `CHANGES_REQUESTED`，保留最近审核意见可见性。
- `IN_REVIEW`、`COMPLETED` 拒绝恢复。
- provisional 记录拒绝恢复。

### 4.3 Revision 编号

恢复本身不插入 `record_revisions`，因为 revision 只代表正式提交审核快照。恢复后下一次提交使用现有规则：

```text
new revision no = current_revision_no + 1
```

例如已有 R1、R2，恢复 R1 后再提交生成 R3。不得把 working copy 显示成 R1，也不得生成 R1-copy。

### 4.4 模板结构

恢复完整来源 `templateSnapshot`，因为模板字段值必须按该结构解释。正常情况下同一记录模板结构不变；若检测到历史异常差异，Preview 必须警告，执行仍以来源快照为准。

## 5. 附件恢复

### 5.1 `restoreAttachments=true`

目标是让当前活动附件集合精确等于来源 revision 的附件集合：

- 来源 revision 中、当前已软删除的附件：清除 `deleted_at`，重新激活。
- 当前活动但不属于来源 revision 的附件：设置 `deleted_at`，保留底层文件。
- 两边都有：保持活动。
- 历史 revision 的 `revision_attachments` 不变化。

不得物理删除文件。恢复后，来源 revision 的 FILE 字段引用应全部指向活动附件。

### 5.2 `restoreAttachments=false`

- 当前附件活动状态不变。
- 对来源 field values 中 FILE 字段做安全处理：只保留当前仍活动且属于该 record 的附件 ID。
- Preview 必须列出被丢弃的历史文件字段引用。
- UI 默认建议完整恢复附件，用户明确选择才允许只恢复内容。

### 5.3 文件完整性

如果 revision 关联的 attachment row 存在但物理文件缺失：

- Preview 返回 warning 和 `canExecute=false`，默认禁止完整附件恢复。
- 不允许生成看似成功但无法访问附件的工作副本。
- `restoreAttachments=false` 仍可执行内容恢复，但 FILE 引用按当前活动文件过滤。

## 6. Preview 流程

1. 验证用户仍是项目成员。
2. 加载 record 和项目状态。
3. 使用 `RecordActionPolicy.canRestore`。
4. 校验 source revision 属于 record。
5. 校验请求 `expectedRecordVersion` 等于当前版本。
6. 用 `RevisionDiffService` 比较 `WORKING_COPY -> sourceRevision`。
7. 生成 AttachmentRestorePlan。
8. 生成 warnings 和 capabilities。
9. 签发 5 分钟内有效的 preview token。

Preview 是只读操作，不获取长事务行锁，不写 audit，不修改附件。

### 6.1 Token 内容

token 至少绑定：

- actor ID
- record ID
- project ID
- source revision ID
- expected record version
- restoreAttachments
- current working copy canonical hash
- source canonical hash
- expiresAt
- 随机 nonce

统一使用服务端 HMAC-SHA256 签名 token：`base64url(canonicalJsonPayload).base64url(signature)`。密钥为独立环境变量 `RESTORE_PREVIEW_SECRET`，至少 32 字符；test/local 使用明确的非生产测试值。不得从 JWT token 读取、不得与 JWT secret 相同、不得把 secret 或完整 token 写入审计和日志。

token payload 中的 nonce 用于防止内容完全相同的预览 token 可预测，但第二阶段不要求 preview 一次性消费；最终幂等由 restore operation 保证。多实例只需共享同一 `RESTORE_PREVIEW_SECRET`，不依赖进程内 Map。

## 7. Execute 流程

`RestoreExecutionService.restore()` 使用单个短数据库事务：

1. 规范化并验证 `Idempotency-Key`。
2. 若已有相同 `(record,key)` operation：
   - payload hash 相同则返回原结果。
   - payload 不同返回 `IDEMPOTENCY_CONFLICT`。
3. 校验 preview token 的签名、绑定参数和有效期。
4. `SELECT record ... FOR UPDATE`。
5. 重新校验成员、项目状态、creator、记录状态和 provisional。
6. 校验数据库 `version == expectedRecordVersion`。
7. 重新加载 source revision 并校验 canonical hash。
8. 重新计算 current -> source Diff 和附件计划；不能信任 preview 返回值。
9. 更新 experiment_records 内容字段：
   - 使用 `WHERE id=? AND version=?`。
   - `version=version+1`。
   - `updated_at=now`。
10. 按 plan 更新附件 `deleted_at`。
11. 插入 `record_restore_operations`。
12. 发布 `RecordRestoredEvent`，同步写 audit。
13. 提交事务。

`payload_hash` 只包含规范化的 `recordId + sourceRevisionId + expectedRecordVersion + restoreAttachments`，不包含 preview token、nonce 或签发时间。因此同一逻辑恢复在网络重试时仍可命中原 operation；换 source/version/附件策略则视为不同 payload。

任何一步失败必须整体回滚。模型或 Agent 不参与恢复事务。

## 8. 并发与边界

### 8.1 Preview 后又保存

执行返回 409 `RESTORE_PREVIEW_STALE` 或 `OPTIMISTIC_LOCK_CONFLICT`。前端要求用户重新生成预览，不能自动使用新版本继续。

### 8.2 双击确认

相同 Idempotency-Key 只产生一个 operation、一次 version increment 和一条 audit event。

### 8.3 两个不同恢复请求并发

只有一个能以相同 expected version 成功；另一个冲突。

### 8.4 source revision 被修改

正常业务不允许修改 revision。如果数据库异常导致 source hash 与 preview 不同，执行拒绝并记录完整性错误。

### 8.5 恢复当前相同内容

Preview 可返回 zero diff。执行默认拒绝并提示“当前工作副本已与该版本一致”，避免无意义 version/audit；错误码可使用 `RESTORE_NO_CHANGES`（409）。如果团队决定允许，必须统一修改契约和测试。

## 9. V10 Migration

严格按 `01-phase2-domain-api-contract.md` 创建 `record_restore_operations`。

必须保存 `payload_hash CHAR(64) NOT NULL`，用于识别相同幂等 key 被用于不同执行 payload。

索引：

- `(record_id, restored_at DESC)`
- `(source_revision_id)`
- unique `(record_id,idempotency_key)`

MySQL 与 H2 test profile 都必须执行成功。

## 10. API 与能力字段

### 10.1 Preview

```http
POST /api/v1/records/{recordId}/restore-preview
```

无论前端是否显示按钮，后端完整校验。

### 10.2 Execute

```http
POST /api/v1/records/{recordId}/restore
Idempotency-Key: UUID
```

提交中禁止重复点击；网络超时重试必须复用同一 key。

### 10.3 History

```http
GET /api/v1/records/{recordId}/restore-operations?page=0&size=20
```

项目参与者可查看安全摘要，不返回 before/after 正文和 idempotency key。

### 10.4 Record capabilities

建议在 Record View 增加：

```json
"capabilities": {
  "canRestore": true
}
```

该字段用于体验，不替代 API 授权。若修改 RecordDtos 属于共享热点，由集成负责人合并。

## 11. 前端 API 封装

`frontend/src/api/restore.js`：

```javascript
previewRestore(recordId, input)
executeRestore(recordId, input, idempotencyKey)
fetchRestoreOperations(recordId, params)
```

API 封装负责：

- 统一 JSON、错误解析。
- execute header。
- 不在 API 层缓存 preview token。

本批次不修改页面。

## 12. 自动化测试

### 12.1 Preview

- R1 与当前工作副本生成正确反向 Diff。
- expected version 过期。
- source 不属于 record。
- outsider、非 creator、OWNER 非 creator。
- IN_REVIEW、COMPLETED、ARCHIVED、provisional。
- 附件激活/软删除计划正确。
- token 绑定参数且过期拒绝。
- 客户端篡改 diff 不影响执行。

### 12.2 Execute

- IN_PROGRESS 从 R1 恢复内容，状态不变，version +1。
- CHANGES_REQUESTED 从 R1 恢复，状态仍为 CHANGES_REQUESTED。
- revision/review hash 与行内容不变。
- 完整附件恢复精确匹配来源集合。
- 不恢复附件时 FILE 引用过滤。
- 缺失物理文件的行为。
- 相同 key 重试只产生一条 operation/audit。
- 相同 key 不同 payload 冲突。
- 两个并发 expected version 只有一个成功。
- audit metadata 无正文和 storage key。
- 恢复后提交生成下一个 revision no。

### 12.3 事务回滚

故意让 audit handler 或 operation insert 失败，确认：

- record 内容未改变。
- attachment deleted_at 未改变。
- version 未递增。

## 13. 人工验收

1. 找到已有 R1、R2 且当前为 CHANGES_REQUESTED 的记录。
2. 选择恢复 R1。
3. Preview 明确列出 R2 之后的字段、正文和附件变化。
4. 确认恢复。
5. 页面进入编辑器，内容为 R1，但修订历史仍同时存在 R1/R2。
6. 修改并提交，生成 R3。
7. 时间线显示“从 R1 恢复工作副本”。

## 14. 验收命令

```powershell
cd backend
.\mvnw.cmd -Dtest=*Restore*Test test
.\mvnw.cmd test

cd ..\frontend
npm.cmd test -- --run
npm.cmd run build
```

## 15. 完成报告

说明：

- preview token 的实现与多实例限制。
- 附件恢复的精确语义。
- 事务边界和锁策略。
- 幂等 payload hash 规则。
- 恢复后 revision 编号验证。
- 实际测试结果与已知限制。
