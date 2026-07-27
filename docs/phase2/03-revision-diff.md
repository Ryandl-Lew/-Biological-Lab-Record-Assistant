# 03：批次二 A——Revision 查询与领域感知 Diff

## 本批次目标

把现有“审核模块顺便返回完整 revision”的实现升级为独立版本查询能力，并实现可供前端、恢复预览和 Agent Tool 复用的结构化 Diff。

本批次是版本能力的事实来源。Restore 和 Agent 不得复制 snapshot 解析与差异算法。

## 1. 前置条件

- 批次一公共策略和事件基础已合并。
- V1-V8 校验通过。
- 当前 `record_revisions`、`reviews`、`revision_attachments` 数据不被改写。
- 完整理解现有 `ReviewService.snapshot()` 写入的 schema version 1。

## 2. 文件归属

本批次独占：

```text
backend/src/main/java/com/bionote/revision/**
backend/src/test/java/com/bionote/revision/**
backend/src/main/resources/db/migration/V9__phase2_revision_metadata.sql
frontend/src/api/revisions.js
```

允许最小修改：

```text
backend/src/main/java/com/bionote/review/ReviewController.java
backend/src/main/java/com/bionote/review/ReviewService.java
```

修改目的仅为旧 endpoint 委托或消除重复查询；不要同时实现前端页面、恢复或 Agent。

## 3. 后端组件

推荐结构：

```text
revision/
  RevisionController.java
  RevisionDtos.java
  RevisionQueryService.java
  RevisionRepository.java
  SnapshotSourceResolver.java
  SnapshotNormalizer.java
  NormalizedRecordSnapshot.java
  RevisionDiffService.java
  ScalarDiffStrategy.java
  TemplateFieldDiffStrategy.java
  RichTextDiffStrategy.java
  AttachmentDiffStrategy.java
  TextDiffEngine.java
```

可以使用 JdbcTemplate，阶段二不要求把整个后端迁移到 JPA。SQL 必须集中在 repository/query 层，Controller 不拼 SQL。

## 4. Revision 查询重构

### 4.1 列表

当前实现先查 revision ID，再逐条调用详情，存在 N+1。新列表必须使用一次主查询加必要聚合/批量查询，返回分页摘要：

- revision 基本元数据。
- submitter、reviewer 展示名。
- review 状态、意见、时间。
- attachment count。
- 是否为当前审核 revision。
- 是否为最终批准 revision。

不得返回 snapshot_json 或附件详情。

SQL 结果稳定按：

```text
revision_no DESC, id DESC
```

### 4.2 详情

详情通过一次 revision/review/user 查询和一次 attachment 查询得到：

- snapshot schema version。
- 原始快照映射成受控 DTO。
- 内容 hash。
- 提交信息和审核信息。
- revision attachments。

附件访问仍由附件 endpoint 重新授权。Revision Detail 只返回安全元数据，不返回 storage key。

### 4.3 兼容

- 新路径：`/records/{recordId}/revisions/{revisionId}`。
- 旧路径：`/revisions/{revisionId}` 委托同一 query service。
- `GET /records/{recordId}/revisions` 按 `01` 契约统一改为分页摘要；同一批次更新全部现有前端消费者、API mock 和测试。不得增加临时 `/revision-summaries` 平行路径。单 revision 详情继续通过详情 endpoint 获取。
- 不允许长期维护两套不同业务逻辑。

## 5. Snapshot 规范化

### 5.1 目的

快照 JSON 包含固定字段、模板结构、字段值、TipTap JSON、HTML 和纯文本。直接比较 JSON/HTML 会受到 key 顺序、默认值、HTML 属性和空值形式影响。

`SnapshotNormalizer` 必须转换为确定性内部模型：

```java
public record NormalizedRecordSnapshot(
    SnapshotSourceRef source,
    UUID recordId,
    Map<String, ScalarValue> fixedFields,
    List<NormalizedTemplateField> templateFields,
    List<RichTextBlock> contentBlocks,
    List<NormalizedAttachment> attachments,
    ReviewMetadata reviewMetadata,
    String canonicalHash
) {}
```

当前 schema version 1 的受控逻辑结构为：

```json
{
  "id": "record uuid",
  "code": "EXP-...",
  "projectId": "project uuid",
  "creatorId": "user uuid",
  "title": "...",
  "experimentType": "...",
  "experimentDate": "2026-07-26",
  "purpose": "...",
  "templateSnapshot": {
    "name": "...",
    "version": 1,
    "fields": []
  },
  "fieldValues": {},
  "contentJson": {},
  "contentHtml": "...",
  "contentPlainText": "..."
}
```

历史附件不在 `snapshot_json` 内，必须从 `revision_attachments` 读取。Normalizer 不得假定 JSON 中存在 attachments。

### 5.2 固定字段

固定 key 和顺序：

1. `title`
2. `experimentType`
3. `experimentDate`
4. `purpose`

`id`、`code`、`projectId`、`creatorId` 属于不可变身份信息，默认不作为“变化 section”；详情可展示。如果检测到这些值在同记录 revision 中变化，视为数据完整性异常并记录错误，不静默展示为普通修改。

### 5.3 模板字段

- 根据 `templateSnapshot.fields` 的 `fieldKey` 对齐。
- 保留 label、fieldType、required、sortOrder。
- source 中不存在、target 中存在为 ADDED；反之 REMOVED。
- label 变化也要展示，但已有记录模板快照通常不变化。
- NUMBER 将数值规范化，`25` 与 `25.0` 视为相等；保留原展示值。
- DATE 使用 ISO 日期。
- FILE 先规范化成附件 ID 有序集合，再交给附件策略展示元数据。
- SELECT 使用字符串值，不按当前模板 options 重新解释历史。
- 未知类型不能丢失，使用字符串 fallback 并返回 warning。

### 5.4 富文本

优先解析 `contentJson`，按以下节点生成 block：

- paragraph
- heading，保留 level
- bullet/ordered list item
- blockquote
- code block
- horizontal rule

无法解析 JSON 时 fallback 到 `contentPlainText`。不得用 `contentHtml` 作为 Diff 输入。

每个 block 生成：

```text
type + normalizedText + ordinal
```

不要依赖 HTML class、style 或属性顺序。

### 5.5 附件

从 `revision_attachments` 和 `attachments` 读取：

- id
- original filename
- media type
- size bytes
- uploader
- created at

同一附件 ID 视为同一对象；文件名相同但 ID 不同仍是两个附件。

### 5.6 Canonical hash

对规范化结构使用稳定 key 顺序和 UTF-8 序列化计算 SHA-256。用于 Restore 的 before/after hash 只覆盖可恢复字段和活动附件 ID 集合，排除 record status、review metadata、更新时间与乐观锁 version。该 hash 用于 preview 校验和测试，不替换现有 revision `content_hash`。

## 6. Diff 策略

### 6.1 标量

返回 before/after 和状态。长文本 purpose 可以附加 text hunks。

### 6.2 模板字段

按 fieldKey 处理。输出 key 使用：

```text
field:{fieldKey}
```

对于多行文本生成 hunks；NUMBER、DATE、SELECT 默认只返回 before/after。

### 6.3 富文本正文

建议两层算法：

1. 对 block 序列使用 LCS/Myers，识别新增、删除和潜在修改块。
2. 对对应修改块的文本运行 word/character 或行级 Diff，生成 `TextOperation`。

不要求保留富文本每个 mark 的视觉差异。粗体变普通但文本相同可在第二阶段标记为 `formattingChanged=false/unsupported`，不应制造正文变化。

可使用轻量 Java Diff 库，但新增依赖必须由本批次负责人提交，并在 POM 说明许可证和版本。也可以实现受测的 Myers/LCS；不能使用二次方算法处理无限正文而无上限。

### 6.4 附件集合

输出：

- `addedAttachments`
- `removedAttachments`
- `unchangedAttachments`，仅 includeUnchanged 时

不对二进制内容做 Diff。相同 ID 的 filename/mediaType 理论上不可变；若异常变化，标记 metadataModified。

### 6.5 Review metadata

Revision 与 revision 比较时可展示：

- submit note。
- reviewer。
- review status。
- decision comment。

这些内容不计入“实验内容已修改”的主计数，单独放 `reviewChanges` 或 `REVIEW_METADATA` section，避免用户误解。

## 7. Source 解析

`SnapshotSourceResolver` 支持：

- `REVISION`：加载指定 revision，验证属于路径 record。
- `WORKING_COPY`：加载 `experiment_records` 当前内容和当前活动附件。

权限：

- 调用者必须是当前项目参与者。
- working copy 即使为 IN_PROGRESS 也可被参与者查看，沿用第一阶段规则。
- 软删除 record 和非成员返回 404。
- 不能跨 record 比较。

工作副本 source 返回准确 `recordVersion`。Diff 生成后若工作副本再次保存，客户端下次执行恢复预览必须重新请求。

## 8. 大内容与截断

配置建议：

```text
revision.diff.max-text-chars=100000
revision.diff.max-hunks=500
revision.diff.max-sections=500
```

达到限制时：

- 不抛出 OOM 或返回 500。
- 返回已有差异、`truncated=true` 和 warning。
- Restore Preview 如果关键内容 Diff 被截断仍可以执行恢复，但确认页必须明确“差异过大，仅展示部分”；执行使用完整快照，不使用裁剪结果写入。
- Agent Tool 默认使用 summary 和限定 hunks，不把巨大正文塞入上下文。

## 9. V9 Migration

只允许：

- 新增 `snapshot_schema_version`，默认 1、非空。
- 增加经 H2/MySQL 验证的 revision history 索引。

不得：

- 重写历史 snapshot_json。
- 重算或覆盖历史 content_hash。
- 增加 restore 或 Agent 表。

## 10. 前端 API 封装

`frontend/src/api/revisions.js` 提供：

```javascript
fetchRevisionSummaries(recordId, params)
fetchRevisionDetail(recordId, revisionId)
fetchRevisionDiff(recordId, { fromRevisionId, toRevisionId, to, includeUnchanged })
```

页面不得自行拼 query string；复用统一 client 和错误处理。

本批次只新增 API 封装和必要 mock 更新，不实现最终 UI。

## 11. 自动化测试

### 11.1 Query

- 30 个 revision 正确分页、稳定倒序。
- 列表不含 snapshot/正文。
- current/final 标记正确。
- outsider、已移除成员返回 404。
- 路径 record 与 revision 不匹配返回 404。
- 查询数量受控，避免按 revision 数量线性增长。

### 11.2 Normalizer

- JSON key 顺序不同得到同一 canonical hash。
- `25` 与 `25.0` 相等。
- null、空字符串、空数组行为明确。
- TipTap block 规范化稳定。
- 非法 contentJson fallback 到 plain text。
- schema version 1 解析。

### 11.3 Diff

- 标题、日期、purpose 修改。
- 模板字段 added/removed/modified/unchanged。
- 多行文本 hunks 正确。
- 正文新增、删除和修改段落。
- 附件 added/removed。
- review metadata 不混入内容计数。
- R1 vs R2、R1 vs WORKING_COPY。
- 跨记录 revision 被拒绝。
- 大内容截断。

### 11.4 回归

- R1 退回、R2 通过测试继续通过。
- 导出仍读取最终 revision。
- revision attachment 历史访问不受影响。

## 12. API 示例验收

```http
GET /api/v1/records/{recordId}/revisions?page=0&size=20
GET /api/v1/records/{recordId}/revisions/{r1Id}
GET /api/v1/records/{recordId}/revision-diff?fromRevisionId={r1Id}&toRevisionId={r2Id}
GET /api/v1/records/{recordId}/revision-diff?fromRevisionId={r1Id}&to=WORKING_COPY
```

确认：

- API 返回结构化 section，而非服务器拼装 HTML。
- 同样输入返回稳定 section 顺序和 hash。
- 无权访问不泄露 revision 是否存在。

## 13. 验收命令

```powershell
cd backend
.\mvnw.cmd -Dtest=*Revision*Test test
.\mvnw.cmd test

cd ..\frontend
npm.cmd test -- --run
npm.cmd run build
```

## 14. 完成报告

必须说明：

- Revision list 如何避免 N+1。
- Snapshot schema 和 canonical hash 规则。
- 正文 Diff 使用的算法/依赖和上限。
- 哪些内容明确不支持语义 Diff。
- API 兼容处理。
- 实际测试与性能观察。
