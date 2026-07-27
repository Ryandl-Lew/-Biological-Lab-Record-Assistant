# 02：批次一——状态策略与类型化领域事件基础

## 本批次目标

为 Revision、Restore 和 Agent 模块提供稳定的公共基础：集中表达记录动作策略，建立类型化领域事件和处理器接口，并在不破坏第一阶段事务语义的前提下适配现有审计/通知写入。

本批次必须先合并。后续并行分支只依赖本批次公开接口，不得再次修改其语义。

## 1. 范围

必须完成：

- `RecordActionPolicy` 或等价纯领域策略。
- 统一的 `DomainEvent`、`DomainEventPublisher`、`DomainEventHandler` 契约。
- 第二阶段事件类型定义。
- `AuditEventHandler`，将类型化事件映射到现有 `audit_events`。
- 对现有 `EventService` 的兼容适配，第一阶段代码无需一次性全部改写。
- 事件 metadata 白名单和敏感数据裁剪策略。
- 领域/单元测试与最小集成测试。

不包含：

- Revision Diff 算法。
- Restore 业务实现。
- Agent 表、worker 或工具。
- 将整个系统重写为 Event Sourcing。
- Kafka、RabbitMQ、外部消息系统或通用 outbox。

## 2. 文件归属

本批次建议独占：

```text
backend/src/main/java/com/bionote/domain/record/**
backend/src/main/java/com/bionote/collaboration/event/**
backend/src/test/java/com/bionote/domain/record/**
backend/src/test/java/com/bionote/collaboration/event/**
```

允许最小修改：

```text
backend/src/main/java/com/bionote/collaboration/EventService.java
backend/src/main/java/com/bionote/audit/AuditService.java
backend/src/main/java/com/bionote/record/RecordService.java
backend/src/main/java/com/bionote/review/ReviewService.java
```

不要在本批次格式化或拆分这些现有大文件的无关代码。后续分支可能同时依赖它们。

## 3. 记录动作策略

### 3.1 目标

现有权限和状态校验散落在 service 的 `if` 中。第二阶段新增恢复动作后，必须避免前端、Restore Service 和 Record Service 各自解释状态。

建议提供纯 Java 类：

```java
public final class RecordActionPolicy {
    Decision canEdit(RecordContext context, UUID actorId);
    Decision canSubmit(RecordContext context, UUID actorId);
    Decision canRestore(RecordContext context, UUID actorId);
    Decision canViewRevision(RecordContext context, UUID actorId);
    Decision canGenerateRecordSummary(RecordContext context, UUID actorId);
}
```

`Decision` 至少包含：

```java
public record Decision(
    boolean allowed,
    String errorCode,
    String message
) {}
```

`RecordContext` 是已加载的最小领域投影，不允许策略内部访问数据库：

```java
public record RecordContext(
    UUID recordId,
    UUID projectId,
    UUID creatorId,
    String recordStatus,
    String projectStatus,
    boolean deleted,
    boolean provisional,
    String actorProjectRole
) {}
```

### 3.2 `canRestore` 规则

按顺序判断并返回稳定错误：

1. 记录不存在/已删除由查询层按 404 处理，不进入策略。
2. actor 必须仍为项目成员。
3. 项目必须为 `ACTIVE`。
4. actor 必须等于不可变 `creator_id`。
5. 记录不能为 provisional；临时预留记录没有 revision 可恢复。
6. 状态必须为 `IN_PROGRESS` 或 `CHANGES_REQUESTED`。

`OWNER` 身份不能覆盖 creator 约束。

### 3.3 渐进迁移

本批次只要求：

- 新的 Restore 和 Agent 模块必须使用策略。
- 现有 Record/Review Service 中与 `canEdit`、`canSubmit` 明显重复的判断可委托策略。
- 不要求一次性替换项目邀请、附件、导出等所有权限逻辑。

## 4. 类型化领域事件模型

### 4.1 基础接口

```java
public interface DomainEvent {
    UUID eventId();
    String eventType();
    UUID actorId();
    UUID projectId();
    UUID recordId();
    Instant occurredAt();
    Map<String, Object> metadata();
}
```

允许 `actorId`、`recordId` 按事件语义为空，但 `eventId`、`eventType`、`occurredAt` 必须存在。

推荐使用具体 record，而不是一个包含任意字符串的通用构造器：

```java
public record RecordRestoredEvent(...) implements DomainEvent {}
public record AgentRunRequestedEvent(...) implements DomainEvent {}
public record AgentRunCompletedEvent(...) implements DomainEvent {}
public record RevisionDiffViewedEvent(...) implements DomainEvent {}
```

`RevisionDiffViewedEvent` 属于可选低价值审计，默认不写，以免浏览产生噪声。必须写入的第二阶段事件：

- `RECORD_REVISION_RESTORED`
- `AGENT_RUN_REQUESTED`
- `AGENT_RUN_SUCCEEDED`
- `AGENT_RUN_FAILED`
- `AGENT_ARTIFACT_VIEWED` 可选

新的类型化事件写入 audit 时使用 `DomainEvent.eventId` 作为 `audit_events.id`，使同一事件被重复投影时由主键阻止重复审计。第一阶段尚未迁移的旧 `EventService.audit()` 调用可以继续生成独立随机 ID。

### 4.2 Publisher 和 Handler

```java
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}

public interface DomainEventHandler<E extends DomainEvent> {
    Class<E> eventType();
    void handle(E event);
}
```

初版允许进程内同步 registry。要求：

- handler 注册发生在应用启动时。
- 同一 handler 不重复注册。
- handler 执行顺序若影响事务必须明确，不依赖 Spring Bean 偶然顺序。
- 核心 audit handler 异常必须使业务事务回滚。
- 非关键 Agent 自动触发 handler 必须在事务提交后运行，失败不能回滚恢复/审核等核心业务。

可以将 handler 分为：

- `TRANSACTIONAL_REQUIRED`：审计、必须同时生成的通知。
- `AFTER_COMMIT_BEST_EFFORT`：Agent 自动触发等派生任务。

如果实现 AFTER_COMMIT 注册复杂，本阶段可只定义扩展点，Agent 第二阶段默认手动触发。

## 5. 审计映射

### 5.1 `RECORD_REVISION_RESTORED`

目标：

- `target_type=RESTORE_OPERATION`
- `target_id=restoreOperationId`
- `record_id=recordId`
- metadata 白名单字段：
  - `revisionNo`
  - `sourceRevisionId`
  - `fromVersion`
  - `toVersion`
  - `changedSections`
  - `attachmentAdded`
  - `attachmentRemoved`

不得写入完整 before/after 正文、snapshot_json、storage_key 或 preview token。

### 5.2 Agent 事件

`AGENT_RUN_REQUESTED/SUCCEEDED/FAILED` metadata 只包含：

- `runId`
- `artifactKind`
- `triggerType`
- `status`
- `errorCode`，失败时
- `artifactId`，成功时

不得包含 prompt 全文、模型密钥、工具结果正文或用户完整 focus。

### 5.3 AuditService 读取

更新 `SAFE_KEYS` 以允许上述安全字段。项目时间线默认可展示：

- `RECORD_REVISION_RESTORED`
- `AGENT_RUN_SUCCEEDED`

失败事件保留在底层审计，不默认出现在参与者协作时间线，避免噪声。OWNER 可以在 Agent trace 中查看失败。

## 6. 与现有 EventService 的兼容

当前大量服务直接调用：

```java
events.audit(...)
events.notify(...)
```

本批次不得为了“架构纯度”一次性重写所有调用。建议：

1. 保留原方法签名，保证第一阶段测试不变。
2. 在 `EventService` 内委托新的 audit writer，或让新 publisher 复用同一 writer。
3. 新的第二阶段模块只发布类型化事件，不直接拼 audit SQL。
4. 后续可以逐批迁移旧调用，但不是第二阶段 DoD。

## 7. 事务语义

- `RecordRestoredEvent` 在 Restore Service 的 `@Transactional` 方法内同步发布。
- audit insert 失败时整个恢复回滚。
- 事件 handler 不允许开启 `REQUIRES_NEW` 绕过主事务。
- Agent run 自身的运行 trace 使用独立短事务逐步提交，不能用一个长事务覆盖整个模型执行。
- 模型网络调用绝不能发生在持有 record 行锁的事务中。

## 8. 测试要求

### 8.1 Policy 单元测试

覆盖 `canRestore`：

- creator + ACTIVE + IN_PROGRESS -> allowed。
- creator + ACTIVE + CHANGES_REQUESTED -> allowed。
- OWNER 但非 creator -> denied。
- REVIEWER -> denied。
- IN_REVIEW -> denied。
- COMPLETED -> denied。
- ARCHIVED -> denied。
- provisional -> denied。

错误码和消息必须稳定，不只断言 boolean。

### 8.2 Event 单元测试

- registry 自动收集 handler。
- 每个事件只被匹配 handler 调用一次。
- metadata sanitizer 丢弃未知或敏感 key。
- transactional handler 异常向上传播。
- after-commit/best-effort handler 异常按设计记录但不污染核心事务。

### 8.3 集成测试

- 发布恢复事件后写入正确 audit row。
- audit metadata 不包含正文、prompt、token、storage key。
- 现有第一阶段创建/提交/审核审计继续工作。

## 9. 验收命令

```powershell
cd backend
.\mvnw.cmd -Dtest=*RecordActionPolicyTest,*DomainEvent*Test test
.\mvnw.cmd test
```

## 10. 完成报告

报告必须列出：

- 公共策略和事件接口的准确包名。
- 哪些现有 service 已委托新策略，哪些仍保留兼容调用。
- 事务内与 after-commit handler 的实际行为。
- 新增 audit event 和安全 metadata。
- 实际测试结果。

不得宣称系统已经完成 Event Sourcing 或可靠消息队列。
