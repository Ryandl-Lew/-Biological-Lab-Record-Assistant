# 05：批次四——审核闭环、附件预览与记录导出

## 本批次目标

完成 MVP 最核心的业务闭环：记录创建者上传附件并提交不可变快照；指定审核人退回或通过；再次提交生成新修订；已完成记录只读并可预览/导出 PDF 和 Markdown。

## 1. 附件预览参考范围

本批次允许定向阅读：

- `SE26Project-14-p5/frontend/src/components/file/FileManager.jsx`
- `SE26Project-14-p5/frontend/src/api/files.js`
- `SE26Project-14-p5/backend/src/main/java/com/bionote/file/controller/FileController.java` 中 preview/download 响应头部分

只能参考图片/PDF 弹窗预览的交互形式与 inline 响应语义。不得复制：

- 项目级附件。
- 回收站和恢复。
- `MOCK_USER_ID`、缺失权限校验。
- 裸 preview URL 直接放入 iframe 的 Bearer 认证缺陷。
- P5 的导出、记录、审核或其他业务代码。

## 2. 附件后端

### 2.1 数据和存储

新增 `attachments`、`revision_attachments`。

硬约束：

- 每个附件必须且只能属于一条记录；数据库不设置 project attachment 字段。
- 原始文件名只作为元数据；磁盘对象使用 UUID storage key。
- 存储根目录来自环境变量，解析后的路径必须位于根目录内。
- MVP 单文件最大 20 MB，可配置。
- 建议允许：JPEG、PNG、WebP、PDF、TXT、MD、CSV、XLSX、DOCX；预览只支持 JPEG/PNG/WebP/PDF，其他只下载。
- 服务端同时检查扩展名、声明 MIME 和基础文件签名；拒绝空文件、双扩展欺骗和 SVG。
- 同名文件允许，以附件 ID 区分。
- 物理文件失败时不得保留有效数据库记录；数据库保存失败时清理刚写入的孤儿文件。

### 2.2 权限和状态

- 列表/预览/下载：当前用户仍是记录所属项目参与者。
- 上传/软删除：记录 creator、项目 ACTIVE、记录状态为 `IN_PROGRESS` 或 `CHANGES_REQUESTED`。
- `IN_REVIEW`、`COMPLETED` 禁止增删附件。
- DELETE 只设置 `deleted_at`；普通工作副本列表排除已删除附件。
- 已关联历史 revision 的软删除附件仍允许项目参与者通过 revision 详情预览/下载。
- 被移除成员即使知道附件 ID 也无法访问。

### 2.3 预览与下载响应

- preview：正确 `Content-Type`，`Content-Disposition: inline; filename*=UTF-8''...`，并设置 `X-Content-Type-Options: nosniff`。
- download：`Content-Disposition: attachment`，支持 RFC 5987 中文文件名。
- 不支持预览的类型返回 `PREVIEW_NOT_SUPPORTED`，前端提供下载，而不是在 iframe 中尝试解析 Office。
- 可支持 Range 请求以改善 PDF，但不是 MVP 阻塞项。

## 3. 附件前端

建立复用的记录附件组件，适配现有卡片风格：

- 上传按钮、进度、成功/失败、重试。
- 文件名、大小、上传者、上传时间。
- 图片/PDF：预览、下载、可选新窗口。
- 其他类型：下载，不显示无效预览按钮。
- creator 且状态允许时显示删除；其他人只读。
- 删除二次确认，明确“历史已提交修订仍保留该文件”。

认证实现：

1. 统一 API 客户端以 `responseType=blob` 请求 preview。
2. 使用 Blob 创建 Object URL。
3. 图片使用 `<img>`，PDF 使用受 sandbox 限制的 `<iframe>` 或 `<object>`。
4. 弹窗关闭、切换文件和组件卸载时 `URL.revokeObjectURL`。
5. 下载同样通过认证客户端获取 Blob，再解析 Content-Disposition。

不得把 `/attachments/{id}/preview` 裸 URL直接赋给需要认证的 iframe。

模板 `FILE` 字段在记录已创建后，允许从当前未删除附件中选择/关联；提交时后端验证附件属于同一记录。

## 4. 提交快照与审核数据

新增 `record_revisions`、`reviews`，并为 `experiment_records.current_review_id/current_revision_no` 建立约束。

### 4.1 提交审核

`POST /records/{recordId}/submissions`

请求至少包含：

- `reviewerId`
- `submitNote` 可空
- `expectedRecordVersion`

事务内执行：

1. 校验 creator、项目 ACTIVE、记录为 `IN_PROGRESS` 或 `CHANGES_REQUESTED`。
2. 校验固定字段和所有模板 required 字段；FILE 字段引用有效附件。
3. reviewer 必须是同项目 OWNER 或 REVIEWER，不能是 creator。
4. 校验没有其他 PENDING review。
5. 锁定/检查 record version，递增 revisionNo。
6. 生成完整不可变 snapshot 和 content hash。
7. 把当前未删除附件写入 `revision_attachments`。
8. 创建 `Review(PENDING)` 并把 record 设为 `IN_REVIEW`。
9. 创建 `REVIEW_ASSIGNED` 通知和 `RECORD_SUBMITTED` 审计。

全部操作同一事务。文件内容已提前存在，不在此事务复制物理文件。

重复 `Idempotency-Key` 或快速双击只能产生一个 revision/review。

### 4.2 退回修改

`POST /records/{recordId}/reviews/{reviewId}/request-changes`

- 只有该 PENDING review 的指定 reviewer。
- 意见 trim 后必填。
- 校验 review、revision、record 仍是当前审核轮次。
- 同一事务把 review 设为 `CHANGES_REQUESTED`、record 设为 `CHANGES_REQUESTED`。`current_review_id` 继续指向这条最新 review，供编辑页展示意见；下一次提交时替换为新 review。只有状态为 PENDING 的 review 能被决策。
- 通知 creator，写审计。
- 退回后 creator编辑的是工作副本；旧 revision 不变。

### 4.3 审核通过

`POST /records/{recordId}/reviews/{reviewId}/approve`

- 只有指定 reviewer；意见可空。
- 校验仍是当前 PENDING review。
- 同一事务把 review 设 `APPROVED`、record 设 `COMPLETED`，记录最终批准 revision。
- 通知 creator，写审计。
- 完成后工作副本、模板值和附件永久只读；MVP 无重开接口。

### 4.4 读取

- 记录在 `IN_REVIEW` 时，普通详情默认展示当前提交 revision，而非任何可变工作副本。
- `CHANGES_REQUESTED` 的 creator 编辑页展示工作副本，并在侧栏展示上一轮意见。
- revision 历史可列出 R1/R2 和结论，但本阶段不做差异对比或恢复。
- “待我审核”接口只返回 `reviewer_id=currentUser` 且 review PENDING 的记录。

## 5. 审核 UI

### 5.1 提交弹窗

- 审核人选项来自后端合法候选接口或项目成员数据过滤后的服务端再次校验结果。
- 不显示 creator；无合法审核人时禁用提交，并提示联系 OWNER 添加/调整审核者。
- 提交前自动执行一次保存或要求无未保存修改。
- 显示“生成不可变 Rn、提交后不可撤回、内容和附件锁定”。
- 提交成功跳转只读详情并显示修订号。

### 5.2 审核详情

- 仅指定 reviewer 看到审核卡；其他参与者看到“由某人审核中”。
- 审核人查看 revision snapshot，包括当时模板字段、正文和附件。
- 退回按钮在意见为空时禁用；通过可选意见。
- 请求期间禁用两个按钮；过期操作返回冲突并刷新详情。

### 5.3 修改与再次提交

- creator 在 `CHANGES_REQUESTED` 可继续编辑和管理附件。
- 页面醒目展示上一轮审核意见和 Rn。
- 再次提交生成新 revision，不能覆盖旧 revision 或评论。

## 6. 接回成员变更和移除

替换批次二剩余的 review guard：

- REVIEWER → MEMBER 或移除 reviewer 时，查找其所有 PENDING reviews。
- 请求必须为每条 review 提供新 reviewer；新 reviewer 仍需合法且不能是 creator。
- 角色变更/移除、全部 review reviewer 更新、通知和审计同一事务。
- 若原 reviewer 仍有审核权限且未发生角色变化/移除，OWNER 不能任意更换指定 reviewer。
- 重指派不修改 revision、record 状态或 revisionNo。

## 7. 导出

### 7.1 统一报告模型

建立 `RecordReportModel`，从最终批准 revision 构造一次，供 HTML 预览、Markdown 和 PDF 共用，避免三套数据拼装。

至少包含：

- 标题、编号、项目、实验类型、日期、创建者、目的。
- 模板字段按 snapshot 顺序展示。
- 净化后的富文本正文。
- 最终 revision、审核人、审核时间和意见。
- 最终 revision 的附件清单（文件名、大小、上传者/时间可选），不把附件二进制嵌入报告。

### 7.2 接口

- `/exports/preview`：返回可在前端新页或弹窗展示的安全 HTML/JSON 预览。
- `/exports/markdown`：UTF-8 Markdown Blob，中文文件名。
- `/exports/pdf`：PDF Blob。

约束：

- 记录必须 `COMPLETED`。
- 当前用户仍是项目成员。
- 数据只来自最终批准 revision。
- 生成成功后写对应导出审计；失败不写成功事件。
- 同一下载重试可产生多条导出事件，但通过 requestId 去重更佳。

PDF 推荐使用 OpenHTMLtoPDF 或等价开源库，并在资源中提供许可兼容的中文字体。必须实际渲染检查中文、分页、长表单字段和长文件名。

## 8. 测试要求

后端至少覆盖：

- creator 可在可编辑状态上传/删除，其他人不可；审核中/完成不可增删。
- 非成员不能用附件 ID 预览/下载。
- 文件类型、大小、空文件、路径穿越被拒绝。
- 图片/PDF preview 的 MIME 和 inline header；Office 预览被拒绝但可下载。
- 提交必填模板字段校验、合法 reviewer 校验、自审禁止。
- 提交生成 R1，退回后再提交生成 R2，R1 不变。
- 双击提交/审核只产生一个结果。
- 旧页面审核决定返回 409。
- 退回意见必填，通过意见可空。
- 历史 revision 仍能访问后来软删除的附件。
- reviewer 角色变化/移除的事务重指派。
- 未完成记录、非成员不能导出；完成记录导出使用最终 revision。

前端至少覆盖：

- 上传进度和错误提示。
- 预览 Object URL 创建与释放。
- 不支持预览类型只显示下载。
- 提交弹窗合法候选和无候选状态。
- 退回意见校验、过期审核冲突刷新。
- 仅 COMPLETED 显示导出按钮。

## 9. 人工验收

1. B 上传 PNG、PDF、DOCX；PNG/PDF 可弹窗预览，DOCX 只能下载。
2. 非项目用户复制预览/下载请求得到无权响应。
3. B 提交给 C 生成 R1；任何人不能继续修改 R1 内容和附件。
4. C 退回时空意见无法提交；填写意见后 B 可编辑。
5. B 删除一个附件、补充内容、再次提交生成 R2；R1 仍保留原附件访问。
6. C 通过 R2；记录变为永久只读。
7. PDF 和 Markdown 内容来自 R2，中文、模板字段和审核信息正确。
