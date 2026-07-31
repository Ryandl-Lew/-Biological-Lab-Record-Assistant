# 07：批次四——前端版本治理与 Agent 体验

## 本批次目标

在现有视觉语言中整合 Revision、Diff、Restore 和 Agent，而不是新建孤立 AI 聊天页面。版本功能放在记录上下文，项目进展报告放在项目上下文，所有操作展示真实权限、状态、证据和失败原因。

## 1. 前置依赖

- Revision、Diff、Restore、Agent API 已稳定。
- `revisions.js`、`restore.js`、`agentRuns.js` 已存在。
- 公共 DTO 与错误码不再变动。

## 2. 文件归属与冲突控制

版本 UI 负责人独占：

```text
frontend/src/components/revision/**
frontend/src/components/restore/**
frontend/src/pages/RecordDetailMvpPage.jsx
frontend/src/pages/RecordDetailMvpPage.test.jsx
```

Agent UI 负责人独占：

```text
frontend/src/components/agent/**
frontend/src/pages/ProjectDetailMvpPage.jsx
frontend/src/pages/ProjectDetailMvpPage.test.jsx
```

共享热点由集成负责人最后处理：

```text
frontend/src/api/index.js
frontend/src/router/index.jsx
frontend/src/domain/enums.js
frontend/src/components/layout/Sidebar.jsx
```

推荐不新增顶级 `/ai` 路由和侧栏“AI 助手”。Agent 是项目/记录的上下文能力。

## 3. 记录详情信息架构

现有详情按基本信息、模板字段、正文、附件、审核、修订历史纵向排列。第二阶段建议使用标签或清晰分区：

```text
记录详情
  |-- 当前内容
  |-- 版本历史
  |-- AI 记录总结
```

不强制修改 URL；建议用 query 参数保留可分享状态：

```text
/records/{id}?tab=history
/records/{id}?tab=summary
```

Diff 可以使用 query：

```text
/records/{id}?tab=history&from={r1}&to={r2}
```

刷新后应恢复选择，不把完整 Diff 放入客户端全局 store。

## 4. Revision History UI

### 4.1 列表

每项展示：

- Rn。
- 提交人/时间。
- submit note 摘要。
- review 状态和 reviewer。
- 附件数量。
- `最终批准版本`、`当前审核版本`标记。

默认加载第一页 20 条。支持上一页/下一页或“加载更多”，不能一次请求完整 snapshots。

### 4.2 详情

点击 revision：

- 打开右侧抽屉/页面内面板。
- 延迟请求详情。
- 展示固定字段、模板字段、正文、附件、提交和审核。
- 正文使用 DOMPurify；附件通过现有授权 Blob 流程预览/下载。
- 不把需要 Bearer 的裸 URL 放进 iframe/img。

### 4.3 选择比较

提供两个明确选择：

- `基准版本` from。
- `目标版本` to，可选择“当前工作副本”。

默认：

- 打开 R2 时 from=R1、to=R2。
- 只有 R1 时可 from=R1、to=当前工作副本，若工作副本可见。
- 同一个 source 不能比较，按钮禁用并说明。

## 5. Diff UI

### 5.1 概览

顶部展示：

- `R1 -> R2`。
- 修改、新增、删除计数。
- 附件新增/删除数。
- 生成时间和是否截断。

### 5.2 Section 渲染

- SCALAR：两列 before/after，变化值突出。
- TEMPLATE_FIELD：显示字段 label/type，NUMBER/DATE/SELECT 用值对比，多行文本用 hunks。
- RICH_TEXT：按 block 展示，删除红色、插入绿色、相同折叠。
- ATTACHMENT_SET：新增、移除、保留列表；按钮仍走授权下载。
- REVIEW_METADATA：单独“提交与审核变化”，不混在实验内容计数。

颜色不能作为唯一信息。使用 `+`/`−`、文本标签和可读 aria-label。

### 5.3 响应式

- `>=1024px`：before/after 双栏。
- `<1024px`：每个 section 上下堆叠。
- 约 768px 可完整选择版本、查看 hunk 和操作恢复。
- 大正文 section 默认折叠，仅展开差异附近上下文。

### 5.4 状态

- loading skeleton。
- 无差异 empty state。
- truncated warning。
- 404/权限错误。
- source 过期：对 working copy 重新加载。
- 网络失败可重试，不清除用户的 from/to 选择。

## 6. Restore Preview UI

### 6.1 入口

只在 revision history 中显示“恢复为工作副本”，并同时满足：

- `record.capabilities.canRestore`。
- 当前 source 是历史 revision。
- record 非 IN_REVIEW/COMPLETED。

前端能力字段只是体验；后端仍校验。

### 6.2 两阶段交互

第一步：点击恢复，调用 preview。

第二步：确认弹窗展示：

- 来源 revision。
- 当前 record version。
- Diff summary 和受影响 section。
- 将重新启用/软删除的附件。
- warnings。
- `restoreAttachments` 选择，默认 true。
- 明确说明：历史不会被删除；下次提交将生成新的 Rn。

如果用户切换 restoreAttachments，必须重新请求 preview，不在前端自行改附件计划。

### 6.3 执行

- 生成一个 idempotency key，并在同一次重试中复用。
- 提交期间禁用关闭/重复点击或明确说明后台状态。
- 409 `RESTORE_PREVIEW_STALE`：保留弹窗，提示工作副本已变化，并提供“重新生成预览”。
- 成功：清理本地 record/revision/diff cache，跳转 `/records/{id}/edit`，显示“已从 Rn 恢复，历史版本未改变”。
- 不宣称创建了新 revision，直到用户实际再次提交。

## 7. Record Summary UI

在记录详情增加“AI 记录总结”标签：

- 最新成功 artifact。
- 生成时间、provider/model、prompt version（可放详情）。
- headline、summary、版本演进、审核反馈、风险、下一步、limitations。
- evidence chip 可跳转：
  - REVISION -> 版本详情。
  - REVIEW -> 对应审核区。
  - REVISION_DIFF -> 自动选择 from/to 并打开 Diff。
- 模型建议用“AI 建议”标签；审核要求用“审核反馈”标签。

只有 creator 显示“生成/重新生成”。其他成员可查看已有 artifact。

没有 revision 时允许生成，但 UI 应显示报告 limitation。

## 8. Project Progress UI

在项目详情现有 tabs 中增加：

```text
智能进展
```

不要修改现有“时间线”语义。

页面包含：

1. 最新报告卡片。
2. 时间窗口选择，默认最近 7 天，最大 90 天。
3. OWNER 的“生成进展报告”按钮。
4. 历史 artifact 列表。
5. 运行中状态与取消入口。
6. 失败详情和 rerun。
7. evidence 跳转。

报告展示顺序：

- headline/executive summary。
- 进展。
- 风险/阻塞。
- 下一步。
- 证据。
- 局限性。

状态统计可以使用报告 artifact 内的结构或额外真实 API，但必须标明实时统计与报告截止时间，不能混淆。

## 9. Agent Run 状态与轮询

创建返回 202 后：

- 立即显示 QUEUED。
- 仅在 QUEUED/RUNNING 轮询，例如 2 秒、3 秒、5 秒退避。
- 页面隐藏/卸载时停止定时器。
- 终态停止轮询。
- 重新进入页面可通过 run ID 恢复状态。

状态文案：

| 状态           | 用户文案                     |
| -------------- | ---------------------------- |
| QUEUED         | 等待处理                     |
| RUNNING        | 正在收集证据并生成报告       |
| SUCCEEDED      | 已生成                       |
| FAILED         | 生成失败，可查看原因并重试   |
| CANCELLED      | 已取消                       |
| LIMIT_EXCEEDED | 达到运行限制，未生成报告     |
| INVALID_OUTPUT | 模型结果未通过结构或证据校验 |

不要用前端 setTimeout 伪造步骤进度。

## 10. Trace Viewer

成功或失败 run 都可打开“查看运行轨迹”。

展示：

- run ID、artifact kind、trigger、provider/model、prompt version、时间、耗时、token 统计。
- step 时间线。
- TOOL_CALL：tool name、参数摘要。
- TOOL_RESULT：结果数量、证据候选、是否截断、耗时。
- VALIDATION：通过或错误摘要。
- 最终 artifact 链接。

不展示：

- chain-of-thought。
- API key/header。
- 完整大正文/附件。
- provider 原始敏感响应。

Trace Replay 是按 step_no 逐步展开已保存数据，可提供播放/下一步按钮，但不重新发送请求。

## 11. 前端组件建议

```text
components/revision/
  RevisionHistoryPanel.jsx
  RevisionDetailDrawer.jsx
  RevisionSelector.jsx
  RevisionDiffViewer.jsx
  ScalarDiffSection.jsx
  TextDiffSection.jsx
  AttachmentDiffSection.jsx

components/restore/
  RestorePreviewDialog.jsx
  RestoreWarningList.jsx

components/agent/
  AgentRunStatus.jsx
  AgentArtifactView.jsx
  EvidenceLink.jsx
  AgentTraceViewer.jsx
  ProgressReportPanel.jsx
  RecordSummaryPanel.jsx
```

组件拆分应围绕职责，不要把整页复制成 `Phase2RecordPage` 与旧页并存。

## 12. 错误处理

按 code 处理：

- `REVISION_NOT_FOUND`：版本已不可用或无权。
- `RESTORE_NOT_ALLOWED`：展示后端具体状态原因，刷新 record。
- `RESTORE_PREVIEW_STALE`：提供重新预览。
- `OPTIMISTIC_LOCK_CONFLICT`：保留当前 UI 选择并刷新。
- `AGENT_DISABLED`：隐藏/禁用生成入口，旧 artifact 仍可看。
- `MODEL_PROVIDER_UNAVAILABLE`：允许稍后 rerun。
- `AGENT_INVALID_OUTPUT/EVIDENCE_INVALID`：说明没有保存不可靠报告。
- `AGENT_RATE_LIMITED`：显示可重试时间。

不得统一显示“操作失败”。

## 13. 测试

### 13.1 Revision

- 分页历史和状态 badge。
- 选择 R1/R2 请求正确 query。
- source 相同按钮禁用。
- scalar/template/text/attachment/review section。
- truncated/empty/error。
- outsider 不渲染数据。

### 13.2 Restore

- canRestore false 不显示入口。
- preview warnings。
- 切换附件重新请求 preview。
- execute 使用 preview token、version、idempotency key。
- stale 后重新预览。
- 成功跳转编辑器并提示历史未覆盖。

### 13.3 Agent

- OWNER 生成项目报告，普通成员无生成按钮。
- creator 生成记录总结。
- 202 后轮询，终态停止。
- 页面卸载清理 timer。
- artifact Schema 各 section。
- evidence 跳转 Diff。
- trace 不展示敏感/推理字段。
- cancel/rerun。
- disabled/failure/invalid output/rate limit。

### 13.4 Accessibility

- 版本选择 label。
- Diff insert/delete 不只靠颜色。
- Dialog focus trap/关闭。
- Trace step 可键盘展开。
- loading status 使用适当 aria-live，避免高频播报。

## 14. E2E 补充

新增或扩展：

1. `revision-diff-restore.spec.js`
   - R1 退回 -> 修改 -> R2。
   - 比较 R1/R2。
   - 当前 CHANGES_REQUESTED 恢复 R1。
   - 再提交生成 R3。
   - R1/R2 内容不变。
2. `agent-progress.spec.js`
   - 使用 fake/demo provider 创建 run。
   - 等待 SUCCEEDED。
   - 查看 artifact、evidence 和 trace。
   - evidence 跳转正确 Diff。
3. `phase2-authorization.spec.js`
   - outsider revision/diff/artifact/trace 失败。
   - OWNER 不能恢复他人记录。
   - MEMBER 不能生成项目报告。

## 15. 构建门禁

```powershell
cd frontend
npm.cmd run lint
npm.cmd test -- --run
npm.cmd run build
npm.cmd run test:e2e
```

检查生产 bundle；Diff 运算主要在后端，不应引入巨大的前端 Diff 库。如果新增路由级页面明显增大 bundle，使用 `React.lazy` 做路由/面板懒加载。

## 16. 完成报告

必须说明：

- 记录页和项目页新增信息架构。
- 响应式和可访问性处理。
- 所有后端错误码对应体验。
- 轮询清理和重复请求保护。
- E2E 实际结果。
- 是否存在未完成/临时入口。
