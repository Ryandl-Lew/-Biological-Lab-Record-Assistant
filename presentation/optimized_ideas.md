# BioNote 软件工程实践课程答辩思路

> 本文是答辩内容策划稿，不是最终 PPT 文案。制作幻灯片时应继续压缩文字，每页只表达一个核心结论。

## 0. 答辩的总命题

### 0.1 希望老师最终形成的认识

BioNote 不是一个堆叠页面和接口的实验记录 CRUD 项目，而是一个由真实干系人需求驱动，经过多轮迭代，在需求、架构、详细设计、编码、测试和项目管理方面形成完整软件工程闭环的轻量化电子实验记录与数据分析系统。

### 0.2 推荐的一句话定位

**面向高校实验课程和初级科研训练的轻量化电子实验记录助手：让实验过程可协作、内容可追溯、版本可恢复、分析结论有依据。**

### 0.3 推荐叙事主线

不要按“登录、项目、记录、附件……”逐项报功能，建议围绕下面的因果链讲述：

1. 真实实验团队存在资料分散、记录不规范、审核责任不清和修改不可追溯的问题。
2. 我们通过真实干系人访谈、竞品研究和原型反馈形成需求基线。
3. 第一阶段先完成项目协作和实验记录审核闭环。
4. 第二阶段围绕版本治理、恢复安全和可信 Agent 增强工程深度。
5. 项目早期出现分工重合、接口不清和集成冲突，之后通过契约优先、模块所有权和持续集成改进协作方式。
6. 最终不仅交付了可运行的软件，也形成了可验证、可维护、可演化的软件工程实践。

这一主线与课程中的“软件工程金三角”相呼应：

- 人：团队成员、真实干系人和用户反馈。
- 技术：架构、接口、设计模式、测试与 Agent Runtime。
- 管理：需求基线、WBS、风险、迭代、文件所有权和集成门禁。
- 过程：将人、技术和管理组织为持续交付的软件过程。

---

## 1. 项目来源与需求工程

### 1.1 项目灵感

项目来源于与友校一名生物专业同学的合作：

- 对方提供真实实验场景、业务想法、部分实验数据和迭代反馈。
- 开发团队负责需求分析、产品设计、技术实现、测试和交付。
- 每轮迭代后，由真实使用者从实验专业和使用体验两个角度提出修改意见。

这一点应作为答辩开场的重要优势，因为它说明项目不是凭空想象需求，而是存在明确的外部干系人。

### 1.2 与课程“需求工程”的对应

课程将需求工程概括为需求获取、需求分析、需求定义、需求验证和需求管理。项目可以按这一框架展示：

| 课程活动 | 项目实践 | 可展示证据 |
|---|---|---|
| 识别干系人 | 生物专业同学、记录编写者、审核者、项目负责人 | 项目背景和角色定义 |
| 需求获取 | 访谈、竞品研究、真实场景反馈 | benchling.md、两轮修改建议 |
| 需求分析 | 明确 Project、Record、Attachment、Revision、Review 等领域对象 | docs/01-product-overview.md |
| 需求定义 | 固定角色、状态机、权限矩阵、API 和错误语义 | 产品文档和契约文档 |
| 需求验证 | 原型试用、每轮可执行版本、用户反馈 | 修改建议及修复结果 |
| 需求变更管理 | 第一阶段和第二阶段分别建立范围与非目标 | agent-guidelines、phase2 契约 |
| 需求跟踪 | 业务规则对应到接口、状态、测试和 E2E 场景 | 测试类、实施状态文档 |

### 1.3 真实反馈如何改变系统

不要只说“甲方提出了建议”，应展示“反馈—分析—实现—验证”闭环。

#### 第一轮反馈与对应改进

- 项目需要简介和详细描述两个层次。
- 实验记录列表改为目录式结构。
- 新建记录首次保存前也应能上传附件。
- 编辑器改为正文与操作区双栏布局。
- 新记录支持舍弃，已有记录支持取消本次编辑。
- 已正式保存的记录每 30 秒自动保存。
- 自动保存只更新 Working Copy，不生成正式 Revision。
- 项目时间线聚合连续同类事件，减少保存噪声。
- 个人中心和创建流程的信息层次得到调整。

#### 第二轮反馈与对应改进

- Markdown 附件增加安全文本预览。
- PDF 通过认证 API 获取 Blob 后创建临时 Object URL。
- 引入 TipTap 富文本编辑器。
- 服务端使用 Jsoup、前端使用 DOMPurify，兼顾编辑、展示和导出安全。
- 统一必填标识的视觉规范。

### 1.4 可引用的课程思想

- “与客户合作”高于只依赖最初设想。
- “响应需求变化”高于机械执行原计划。
- 需求验证不仅是检查文档，也包括原型确认和需求评审。
- 需求变更应经过提出、评估、实施和验证，而不是临时改代码。

---

## 2. 竞品研究与产品定位

### 2.1 Benchling 带来的启发

Benchling 的优势：

- 以 Project 和 Notebook Entry 组织实验资料。
- 对生命科学领域适配度高。
- 强调模板、权限、团队协作和数据追踪。
- 具有成熟的版本管理和审计思想。

对简单教学实验的不足：

- 功能复杂，初学者学习成本较高。
- 企业级和专业科研能力较重。
- 部分能力依赖付费或机构账号。
- 中文和国内教学实验并非其主要目标。

### 2.2 我们做出的取舍

BioNote 不复制 Benchling 的全部专业能力，而是保留最适合课程实践的部分：

- 项目化组织实验。
- 模板化规范记录。
- 明确成员和审核责任。
- 不可变提交快照。
- 版本比较与审计追踪。
- 已完成记录导出。

主动舍弃或延后：

- DNA、RNA、蛋白质序列设计。
- 试剂库存和仪器预约。
- 法规级电子签名。
- 多级或并行审批。
- 多人实时协同编辑。
- 二进制附件内容 Diff。

### 2.3 推荐竞品对比

| 维度 | 普通文档/网盘 | Benchling | BioNote |
|---|---|---|---|
| 资料组织 | 文件夹和文件 | 专业科研对象体系 | 项目—记录—附件 |
| 记录规范 | 依靠个人习惯 | 专业模板 | 面向教学实验的轻量模板 |
| 团队责任 | 不明确 | 企业级权限 | Owner、Member、Reviewer |
| 审核流程 | 依靠线下沟通 | 专业流程 | R1/R2 单审核人闭环 |
| 修改追踪 | 文件副本或简单历史 | 完整审计 | 结构化 Diff 与时间线 |
| 使用门槛 | 低但不规范 | 较高 | 规范性和轻量化平衡 |
| AI 分析 | 无 | 取决于产品配置 | 有工具、证据和 Trace 的受控 Agent |

推荐结论：

**我们不是做一个缩小版 Benchling，而是从专业工具中提取“结构化、权限、追溯”三个核心思想，再针对高校实验场景重新设计。**

---

## 3. 产品主要功能

建议用一个完整用户故事代替功能清单：

~~~
负责人创建项目并邀请成员
  → 成员使用模板创建实验记录
  → 填写固定字段、模板字段和自由正文
  → 上传图片、PDF、Markdown、CSV 或 Excel 附件
  → 提交 R1 给指定审核人
  → 审核人退回并给出意见
  → 创建者修改后提交 R2
  → 审核通过，记录完成并保持只读
  → 用户搜索、查看审计时间线并导出报告
  → 对历史 Revision 进行 Diff 或恢复工作副本
  → Agent 根据有权数据生成总结、进展报告或辅助分析
~~~

### 3.1 第一阶段业务闭环

- 注册、邮箱登录、JWT 会话恢复和个人资料。
- 项目创建、简介、详细描述和不可逆归档。
- 邮箱邀请、接受或拒绝邀请。
- Owner、Member、Reviewer 三种角色。
- 空白记录、系统模板和个人模板。
- 固定字段、模板字段和 TipTap 自由正文。
- Record Reservation、附件上传、首次保存和自动保存。
- R1 退回、修改、R2 再次提交和审核通过。
- 通知、搜索、审计时间线、PDF 和 Markdown 导出。

### 3.2 第二阶段能力

- Revision 历史列表和详情。
- Revision/Revision、Revision/Working Copy Diff。
- Restore Preview 和安全恢复。
- Agent Run、Artifact、Evidence、Trace 和 Replay。
- Record Summary 与 Project Progress。

### 3.3 当前代码中的后续扩展

- 项目级和记录级 Agent 问答。
- 临时 CSV/Excel 文件引用。
- 自然语言拟合需求解析。
- 线性、非线性和多元拟合。
- 用户确认拟合方案后再计算。
- R²、RMSE、参数、观测点和拟合曲线展示。
- 面向不同实验的分析模板。

这部分应标记为“第二阶段后的扩展探索”，避免与 phase2 文档中“不做开放聊天”的原始范围发生冲突。

---

## 4. 产品特色与创新

答辩时建议选 3 至 5 个作为主要创新，其他作为补充。

### 4.1 状态机和责任边界

- IN_PROGRESS：创建者可编辑。
- IN_REVIEW：任何人都不能修改内容和附件。
- CHANGES_REQUESTED：创建者根据审核意见修改。
- COMPLETED：记录永久只读。
- 项目负责人拥有项目管理权，但不能修改其他成员的记录。
- 只有当前 Revision 的指定审核人能够作出结论。
- 所有权限都在后端执行，前端隐藏按钮只改善体验。

产品价值：

- 明确记录责任。
- 防止审核过程中内容被悄悄修改。
- 防止项目负责人权限无限扩大。

### 4.2 Git 风格版本治理和项目时间线

#### 不可变 Revision

- 自动保存只改变 Working Copy。
- 只有提交审核才生成 R1、R2、R3。
- 历史 Revision 不修改、不覆盖、不删除、不重编号。
- 审核始终针对一个确定 Revision。

#### 领域感知 Diff

BioNote 先规范化实验记录，再分别比较固定字段、模板字段、TipTap 正文块、附件集合和审核元数据。

进一步处理：

- 数字、日期和换行规范化。
- canonical JSON 和 SHA-256 内容指纹。
- 正文块和文本 hunk。
- 大内容确定性裁剪和 warning。
- 支持 R1/R2 以及 Revision/Working Copy。

#### Git 风格项目时间线

- 项目级事件位于主干。
- 每条记录创建后形成独立分支。
- 审核完成后分支汇入主干。
- 删除记录以终止节点表示。
- 连续同类事件聚合，减少噪声。

推荐表述：

**我们借鉴的不是 Git 的代码管理界面，而是不可变提交、差异比较、分支演化和可追溯历史的思想。**

### 4.3 两阶段事务安全恢复

~~~
选择 Revision
  → 计算 Diff 和附件计划
  → 签发五分钟有效的 HMAC Preview Token
  → 用户确认
  → 行锁、重新鉴权、Token、乐观锁和幂等校验
  → 更新 Working Copy
  → 写入 Restore Operation 和审计事件
~~~

安全机制：

- Token 绑定 actor、project、record、source revision、expected version、附件策略和前后哈希。
- Preview 后内容变化会导致执行失败。
- 相同 Idempotency-Key 重试返回同一结果。
- 相同 Key 用于不同请求返回冲突。
- 并发恢复只允许一个成功。
- 审计失败时记录、附件和恢复操作整体回滚。
- 恢复只修改 Working Copy，不篡改 R1/R2 和审核结论。

### 4.4 可信 Agent Harness

课程详细设计课件介绍了 Tool Use Agent 模式。BioNote 在此基础上增加了工程约束：

- AgentModelClient 隔离模型供应商。
- Fake Provider 用于离线和确定性测试。
- OpenAI-compatible Adapter 接入真实模型。
- 数据库 Run 队列和 Worker 异步执行。
- AgentTool SPI 和 Tool Registry 支持扩展工具。
- 工具只能是白名单只读工具。
- 每次工具调用重新执行对象级授权。
- 模型不能覆盖 actor、project 和 record 安全上下文。
- 输出必须通过 JSON Schema。
- 事实性结论必须通过 Evidence Validator。
- Trace 保存脱敏工具调用、结果摘要、耗时和验证信息。
- Artifact 与 SUCCEEDED 状态在同一事务中保存。
- 失败、取消、超时和非法输出均不生成伪成功报告。

推荐核心句：

**我们关注的不是模型能不能回答，而是模型依据什么回答、能访问什么、失败时是否会产生伪成功、结果能否追溯。**

### 4.5 模型理解意图，数学引擎负责计算

当前数据分析扩展可以作为加分项：

- 用户通过自然语言描述拟合需求。
- 系统解析 CSV/Excel 表头、数据列和时间列。
- Agent 先提出结构化拟合方案。
- 用户必须点击“确认拟合”。
- 确定性程序计算参数、R² 和 RMSE。
- 模型只解释已经计算出的结果，不能自行编造统计指标。
- 前端展示观测点和拟合曲线。

这一设计体现 Human-in-the-loop：

- 模型负责理解和解释。
- 程序负责确定性计算。
- 用户负责确认分析范围和方法。

### 4.6 其他可选特色

- 模板结构快照，模板修改不影响历史记录。
- Record Reservation 解决首次保存前附件上传。
- 自动保存与正式 Revision 分离。
- PDF、Markdown 和图片的认证 Blob 预览。
- 搜索查询在数据库层完成对象级权限过滤。
- 已完成记录导出始终使用最终审核 Revision。
- Audit Metadata 和 Agent Trace 使用白名单与裁剪。
- Evidence 可以跳转回记录、Revision、Review 或 Diff。
- 前端 Diff 不只依赖颜色，也提供文字、符号和 aria label。

---

## 5. 软件架构

### 5.1 架构选择

项目采用按业务模块组织的模块化单体，而不是微服务：

- 团队规模和课程项目部署复杂度不需要微服务。
- 单体事务能够保证审核、恢复和 Artifact 保存的一致性。
- 通过接口和业务包维持模块边界。
- 如果未来需要拆分，UseCase 和 Store Port 可以作为服务边界基础。

### 5.2 与课程“多架构视图”的对应

课程强调软件架构应使用多个视图刻画。答辩不要只画一张技术栈图，建议至少准备：

1. 逻辑视图：前端、Controller、UseCase、Service、Port、Adapter、数据库。
2. 数据视图：Project、Record、Revision、Review、Restore Operation、Agent Run、Artifact。
3. 进程视图：Agent Run API、数据库队列、Worker、Harness。
4. 状态视图：Record 状态机和 Agent Run 状态机。
5. 部署视图：React/Nginx、Spring Boot、MySQL、附件存储、模型 Provider。
6. 质量属性视图：安全性、可靠性、可维护性、性能、可测试性。

PPT 主页面建议只展示逻辑视图，再选择数据视图或运行时序作为下一页。

### 5.3 逻辑架构

~~~
React 页面和领域组件
        │
统一 API Client / JWT / 错误处理
        │
Spring REST Controller
        │ 只依赖接口
UseCase 接口
        │
Application Service
 ├─ Policy / State Machine
 ├─ Transaction / Idempotency
 └─ Domain Event
        │
Store / Query Port 接口
        │
JPA / JDBC / File / Model Adapter
        │
MySQL / 文件系统 / 模型 Provider
~~~

Agent 支路：
~~~
Run API
  → agent_runs 数据库队列
  → AgentRunWorker
  → AgentHarness
      ├─ Planner
      ├─ ModelClient
      ├─ Tool Registry / Executor
      ├─ Validator
      └─ Trace Recorder
  → Artifact
~~~


#### 5.4.1前端

- React Router 管理页面和受保护路由。
- 页面组件负责完整业务流程编排。
- components 按 record、revision、restore、agent、timeline 等领域拆分。
- api 目录统一封装路径、Token、错误、Blob 和 Idempotency-Key。
- Zustand 只保存认证和轻量状态，不复制全部服务端业务数据。
- 搜索条件、Diff 来源和 Agent Run ID 保存在 URL 中。
- Revision 和 Agent 等较重组件使用 lazy loading。
- 后端返回 capabilities，前端不自行复制复杂权限规则。

### 5.5 后端架构

- Java 17、Spring Boot 3.2。
- 按 auth、project、record、revision、restore、agent 等业务包组织。
- Controller 负责 HTTP 协议转换。
- UseCase 表达应用入口。
- Service 承担权限、状态、事务和业务规则。
- Store/Repository Port 隔离持久化。
- infrastructure 内实现 JPA、JDBC、文件和外部 Provider Adapter。
- Flyway V1—V13 管理数据库演化。

### 5.6 信息隐藏、高内聚和低耦合

课程强调模块通过受控接口公开能力，内部算法、数据结构和外部资源细节应隐藏。

项目对应：

- Controller 不知道 JPA Repository 和 SQL。
- Revision 使用 RevisionStore，不关心具体查询技术。
- Agent Harness 只依赖 AgentModelClient，不关心模型厂商。
- Restore 复用 RevisionDiffUseCase，不建立第二套 Diff。
- 前端页面不自行拼 URL，统一通过 api 模块访问后端。

---

## 6. 基于接口的设计与设计模式

### 6.1 依赖倒置原则 DIP

课程定义：

- 高层模块不依赖低层模块，二者都依赖抽象。
- 抽象不依赖细节，细节依赖抽象。

项目体现：

~~~
RecordController → RecordUseCase ← RecordService
RecordService → RecordStore ← JpaRecordStore

AgentHarness → AgentModelClient
                   ↑
       FakeAgentModelClient
       OpenAiCompatibleModelClient
~~~

进一步证据：

- LayerDependencyTest 自动检查业务层不能依赖 infrastructure。
- Controller 字段必须是 interface。
- SQL 和 Spring Data 技术只能出现在 infrastructure。

这比只说“采用分层架构”更有说服力，因为架构约束本身也有自动化测试。

### 6.2 接口隔离原则 ISP

- RecordUseCase、RevisionDiffUseCase、RestorePreviewUseCase、RestoreExecutionUseCase 分别表达不同能力。
- AgentArtifactReader 与 AgentArtifactAppender 分离读写职责。
- AgentStepReader 与 AgentStepAppender 分离。
- 不要求调用者依赖其不使用的方法。

### 6.3 开闭原则 OCP

- 新增 AgentTool Bean 即可扩展工具，不修改 Harness 主循环。
- 新增模型 Provider 只需实现 AgentModelClient。
- 新增领域事件 Handler 不需要修改事件发布器。

### 6.4 单一职责原则 SRP

- SnapshotNormalizer 负责快照规范化。
- RevisionDiffService 负责领域 Diff。
- RestorePreviewTokenService 负责 Token 签发和验证。
- AttachmentRestorePlanner 负责附件恢复计划。
- EvidenceValidator 负责证据合法性。
- TraceSanitizer 负责 Trace 脱敏。

### 6.5 组合优于继承

- Service 通过构造函数组合 Policy、Store、Planner、Validator 等能力。
- Agent Harness 通过组合 ModelClient、ToolExecutor、Validator 和 TraceRecorder 构成运行循环。
- 业务实现较少依赖复杂继承体系。

### 6.6 设计模式候选

| 模式 | 项目实现 | 作用 |
|---|---|---|
| Strategy | AgentModelClient 的 fake/真实实现 | 动态替换模型策略 |
| Adapter | JPA/JDBC Adapter、OpenAI-compatible Adapter | 转换基础设施接口 |
| Repository | RecordStore、ProjectStore、RevisionStore | 隔离业务与数据访问 |
| Observer | DomainEventPublisher 与 DomainEventHandler | 解耦业务操作和审计 |
| State Machine | RecordActionPolicy、AgentRunStateMachine | 限制非法状态转换 |
| Policy Object | RecordActionPolicy | 集中权限决策 |
| Memento/Snapshot | 不可变 RecordRevision | 保存提交时刻状态 |
| Registry/Plugin | AgentToolRegistry | 发现和校验扩展工具 |
| Facade | 各类 UseCase/Service | 为 Controller 提供统一入口 |
| Protection Proxy | 认证 API、对象级权限和附件访问控制 | 控制受保护资源访问 |

答辩时选择代码证据最强的 4 至 6 个模式，不要为了数量牵强套用全部 GoF 模式。

### 6.7 Agent Tool Use 模式

与课程 Tool Use Pattern 对应：

~~~
用户任务
  → LLM 判断需要的信息
  → 调用外部工具
  → 获取工具结果
  → 基于结果生成响应
~~~

项目增加的工程约束：

- 工具只读。
- 工具 Schema 启动时校验。
- 重名和写工具直接拒绝启动。
- 工具调用重新授权。
- 工具输出有条数和字符上限。
- 工具结果形成 Evidence Candidate。
- 最终 Artifact 必须通过 Schema 和 Evidence 验证。

---

## 7. 关键技术

### 7.1 身份、安全和权限

- Spring Security 和无状态 JWT。
- BCrypt 密码哈希。
- 统一 401 和 API 错误结构。
- 对象级权限校验。
- CORS 白名单。
- Jsoup 和 DOMPurify 富文本净化。
- 附件随机 storage key。
- 认证 Blob 预览和下载。
- Trace 与 Audit Metadata 脱敏。

### 7.2 数据一致性

- Spring Transaction。
- 乐观锁 version。
- 恢复执行时的数据库行锁。
- Idempotency-Key。
- 不可变 Revision、Review 和 Artifact。
- 软删除。
- 同事务写入业务数据与关键审计事件。

### 7.3 版本治理

- canonical JSON。
- SHA-256 内容摘要。
- TipTap JSON 正文块规范化。
- 结构化 Diff 和文本 hunk。
- HMAC-SHA256 Restore Preview Token。
- 恢复前后 hash 校验。

### 7.4 Agent Runtime

- 数据库异步队列。
- Worker 原子领取。
- Prompt 版本化。
- Provider-neutral ModelClient。
- Tool SPI、Registry 和 Executor。
- Run 内工具缓存。
- 步骤、工具、模型、Token、时间和 repair 限额。
- JSON Schema 和 Evidence Validator。
- Trace、Replay 和 Artifact。

### 7.5 实验数据分析

- Apache POI 读取 Excel。
- CSV 数据抽取。
- 表头识别和列选择。
- 时间列转换为相对分钟。
- Apache Commons Math 曲线拟合。
- 多元回归。
- 观测点和拟合曲线 SVG 可视化。

### 7.6 数据库与性能

- MySQL 和 H2 测试环境。
- Flyway 增量迁移。
- 稳定分页排序。
- 复合索引。
- 真实 MySQL EXPLAIN。
- Revision 列表、Restore History、Artifact 查询避免 N+1 和 filesort。

---

## 8. 测试与质量保证

### 8.1 与课程测试层次对应

| 测试层次 | 项目实践 |
|---|---|
| 单元测试 | Policy、Normalizer、Diff、Token、Registry、Trace、拟合引擎、前端组件 |
| 集成测试 | 认证、项目协作、审核、附件、Revision、Restore、Agent Harness |
| 系统/E2E 测试 | Playwright 三账号审核闭环、恢复、Agent、权限和冲突 |
| 验收测试 | dev seed、演示脚本、真实用户每轮反馈 |
| 回归测试 | 第二阶段新增功能后继续运行第一阶段全部测试 |

### 8.2 测试方法与项目例子

#### 等价类

- 项目成员与非成员。
- Creator 与非 Creator。
- 合法 Reviewer 与非法 Reviewer。
- 可编辑状态与只读状态。
- fake provider 与真实 provider。

#### 边界值

- Restore Token 过期时间。
- 文件大小和支持类型。
- 分页大小。
- Agent 最大步骤、工具调用、模型调用和 Token。
- 空审核意见和最大输入长度。

#### 判定表

可以将角色、项目状态、记录状态和操作组成权限判定表：

~~~
角色 × 项目状态 × 记录状态 × 是否创建者 × 操作
~~~

RecordActionPolicyTest 就是在验证这一类组合。

#### 场景法

Playwright 的三账号流程：

~~~
Owner 创建项目
  → 邀请 Member 和 Reviewer
  → Member 创建记录和上传附件
  → 提交 R1
  → Reviewer 退回
  → Member 提交 R2
  → Reviewer 通过
  → 搜索、导出和归档
~~~

#### 错误推测和故障注入

- outsider 猜测项目、记录、附件和 Trace URL。
- 两个页面同时保存。
- 双击提交或审核。
- 两个 Restore 并发执行。
- 审计 Handler 故意失败。
- Agent Provider 超时。
- 未注册工具和非法参数。
- Artifact 事务故意失败。
- Evidence 引用不存在对象。

### 8.3 代表性测试

- LayerDependencyTest：架构规则。
- RecordActionPolicyTest：权限和状态组合。
- RevisionDiffServiceTest：五类 Diff。
- RestoreApiIntegrationTest：事务、幂等、并发、附件和回滚。
- AgentHarnessIntegrationTest：工具缓存、双 Worker、取消、限额和失败注入。
- phase2-authorization.spec.js：对象级权限。
- revision-diff-restore.spec.js：R1/R2 Diff、恢复与 R3。
- agent-progress.spec.js：Artifact、Evidence、Trace 和 Replay。

### 8.4 第二阶段文档记录的质量结果

- 后端最终全量 99 tests，0 failures，0 errors，1 个默认跳过的联网 smoke。
- 前端 24 个测试文件、39 个测试通过。
- Playwright 8/8 通过。
- 真实 Provider 合成数据 smoke 1/1 通过。
- frontend lint 和 build 通过。
- MySQL Flyway 和 EXPLAIN 验证通过。

当前代码又增加了 Agent Chat、数据拟合和图表测试，因此答辩前必须重新执行一次完整测试，将 PPT 中的数量更新为当前结果。

### 8.5 质量保证不仅是测试

- 公共契约和错误码基线。
- Architecture Test 防止分层退化。
- Flyway 禁止修改已发布迁移。
- API 统一出口。
- Definition of Done。
- 每个模块同时提交实现、迁移和测试。
- 每次合并运行后端全测、前端构建和受影响 E2E。
- 真实用户迭代反馈。
- 已知限制明确记录，不把未验证功能描述成已完成。

---

## 9. 软件过程、迭代和维护

### 9.1 为什么不采用纯瀑布过程

项目初期不能一次获得所有完整需求：

- 外部干系人在看到可执行产品后才能提出更具体的体验问题。
- 前端交互和实验业务规则在使用过程中不断细化。
- 第二阶段 Agent 和版本治理需要在第一阶段不变量上演化。

因此项目更符合演化模型和增量迭代：

- 每一轮都有可执行、可测试的软件版本。
- 每轮都包含计划、需求、设计、编码、测试和总结。
- 迭代后进行用户评审和风险评估。

### 9.2 推荐迭代时间线

#### 迭代 0：需求探索和原型

- 识别用户和项目角色。
- 研究 Benchling。
- 确定项目—记录—附件领域结构。
- 形成产品概述和 MVP 范围。

#### 迭代 1：第一阶段业务闭环

- 认证、项目、邀请和成员角色。
- 模板、实验记录、附件和富文本。
- R1/R2 审核、通知、搜索、导出和归档。
- 完成三个账号端到端场景。

#### 迭代 2：真实用户体验改进

- 项目详细描述和目录式记录页面。
- Record Reservation。
- 自动保存、取消和冲突处理。
- 时间线聚合。
- PDF/Markdown 预览和 TipTap 编辑器。

#### 迭代 3：版本治理

- 不可变 Revision。
- Snapshot Normalizer。
- 领域感知 Diff。
- Restore Preview 和安全恢复。

#### 迭代 4：可信 Agent

- Agent Harness 和模型适配。
- 白名单只读工具。
- Prompt、Run、Step 和 Artifact。
- Schema、Evidence、Trace 和 Replay。

#### 迭代 5：数据分析扩展

- Agent Chat。
- CSV/Excel 临时引用。
- 拟合方案确认。
- 确定性曲线拟合和图表。

### 9.3 每个迭代都有 executable release

与课程“迭代开发与小型发布”对应：

- 每轮有明确范围和非目标。
- 每轮结束时必须能够启动和演示。
- 每个模块有实际测试结果。
- 用户反馈进入下一轮 Backlog。
- 不等到所有模块完成后再一次性集成。

### 9.4 维护类型与真实案例

| 维护类型 | 项目例子 |
|---|---|
| 纠错性维护 | 修复 PDF/Markdown 预览、Agent LIMIT_EXCEEDED 预算错误 |
| 完善性维护 | 改进编辑器、自动保存、记录目录和项目时间线 |
| 适应性维护 | 接入 OpenAI-compatible Provider、增加 CSV/Excel 分析 |
| 预防性维护 | 架构测试、Trace 脱敏、索引优化、工具限额和迁移规范 |

### 9.5 可运维性和 DevOps 实践

- 环境变量管理密钥和 Provider 配置。
- local/dev/real-llm 配置隔离。
- Agent 可以使用 fake provider 离线启动和测试。
- Actuator Health、Trace ID 和统一错误结构。
- Flyway 自动迁移。
- Demo Seed 幂等生成演示数据。
- Dockerfile 支持前后端容器化。

当前更准确的表述是“CI 风格的质量门禁”。除非确实存在自动化流水线，否则不要宣称已经建立完整 CI/CD：

~~~
修改代码
  → lint / 单元测试
  → 后端集成测试
  → 前端 build
  → Playwright E2E
  → 演示 smoke
  → 合并
~~~

---

## 10. 项目管理与团队协作

### 10.1 不回避早期问题：把问题讲成过程改进

课程将职责不清、任务依赖不明、缺少集成、沟通只靠口头约定等列为项目管理中的典型“最差实践”。项目初期也出现过相似问题：

- 文档粒度不统一，接口、字段和异常约定不够明确。
- 按页面或临时任务分工，导致多人同时修改同一批核心文件。
- 前后端各自推进，直到功能末期才发现接口理解不一致。
- 模块“代码写完”但迁移、测试、演示数据和说明没有一起交付。
- 合并依赖依靠口头传递，容易出现顺序错误和重复返工。

答辩不应只说“后来加强了沟通”，而应说明采取了哪些可验证的工程措施。

### 10.2 从功能清单转向 WBS 和依赖关系

先把需求拆成能够验收的工作包，再标出依赖：

~~~
公共契约与数据库迁移
  ├─ 领域模型与 Repository/Store
  ├─ UseCase 和权限策略
  ├─ Controller 与 API
  ├─ 前端 API 封装与页面
  └─ 单元测试、集成测试、E2E 和演示数据
~~~

例如 Restore 不能只拆成“后端恢复接口”和“前端恢复按钮”，而应拆成：

- Snapshot/Revision 数据基础。
- Diff 和 Preview。
- HMAC Preview Token。
- 行锁、乐观锁、幂等和事务。
- 前端二次确认及结果展示。
- 冲突、重复提交、附件和回滚测试。

这样可以提前看见关键路径，避免 UI 已完成但底层契约尚未稳定。

### 10.3 Contract-first：先稳定协作边界

第二阶段形成了公共契约优先的合并策略：

1. 先确认术语、状态、DTO、错误码、路由和数据库迁移。
2. 再实现后端 UseCase 和 API。
3. 前端根据稳定契约实现 API Client 和页面。
4. 最后补齐跨模块 E2E 与演示脚本。

可展示的证据：

- docs/phase2/09-team-ownership-merge-plan.md 中的所有权和合并顺序。
- 后端 request/response DTO、UseCase 接口及统一错误结构。
- 前端集中式 API Client 和类型/页面之间的调用关系。

这一实践对应课程中的接口设计、信息隐藏和配置管理：团队依赖的是明确契约，而不是依赖某位成员对内部实现的口头解释。

### 10.4 文件所有权减少冲突，但不形成知识孤岛

对高冲突区域设置主负责人：

- 数据库迁移与公共领域模型。
- Revision、Diff 和 Restore。
- Agent Harness 和工具系统。
- 前端编辑器、版本时间线和 Agent 页面。
- 测试、种子数据与演示脚本。

主负责人负责最终一致性，其他成员仍通过接口评审、代码评审和测试参与。答辩时可以表述为“单一合并责任，多人共同评审”，不要表述为“一个模块只有一个人懂”。

### 10.5 采用“模块完成包”而不是只交代码

一个模块被认为完成，应同时包含：

- 可运行的实现。
- API/Schema 或公共接口。
- Flyway 迁移或配置变更。
- 单元/集成测试。
- 必要的前端交互与错误处理。
- 演示种子数据。
- 已知限制和验证记录。

这相当于团队自己的 Definition of Done，可防止“在我的电脑上能运行”却无法集成。

### 10.6 小步合并和持续集成

推荐在答辩中展示如下协作闭环：

~~~
领取工作包
  → 确认契约与依赖
  → 小步实现
  → 本地测试
  → 模块评审
  → 按依赖顺序合并
  → 全量回归
  → 更新演示数据与文档
~~~

当前更准确的说法是“采用持续集成思想和 CI 风格质量门禁”。如果仓库中没有真实自动流水线，不要宣称“已经建立完整 CI/CD 平台”。

### 10.7 风险管理

| 风险 | 可能后果 | 应对措施 | 对应证据 |
|---|---|---|---|
| 真实需求变化 | 返工或功能偏离场景 | 每轮可执行版本、用户反馈进入 Backlog | 两轮修改建议文档 |
| 多人修改公共文件 | 合并冲突、契约漂移 | 文件所有权、Contract-first、合并顺序 | ownership/merge plan |
| Revision/Restore 破坏数据 | 历史丢失或并发覆盖 | 不可变快照、Preview Token、锁、幂等、回滚测试 | Restore 测试 |
| Agent 输出不可信 | 幻觉、越权访问、结果不可复核 | 只读工具、Schema、Evidence、Trace、Replay | Harness 测试与运行记录 |
| 外部模型不稳定 | 演示失败或成本不可控 | Provider 抽象、Fake Provider、限额和超时 | AgentModelClient |
| 新功能破坏旧功能 | 回归缺陷 | 分层测试、E2E、演示 smoke | 测试报告 |
| 成员知识集中 | 单点风险 | 接口文档、交叉评审、演示轮换 | 评审与分工记录 |

### 10.8 团队成长过程

可以借用课程中的团队发展模型组织叙述：

| 阶段 | 项目表现 | 改进 |
|---|---|---|
| 形成期 | 对业务和技术栈共同探索 | 竞品研究、需求访谈、MVP 边界 |
| 震荡期 | 文档粒度不一、任务重叠、合并冲突 | 复盘问题，明确公共契约和依赖 |
| 规范期 | 文件所有权、模块完成包、评审和测试门禁 | 形成可重复协作流程 |
| 成熟期 | 能并行推进 Revision、Agent、前端和测试 | 通过稳定接口集成并持续回归 |

重点不是声称团队从未出错，而是展示团队能识别问题、把经验固化为过程。

---

## 11. 经验教训

### 11.1 需求：用户说出的方案不等于真正需求

- 用户最初可能表达“需要某个页面或按钮”，开发者需要继续追问目标、角色、频率和异常场景。
- 只有文字访谈不够，真实用户看到可执行版本后才能提出更准确的反馈。
- 每条反馈应追踪到需求、实现和验证，避免形成无法验收的愿望清单。

### 11.2 架构：边界越早稳定，并行开发成本越低

- Controller、UseCase、Store、Adapter 的接口边界使业务规则不依赖框架细节。
- 公共 DTO、错误码和状态定义晚确定，会让前后端同时返工。
- Architecture Test 能把“约定”变成自动检查，防止分层随着迭代逐渐退化。

### 11.3 设计：复杂业务应显式建模

- 审核、编辑、归档、恢复等规则如果散落在条件判断中，很难测试和解释。
- Policy/State 思想把角色、状态和操作组合集中表达。
- Snapshot、Diff、Preview、Execute 的拆分使高风险恢复操作可以被审计和验证。

### 11.4 测试：测试对象不仅是正常功能

- 权限系统最重要的是验证“不该看到的人看不到”。
- 并发、双击、超时、事务中途失败和非法 Evidence 往往比正常路径更能暴露设计缺陷。
- E2E 证明业务闭环，单元和集成测试负责快速定位，两者不能互相替代。

### 11.5 Agent：能生成答案不等于可信

- 模型必须通过白名单工具获取事实，不能把模型记忆当作项目数据。
- Evidence 必须能回指真实对象；Trace 和 Replay 使输出形成可检查过程。
- 数值分析应由确定性数学引擎完成，LLM 负责理解意图、选择工具和解释结果。
- COMPLETED 只表示 Agent 工作流正常结束，不代表实验结论科学正确；领域结论仍需用户判断。

### 11.6 团队：沟通问题往往需要工程机制解决

- “多沟通”不能替代明确契约、负责人、依赖图和 Definition of Done。
- 分工不应只按代码量衡量，应同时考虑设计、测试、文档、集成和风险处理。
- 小步合并比最后一次大集成更容易发现契约错误。

---

## 12. 成员贡献展示方案

### 12.1 不建议使用的指标

- 不按 commit 数量排名：当前仓库历史可能经过汇总，且 commit 粒度不能代表工作价值。
- 不按代码行数排名：生成代码、配置和重复代码会扭曲贡献。
- 不只列“负责前端/负责后端”：粒度过粗，无法体现设计、验证和协作。

### 12.2 推荐贡献矩阵

答辩前按真实情况填写成员姓名：

| 成员 | 主责模块/问题 | 核心交付物 | 设计与技术贡献 | 测试与质量贡献 | 协作贡献 |
|---|---|---|---|---|---|
| A | 需求与基础业务 | 需求基线、项目/记录/审核闭环 | 领域模型、状态与权限规则 | 业务集成测试、用户验收 | 对接干系人、组织迭代 |
| B | Revision/Restore | Snapshot、Diff、Preview/Execute | 不可变版本、Token、锁和幂等 | 并发、回滚、附件恢复测试 | 契约评审、合并协调 |
| C | Agent Harness | Tool SPI、Registry、Worker、Artifact | Strategy/Adapter、Evidence、Trace | Harness 故障注入和 Replay 测试 | Agent 接口与演示脚本 |
| D | 前端与 E2E | 编辑器、时间线、版本页、Agent UI | API 层、组件边界、交互状态 | Vitest/Playwright、可用性验证 | 用户反馈落地、视觉一致性 |

成员数量和模块划分应按实际情况修改，允许一个成员承担多个角色，也应展示交叉评审。

### 12.3 每位成员的 20 秒陈述模板

“我主要负责的是【问题/模块】。我交付了【可观察成果】，其中关键设计是【接口、模式或技术决策】。我通过【测试/评审/演示】验证它，并与【其他模块或成员】通过【契约/协作机制】完成集成。”

这比“我写了若干页面和接口”更容易让老师判断个人贡献。

### 12.4 可作为贡献证据的材料

- 需求访谈和修改建议的整理记录。
- 架构图、接口契约、ADR 或模块设计说明。
- 具体 UseCase、Adapter、页面与数据库迁移。
- 单元、集成、E2E 和故障注入测试。
- 代码评审、冲突处理、集成验证和演示脚本。
- 用户反馈问题从提出到关闭的追踪。

---

## 13. 推荐现场演示

### 13.1 主演示：从审核到版本治理与安全恢复

这是最能同时体现产品价值、架构、技术和测试的主线，建议控制在 3 至 4 分钟：

1. Member 编辑实验记录并提交 R1。
2. Reviewer 退回，显示意见和状态变化。
3. Member 修改后提交 R2，Reviewer 通过。
4. 打开时间线和 R1/R2 Diff，展示字段、正文块和附件变化。
5. 对 R1 发起 Restore Preview，说明恢复不会直接覆盖当前数据。
6. 确认后执行恢复，产生新的 R3，而不是删除 R2。
7. 展示审计事件、恢复来源和恢复后的 Diff。

演示时同步指出：

- 状态机和 RecordActionPolicy 控制谁能做什么。
- UseCase 依赖 Store 接口，不依赖数据库实现。
- SnapshotNormalizer 生成稳定快照。
- Preview Token、锁、幂等和事务保护恢复。

### 13.2 第二演示：可信 Agent 的 Evidence 与 Trace

建议使用 Fake Provider 或预先验证稳定的 Provider：

1. 提问某项目最近实验状态或审核问题。
2. 展示 Agent 调用只读工具，而不是直接猜测。
3. 展示回答对应的 Evidence。
4. 打开 Trace，查看步骤、工具参数、结果摘要和限额。
5. 用 Replay 说明同一执行过程可以复核。

不要只展示聊天气泡；可信链路才是与普通聊天应用的差异。

### 13.3 加分演示：CSV/Excel 拟合

作为第二阶段后的扩展探索，控制在 1 分钟以内：

1. 临时选择 CSV/Excel 数据。
2. Agent 提议自变量、因变量和拟合方式。
3. 用户确认方案。
4. 确定性数学引擎计算曲线或多元回归。
5. 展示参数、指标和 SVG 图表。

需明确文件是临时分析输入、模型不直接完成数值运算，且结果需要实验人员解释。

### 13.4 主动演示一条失败路径

可选择其中一条：

- 非项目成员访问记录或附件，返回统一 403/404。
- 使用过期 Restore Token，系统拒绝执行。
- 同一 Idempotency-Key 重复恢复，只产生一次结果。
- Agent 请求未注册或写类型工具，Registry/Executor 拒绝。
- Artifact 引用不存在的 Evidence，验证失败且事务回滚。

一条可控的失败演示通常比连续展示多个正常页面更能体现质量保证。

### 13.5 演示预案

- 提前生成固定种子数据和三个角色账号。
- 使用独立浏览器上下文避免角色串号。
- 准备 Fake Provider，避免网络或额度导致 Agent 演示中断。
- 将完整流程录屏，现场失败时可以切换。
- 每个演示点准备一句“产品价值”和一句“工程原理”。

---

## 14. 推荐 PPT 页面顺序

建议控制在 14 至 16 页，避免把所有功能逐页罗列：

| 页码 | 内容 | 核心信息 |
|---|---|---|
| 1 | 项目标题与一句话定位 | 面向教学与科研协作的轻量、可追溯实验记录平台 |
| 2 | 真实场景与干系人 | 与真实生物专业用户合作，需求来自持续反馈 |
| 3 | 需求演化与竞品研究 | Benchling 启发、场景取舍、两轮反馈闭环 |
| 4 | 产品全景 | 项目、记录、审核、版本、Agent 的业务闭环 |
| 5 | 特色一：状态与责任边界 | 多角色协作不只是 CRUD |
| 6 | 特色二：Revision/Diff/Restore | Git 风格可追溯和安全恢复 |
| 7 | 特色三：可信 Agent | Tool、Evidence、Trace、Replay |
| 8 | 架构总览 | 模块化单体、多视图、前后端与 Agent |
| 9 | 基于接口的设计 | Controller—UseCase—Port—Adapter |
| 10 | 设计原则与模式 | 选择 4 至 6 个最有代码证据的模式 |
| 11 | 关键技术与质量属性 | 安全、一致性、可扩展、可审计 |
| 12 | 测试与质量保证 | 测试金字塔、方法、故障注入、结果 |
| 13 | 过程与项目管理 | 增量迭代、真实反馈、Contract-first、团队改进 |
| 14 | 成员贡献 | 贡献矩阵与交叉协作 |
| 15 | 演示或成果总结 | 主业务闭环和可信 Agent |
| 16 | 经验教训与结束语 | 从实现功能走向可演化、可验证的软件工程 |

如果答辩时间较短，可合并第 10/11 页、第 13/14 页，并把详细测试数据和模式列表放入备份页。

---

## 15. 结束语建议

可用以下逻辑结束：

“我们完成的不只是一个实验记录网站。项目从真实干系人的需求出发，通过增量迭代建立项目、记录和审核闭环；再用不可变 Revision、领域 Diff 和安全恢复解决可追溯问题；最后用受约束的 Tool、Evidence、Trace 和 Replay 探索可信 Agent。更重要的是，我们把课程中的需求工程、接口设计、设计模式、分层测试和项目管理落实到了可运行、可验证的代码中。”

一句话版：

“让实验过程不仅能被记录，还能被协作、被追溯、被恢复，并被可信地辅助分析。”

---

## 16. 答辩前事实确认清单

- 重新运行当前分支的后端、前端和 Playwright 全量测试，更新数字与日期。
- 确认是否存在真实 CI 配置；不存在时使用“CI 风格质量门禁”。
- 确认线上/演示环境实际使用的数据库、Provider 和模型名称。
- 确认真实模型 Smoke 的输入、输出和 Evidence，避免只引用历史文档结论。
- 确认所有设计模式均能在代码中定位到接口和实现，不为凑数量强行命名。
- 确认成员贡献与真实分工一致，并准备每人可展示的代码或测试证据。
- 确认开放式 Agent Chat、CSV/Excel 和拟合被标记为第二阶段后的扩展探索。
- 不将 Revision 系统称为完整 Git，也不将审计事件称为完整 Event Sourcing。
- 不把 Restore 表述为覆盖历史；它会在当前状态基础上产生新的 Revision。
- 不把 COMPLETED 解释成科学结论正确。
- 准备演示账号、种子数据、Fake Provider、录屏和离线截图。
- 检查 PPT 中所有截图的敏感信息、Token、文件路径和真实用户数据已脱敏。

---

## 17. 可能的追问与回答思路

### 17.1 为什么不直接使用 Benchling？

Benchling 提供成熟的科研数据与版本治理能力，但目标用户、功能复杂度和成本与本科教学实验场景不完全匹配。项目借鉴其可追溯思想，保留结构化模板、审核、版本和协作闭环，并针对轻量部署、教学流程和可解释 Agent 做取舍。

### 17.2 为什么选择模块化单体而不是微服务？

项目规模、团队人数和部署环境不需要承担微服务的网络、运维和分布式事务成本。模块化单体仍通过 UseCase、Port/Adapter 和架构测试保持边界，未来确有独立扩缩容需求时可以按接口拆分。

### 17.3 你们的“基于接口设计”体现在哪里？

Controller 依赖 UseCase，业务层通过 Store/Port 获取数据，JPA/JDBC 和模型 Provider 是 Adapter；AgentModelClient、AgentTool 等接口允许 Fake/真实实现替换。接口隔离了业务规则与框架、数据库和外部模型。

### 17.4 为什么 Restore 要分 Preview 和 Execute？

恢复是高风险写操作。Preview 让用户先看到 Diff，并签发绑定目标、来源和过期时间的 Token；Execute 再校验 Token、权限、当前版本、幂等键和锁，避免误操作、重放和并发覆盖。

### 17.5 Revision 和普通操作日志有什么区别？

操作日志回答“谁在什么时候做了什么”，Revision 保存可还原的业务状态快照，Diff 解释两个状态之间具体发生了什么。二者互补，不能互相替代。

### 17.6 为什么不用数据库触发器记录版本？

版本不仅涉及表字段，还涉及正文块、附件、领域语义和用户操作上下文。由应用层统一规范化快照更容易测试、演化和生成领域 Diff；事务仍保证业务数据与版本数据一致。

### 17.7 Agent 如何避免幻觉和越权？

Agent 只能调用注册的白名单只读工具；工具执行时重新使用当前用户身份授权；输出必须通过 Schema 和 Evidence 校验；Trace 记录步骤并支持 Replay。它不能完全消除模型不确定性，但把风险限制在可检查边界内。

### 17.8 为什么 Agent 不直接读数据库？

直接读数据库会绕过领域权限、形成表结构耦合并增加敏感数据泄露风险。工具通过业务接口提供最小、受控、可审计的数据视图。

### 17.9 拟合结果由谁计算，可信吗？

LLM 用于理解自然语言和选择分析方案；用户确认后由 Apache Commons Math 等确定性引擎执行计算。系统可以保证计算路径和输入可追踪，但实验结论是否科学仍需领域人员判断。

### 17.10 如何证明系统质量？

不能只用测试数量证明。项目同时提供分层测试、权限判定表、边界与异常场景、并发和事务故障注入、架构依赖测试、真实数据库验证、E2E 业务闭环以及真实用户反馈。

### 17.11 团队发生冲突或延期时如何处理？

先把问题还原为契约、依赖、所有权或完成标准问题，再通过 WBS、公共契约、文件所有权、小步合并和 Definition of Done 解决。答辩时应结合一个真实冲突及其后续规则说明，而不是只说“加强沟通”。

### 17.12 项目目前最大的限制是什么？

可以如实选择：真实用户规模仍小；Agent 真实模型评估样本有限；未形成完整云端 CI/CD；版本快照会增加存储；数据分析覆盖的模型有限。随后说明已经采用的缓解措施和下一步计划。

### 17.13 如果继续开发，优先做什么？

优先项应由真实用户验证决定。可选方向包括更系统的可用性测试、Agent 评测集与质量指标、版本存储压缩、更多实验数据格式、异步任务监控，以及将 CI 风格门禁真正自动化。


