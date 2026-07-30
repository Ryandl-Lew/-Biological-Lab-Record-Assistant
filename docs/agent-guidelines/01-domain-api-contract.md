# 01：统一领域、权限与 API 契约

本文件先于任何业务批次生效。code agent 不得自行发明第二套状态名、角色名或相互矛盾的接口。

## 1. 统一枚举

### 1.1 项目角色

| 值         | 含义                                                           |
| ---------- | -------------------------------------------------------------- |
| `OWNER`    | 唯一项目负责人；管理项目、邀请和角色，也可创建记录和担任审核人 |
| `MEMBER`   | 编辑成员；可创建并管理自己创建的记录                           |
| `REVIEWER` | 审核者；项目只读，仅审核明确指派给自己的提交                   |

同一用户在同一项目只有一个角色。`OWNER` 必须同时固化在 `projects.owner_id` 和成员关系中，并由业务约束保持一致。

### 1.2 项目状态

- `ACTIVE`
- `ARCHIVED`

归档不可逆，不能暴露恢复接口。

### 1.3 记录状态

- `IN_PROGRESS`
- `IN_REVIEW`
- `CHANGES_REQUESTED`
- `COMPLETED`

状态只能由业务动作转换，禁止通用 PATCH 直接传目标状态。

### 1.4 邀请状态

- `PENDING`
- `ACCEPTED`
- `REJECTED`
- `EXPIRED`

读取或执行动作时，若 `expires_at <= now`，必须先把仍为 `PENDING` 的邀请转为 `EXPIRED`。

### 1.5 模板范围与字段类型

模板范围：`SYSTEM`、`PERSONAL`。

字段类型：

- `SINGLE_LINE_TEXT`
- `MULTI_LINE_TEXT`
- `NUMBER`
- `DATE`
- `SELECT`
- `FILE`

`SELECT` 必须有 1 个以上非空、去重后的选项。`FILE` 的值是同一记录下附件 ID 的数组或单值，不保存文件路径。

### 1.6 审核状态

审核轮次：`PENDING`、`CHANGES_REQUESTED`、`APPROVED`。

每条记录同一时刻只能有一个 `PENDING` 审核轮次。

## 2. 权限矩阵

| 动作                              |       OWNER        | MEMBER |     REVIEWER     |
| --------------------------------- | :----------------: | :----: | :--------------: |
| 查看项目和项目内未删除记录        |         是         |   是   |        是        |
| 创建记录                          |         是         |   是   |        否        |
| 编辑/删除自己创建且状态允许的记录 |         是         |   是   |        否        |
| 编辑/删除他人记录                 |         否         |   否   |        否        |
| 管理自己记录的附件                |         是         |   是   |        否        |
| 邀请、移除成员、改角色            |         是         |   否   |        否        |
| 归档项目                          |         是         |   否   |        否        |
| 担任指定审核人                    | 是，但不能审核自己 |   否   | 是，但必须被指派 |
| 审核未指派给自己的记录            |         否         |   否   |        否        |
| 搜索/下载/导出有权项目内容        |         是         |   是   |        是        |

项目归档后，所有角色只能查看、搜索、下载附件、导出已完成记录。所有写操作和审核动作都返回明确的状态冲突错误。

## 3. 状态机与不变量

### 3.1 记录状态机

```text
创建 -> IN_PROGRESS
IN_PROGRESS --保存--> IN_PROGRESS
IN_PROGRESS --提交--> IN_REVIEW
IN_REVIEW --退回--> CHANGES_REQUESTED
CHANGES_REQUESTED --保存--> CHANGES_REQUESTED
CHANGES_REQUESTED --再次提交--> IN_REVIEW
IN_REVIEW --通过--> COMPLETED
```

不变量：

- 只有不变的 `creator_id` 可以保存、管理附件、提交和在允许状态下软删除记录。
- `IN_REVIEW`、`COMPLETED` 的工作副本和附件均不可修改。
- 提交不可撤回。
- 退回必须有非空审核意见；通过意见可空。
- 每次提交创建新 `RecordRevision` 和新 `Review`，修订号从 1 递增，展示为 `R1`、`R2`。
- 审核针对 revision，不针对可变工作副本。
- `COMPLETED` 使用最终批准 revision 作为详情和导出依据，不再允许编辑或删除。

### 3.2 模板快照

创建记录时必须把模板的名称、版本和全部有序字段复制到 `template_snapshot_json`。后续模板重命名、编辑或软删除不能改变已有记录。

### 3.3 附件快照

提交时把当前未删除附件通过 `revision_attachments` 关联到该 revision。后续在退回状态软删除工作副本附件时，历史 revision 仍能查看和下载该文件；物理文件在 MVP 中不做自动清理。

### 3.4 乐观锁

- `Project`、`RecordTemplate`、`ExperimentRecord` 使用 `version`。
- 更新请求携带期望版本；不一致返回 HTTP 409 `OPTIMISTIC_LOCK_CONFLICT`。
- 审核决定还必须校验 record 状态、review 状态和当前 revision ID，旧页面不能覆盖新结果。

### 3.5 幂等

提交、接受邀请、拒绝邀请、审核决定、归档、导出审计写入支持重复请求保护。

推荐客户端为关键 POST 发送 `Idempotency-Key`。服务端至少通过唯一约束和当前状态保证重复点击只产生一个 revision、review、成员关系、通知和审计事件。

## 4. 核心数据模型

字段可按 JPA 习惯调整，但语义不得缺失。

### 4.1 身份

`users`

- `id`
- `display_name`
- `email_normalized`，唯一
- `password_hash`
- `avatar_storage_key`，可空
- `created_at`、`updated_at`
- `version`

### 4.2 项目协作

`projects`

- `id`、`name`、`description`
- `status`
- `owner_id`
- `created_at`、`updated_at`、`archived_at`
- `version`

`project_members`

- `project_id`、`user_id` 联合唯一
- `role`
- `joined_at`、`last_active_at`

`project_invitations`

- `id`、`project_id`
- `inviter_id`、`invitee_user_id`
- `invitee_email_snapshot`
- `status`、`expires_at`
- `created_at`、`responded_at`
- 同项目、同用户最多一个有效 `PENDING` 邀请

### 4.3 模板

`record_templates`

- `id`、`scope`
- `owner_id`：系统模板为空，个人模板为当前用户
- `name`、`name_normalized`
- `experiment_type`、`category`、`description`
- `created_at`、`updated_at`、`deleted_at`
- `version`

`template_fields`

- `id`、`template_id`
- `field_key`：稳定且在模板内唯一
- `label`、`field_type`、`required`
- `sort_order`
- `placeholder`
- `default_value_json`
- `options_json`

### 4.4 记录与审核

`experiment_records`

- `id`、`code`、`project_id`、`creator_id`
- `title`、`experiment_type`、`experiment_date`、`purpose`
- `status`
- `template_snapshot_json`
- `field_values_json`
- `content_json`、`content_html_sanitized`、`content_plain_text`
- `current_revision_no`、`current_review_id`
- `version`
- `created_at`、`updated_at`、`deleted_at`

`record_revisions`

- `id`、`record_id`、`revision_no`
- `snapshot_json`：固定字段、模板快照、字段值、净化正文和附件元数据
- `content_hash`
- `submitted_by`、`submitted_at`
- `(record_id, revision_no)` 唯一

`reviews`

- `id`、`record_id`、`revision_id`
- `reviewer_id`
- `status`
- `submit_note`、`decision_comment`
- `submitted_at`、`decided_at`
- `version`

### 4.5 文件、通知、审计

`attachments`

- `id`、`record_id`
- `original_name`、`storage_key`
- `mime_type`、`size_bytes`
- `uploaded_by`
- `created_at`、`deleted_at`

`revision_attachments`

- `revision_id`、`attachment_id` 联合唯一

`notifications`

- `id`、`recipient_id`
- `type`、`title`、`body`
- `payload_json`
- `dedup_key`，唯一
- `created_at`、`read_at`

`audit_events`

- `id`、`actor_id`
- `project_id`、`record_id`，可空
- `event_type`
- `target_type`、`target_id`
- `metadata_json`
- `created_at`

审计表不提供更新和删除 repository 方法。

## 5. API 统一规范

基础路径：`/api/v1`。

### 5.1 成功响应

单对象：

```json
{ "data": { "id": "..." } }
```

分页：

```json
{
  "data": [/* items */],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

删除或无正文操作可返回 204。

### 5.2 错误响应

```json
{
  "timestamp": "2026-07-23T08:00:00Z",
  "status": 409,
  "code": "RECORD_STATE_CONFLICT",
  "message": "当前记录已进入审核中，不能继续编辑",
  "fieldErrors": { "reviewerId": "审核人不能是记录创建者" },
  "traceId": "..."
}
```

最少区分：

- `AUTHENTICATION_REQUIRED`：401
- `ACCESS_DENIED`：403
- `RESOURCE_NOT_FOUND`：404
- `VALIDATION_ERROR`：400
- `PROJECT_ARCHIVED`：409
- `RECORD_STATE_CONFLICT`：409
- `OPTIMISTIC_LOCK_CONFLICT`：409
- `DUPLICATE_RESOURCE`：409
- `INVITATION_EXPIRED`：409
- `INVALID_REVIEWER`：422 或 400，项目内保持一致
- `FILE_TOO_LARGE`、`INVALID_FILE_TYPE`：400/413

前端按 `code` 给出可执行提示，不统一显示“操作失败”。

### 5.3 分页和排序

- `page` 从 0 开始，默认 0。
- `size` 默认 20，最大 100。
- 服务端定义允许排序字段白名单，禁止把任意参数拼接到 SQL。
- 项目、记录、搜索、通知和审计列表必须分页。

## 6. API 资源清单

具体 DTO 由批次实现，但路径保持稳定。

### 6.1 身份与用户

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout`
- `GET /auth/me`
- `PUT /users/me`
- `POST /users/me/avatar`
- `GET /users/{userId}/avatar`

### 6.2 项目、成员、邀请

- `GET /projects`
- `POST /projects`
- `GET /projects/{projectId}`
- `POST /projects/{projectId}/archive`
- `GET /projects/{projectId}/members`
- `POST /projects/{projectId}/invitations`
- `POST /invitations/{invitationId}/accept`
- `POST /invitations/{invitationId}/reject`
- `PATCH /projects/{projectId}/members/{userId}/role`
- `POST /projects/{projectId}/members/{userId}/remove`

角色变更和移除请求允许携带审核重新指派列表，并在同一事务完成。

### 6.3 模板

- `GET /templates`
- `POST /templates`
- `GET /templates/{templateId}`
- `PUT /templates/{templateId}`
- `DELETE /templates/{templateId}`
- `POST /templates/{templateId}/copy`

系统模板只允许 GET 和使用。

### 6.4 记录与审核

- `GET /records`
- `POST /records`
- `GET /records/{recordId}`
- `PUT /records/{recordId}`
- `DELETE /records/{recordId}`
- `GET /records/{recordId}/revisions`
- `GET /records/{recordId}/revisions/{revisionId}`
- `POST /records/{recordId}/submissions`
- `POST /records/{recordId}/reviews/{reviewId}/request-changes`
- `POST /records/{recordId}/reviews/{reviewId}/approve`
- `GET /reviews/assigned-to-me`

### 6.5 附件与报告

- `GET /records/{recordId}/attachments`
- `POST /records/{recordId}/attachments`
- `DELETE /attachments/{attachmentId}`
- `GET /attachments/{attachmentId}/preview`
- `GET /attachments/{attachmentId}/download`
- `GET /records/{recordId}/exports/preview`
- `GET /records/{recordId}/exports/markdown`
- `GET /records/{recordId}/exports/pdf`

### 6.6 通知、搜索、审计、工作台

- `GET /notifications`
- `PATCH /notifications/{notificationId}/read`
- `POST /notifications/read-all`
- `GET /search`
- `GET /projects/{projectId}/audit-events`
- `GET /dashboard/tasks`
- `GET /dashboard/summary`

## 7. 通知事件

MVP 至少生成：

- `PROJECT_INVITATION`：受邀用户，可直接接受/拒绝。
- `INVITATION_ACCEPTED`、`INVITATION_REJECTED`：项目负责人。
- `PROJECT_ROLE_CHANGED`：角色被修改的成员。
- `MEMBER_REMOVED`：被移除成员。
- `REVIEW_ASSIGNED`：指定审核人。
- `CHANGES_REQUESTED`：记录创建者。
- `RECORD_APPROVED`：记录创建者。
- `REVIEWER_REASSIGNED`：新旧审核人和记录创建者按需接收。
- `PROJECT_ARCHIVED`：全部项目成员。

同一业务事件使用稳定 `dedup_key`，接口重试不得重复通知。

## 8. 审计事件

MVP 至少记录：

- `PROJECT_CREATED`、`PROJECT_ARCHIVED`
- `INVITATION_CREATED`、`INVITATION_ACCEPTED`、`INVITATION_REJECTED`、`INVITATION_EXPIRED`
- `MEMBER_ROLE_CHANGED`、`MEMBER_REMOVED`
- `TEMPLATE_CREATED`、`TEMPLATE_UPDATED`、`TEMPLATE_DELETED`
- `RECORD_CREATED`、`RECORD_UPDATED`、`RECORD_DELETED`
- `RECORD_SUBMITTED`、`REVIEW_CHANGES_REQUESTED`、`REVIEW_APPROVED`、`REVIEWER_REASSIGNED`
- `ATTACHMENT_UPLOADED`、`ATTACHMENT_DELETED`
- `RECORD_EXPORTED_PDF`、`RECORD_EXPORTED_MARKDOWN`

下载和预览不要求逐次审计，以避免噪声；若实现，只能作为额外事件，不能影响主流程。

## 9. 搜索边界

- 只搜索当前用户参与项目中的项目、记录、成员和附件元数据，以及可见的系统模板/自己的个人模板。
- 记录可搜索标题、编号、实验类型、目的、净化后的正文纯文本和模板字段中的文本值。
- 附件只搜索文件名和 MIME/类型元数据，不解析附件正文。
- 被移除成员、软删除记录/附件/模板、无权项目不能出现在结果或计数中。
- 支持 `keyword`、`entityType`、`projectId`、`creatorId`、`recordStatus`、分页和稳定排序。

## 10. 导出边界

- 只有 `COMPLETED` 记录可预览和导出。
- 数据来源必须是最终批准 revision，不读取可能变化的工作副本。
- PDF/Markdown 至少包含：记录标题与编号、项目、实验类型/日期、创建者、目的、模板字段、正文、附件清单、最终审核人和审核时间。
- PDF 必须验证中文字体正常，无方框或乱码。
- 用户必须仍是项目参与者；被移除后旧链接立即失效。
