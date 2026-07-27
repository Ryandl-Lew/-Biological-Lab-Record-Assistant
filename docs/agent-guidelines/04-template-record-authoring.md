# 04：批次三——模板、记录创建与编辑

## 本批次目标

实现“选择项目和结构 → 填写固定字段 → 创建记录 → 编辑模板字段与富文本 → 保存/软删除”的真实创作链路。完成后记录仍停留在可编辑状态，提交审核和附件在下一批次接入。

## 1. 参考范围

本批次允许定向阅读：

- `SE26Project-14-P3/frontend/src/pages/TemplatesPage.jsx`
- `SE26Project-14-P3/frontend/src/components/template/TemplateCard.jsx`
- `SE26Project-14-P3/backend/src/main/resources/db/migration/V1__create_core_schema.sql` 中 `experiment_templates`、`template_fields` 片段

只借鉴“模板元数据 + 有序动态字段”的创建方式。不得阅读或复制 P3 的其他业务模块，不得照搬 TODO、mock 或记录实现。

## 2. 模板后端

### 2.1 数据

新增 `record_templates`、`template_fields`，结构遵循公共契约。

额外要求：

- 个人模板在 owner 范围内，未删除模板的 `name_normalized` 唯一。
- 系统模板 `owner_id=null`，普通用户不可修改/删除。
- 字段 `field_key` 在模板内唯一且创建后稳定；重排只改 `sort_order`。
- `default_value_json`、`options_json` 使用受控 schema，不接受任意可执行配置。

MySQL 不支持通用 partial unique index。不要误用包含 nullable `deleted_at` 的唯一键来保证“仅未删除名称唯一”；使用事务内校验并配合可唯一约束的 active-name key/generated column，或其他有数据库兜底的明确方案。

### 2.2 系统模板

通过 Flyway 或幂等 seed 初始化 2–3 个演示模板，例如 PCR、qPCR、细胞培养。字段只使用 MVP 支持类型。

系统模板必须可重复启动、不重复插入，并在所有用户可见。

### 2.3 CRUD

实现公共契约中的模板接口：

- 列表支持 `scope`、`category`、`keyword`、分页。
- 创建/更新一次性提交模板元数据和完整有序字段列表。
- 服务端重新生成连续 `sortOrder`，拒绝重复 fieldKey、空标签、非法默认值和 SELECT 空选项。
- 更新和删除只允许个人模板 owner。
- 删除为软删除。
- 复制系统/个人可见模板时，创建当前用户的新个人模板；名称冲突自动追加“副本”或返回可处理冲突，项目内保持一致。

模板的“使用次数”不要作为强一致业务字段；可通过记录引用统计，或 MVP 中不显示。禁止继续用 mock 固定次数。

## 3. 模板前端

保留现有模板中心视觉风格，完成真实交互：

- 系统模板与“我的模板”清晰分组。
- 新建模板使用完整字段编辑器，不再是虚线占位。
- 字段行支持：新增、删除、上移、下移；拖拽可选，不是必须。
- 每个字段编辑：名称、类型、必填、placeholder、默认值；SELECT 编辑选项；FILE 不允许默认文件。
- 实时校验模板名、重复字段 key/名称、SELECT 选项。
- 个人模板显示查看、编辑、复制、删除；系统模板只显示查看、复制/使用。
- 删除前确认“不会影响已创建记录”；成功后刷新列表。
- 查看弹窗按顺序展示字段，而不是只展示第一个 mock 模板。

推荐将大段单行 JSX 拆成 `TemplateEditor`、`TemplateFieldRow`、`TemplatePreviewDialog` 等组件。

## 4. 记录数据与创建

### 4.1 数据表

新增 `experiment_records`，包含公共契约字段。此批次状态只会产生 `IN_PROGRESS`。

记录 code 由服务端生成且唯一，例如 `EXP-20260723-XXXX`；不能由前端生成或依赖展示名。

### 4.2 创建接口

`POST /records` 输入：

- `projectId`
- `templateId` 可空
- `title`
- `experimentType`
- `experimentDate`
- `purpose`

规则：

- 当前用户必须是 ACTIVE 项目的 OWNER 或 MEMBER。
- 固定字段缺失时不创建数据库记录。
- creator 固定为当前用户。
- templateId 可空；不为空时必须是系统模板或当前用户未删除的个人模板。
- 在事务中复制模板结构到 `template_snapshot_json`。
- 初始化空 `field_values_json`、空富文本、`status=IN_PROGRESS`、`version=0`。
- 写 `RECORD_CREATED` 审计事件。

进入编辑器前的创建引导页只选择项目/结构，不应在没有固定字段时创建空数据库记录。

### 4.3 更新接口

`PUT /records/{id}` 输入当前 `version`、可编辑固定字段、模板字段值、`contentJson`、`contentHtml`。

规则：

- 只有 creator 且项目 ACTIVE。
- 只允许 `IN_PROGRESS`、`CHANGES_REQUESTED`，后者在下一批次启用。
- 不允许修改 project、creator、template snapshot 和 status。
- 服务端按 snapshot 验证字段类型；保存时允许必填模板字段暂空，提交时才强校验。
- 服务端使用 Jsoup 或等价白名单净化 HTML，生成 `content_plain_text`。
- `version` 不一致返回 409，响应可带最新版本和更新时间，但不能自动覆盖。
- 保存写 `RECORD_UPDATED` 审计；可对短时间连续保存做合理降噪，但不能丢失关键操作者/时间。

### 4.4 详情和列表

- `GET /records` 支持 `projectId`、`creatorId`、`status`、`keyword`、分页。
- 只返回当前用户参与项目的未删除记录。
- 所有项目参与者可查看其他人的进行中记录，但只返回正确的 `capabilities.canEdit/canDelete`。
- `GET /records/{id}` 返回固定字段、模板快照、字段值、富文本、版本和权限 flags。

### 4.5 软删除

- 只有 creator 可删除自己的 `IN_PROGRESS` 或 `CHANGES_REQUESTED` 记录。
- 此批次先覆盖 `IN_PROGRESS`；下一批次补 CHANGES_REQUESTED 和审核中/完成禁止测试。
- DELETE 设置 `deleted_at`，普通详情、列表、搜索均不可见。
- 项目 OWNER 不能删除他人记录。
- 写 `RECORD_DELETED` 审计。

## 5. 富文本编辑器

将现有 `contentEditable` 占位替换为真正的受控富文本编辑器，建议 TipTap。

MVP 最少支持：

- 段落、二级/三级标题。
- 粗体、斜体。
- 有序/无序列表。
- 引用或代码块二选一。
- 链接。
- 撤销/重做。

表格和正文内嵌图片不是产品硬性要求；若时间不足，删除无功能的 toolbar 按钮，不保留假按钮。附件图片在下一批次通过附件模块管理。

保存时提交编辑器 JSON 和 HTML；展示时使用净化后的内容。前端可用 DOMPurify 做防御性净化，但不能替代服务端净化。

## 6. 记录编辑器交互

### 6.1 新建流程

现有 `/records/new` 保留项目和结构选择。

`/records/new/edit`：

- 项目与模板从 query 读取后只读展示。
- 用户填写固定字段，首次点击“保存”才调用 POST 创建。
- 创建成功后 `replace` 到 `/records/{id}/edit`，避免刷新重复创建。
- 模板字段和附件区在记录创建前可展示；附件区提示“请先保存基础信息后上传附件”。

### 6.2 编辑流程

- 所有字段使用受控 state，禁止依赖 `defaultValue`。
- 保存按钮展示 `未保存/保存中/已保存/保存失败/版本冲突`。
- 不实现每次按键自动保存；如实现 debounce，离开页面前必须 flush 或提示。
- 有未保存修改时，路由跳转和浏览器关闭触发确认。
- 409 冲突弹窗提供“重新加载最新内容”和“保留本地内容供复制”，不能静默覆盖。
- 非 creator 或不可编辑状态直接使用详情页，不让用户进入可编辑表单。

### 6.3 模板字段组件

- 单行、多行、数字、日期、SELECT、FILE 分别渲染。
- FILE 字段此批次只显示“保存记录后可关联附件”，下一批次接附件选择。
- required 标记明显，但只在提交审核时阻止；保存草稿不阻止。
- 默认值只在首次创建记录工作副本时填入，后续模板变化不重算。

### 6.4 列表和详情

- `RecordsPage`、项目详情的记录 Tab 使用真实数据和服务端筛选。
- 预览区显示真实目的/正文摘要，不再硬编码 GFP 文案。
- `RecordDetailPage` 展示固定字段、模板字段和净化后的富文本。
- 只按 capabilities 显示编辑/删除；删除需要确认。

## 7. 接回批次二的约束

替换批次二中的记录 guard：

- 项目归档真实检查所有未删除记录是否 `COMPLETED`。
- 移除成员真实检查其是否创建未完成记录。
- MEMBER → REVIEWER 真实检查其创建记录是否全部完成。

此时尚无审核表，审核人重指派 guard 可继续保留到下一批次，但必须有明确测试标记。

## 8. 测试要求

后端：

- 个人模板 owner 隔离、系统模板不可改、名称唯一、软删除。
- 字段排序、类型/default/options 校验。
- 使用模板创建后，修改/删除模板不影响记录 snapshot。
- REVIEWER、非成员、归档项目不能创建记录。
- 其他项目成员可查看但不能编辑。
- 固定字段缺失不产生记录。
- 保存草稿允许模板必填为空；非法字段类型被拒绝。
- 版本冲突返回 409，不覆盖先保存内容。
- creator 可删进行中记录，OWNER 不能删他人记录。
- 归档和成员角色 guard 已接真实记录数据。

前端：

- 模板字段编辑、重排和校验。
- 新建记录首次保存后 URL 替换。
- 模板字段渲染正确。
- 未保存离开提示和冲突弹窗。
- capability 为 false 时不出现编辑/删除。

## 9. 人工验收

1. B 创建带文本、数字、日期、下拉、文件字段的个人模板。
2. B 用该模板创建记录；项目和模板进入编辑器后不可切换。
3. 基本字段不完整时不能创建数据库记录。
4. 首次保存后得到真实编号和可刷新 URL。
5. B 修改原模板，已建记录字段结构保持不变。
6. A/C 能查看 B 的进行中记录，但不能编辑或删除。
7. 两个浏览器页签保存同一记录，后保存者得到冲突而不是覆盖。
