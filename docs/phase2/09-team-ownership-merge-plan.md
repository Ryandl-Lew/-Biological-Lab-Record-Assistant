# 09：团队分工、文件归属与合并计划

## 1. 目标

本计划通过“公共契约先合并、后端按新包隔离、前端按页面所有权、迁移号预分配、共享热点集中处理”降低多人并行开发冲突。

成员数量不同时可以合并职责，但不要拆散同一聚合的后端事务或让多人同时修改同一大页面。

## 2. 推荐角色

### 2.1 集成/基础负责人

负责：

- 批次一状态策略和类型化事件。
- 公共 DTO/错误码最终裁决。
- 共享热点文件。
- V12、全量回归、README、演示。
- 合并顺序和契约变更通知。

### 2.2 版本负责人

负责：

- V9。
- Revision Query、Normalizer、Diff。
- Revision 后端测试。
- `frontend/src/api/revisions.js`。

### 2.3 恢复负责人

负责：

- V10。
- Preview token、附件计划、事务恢复和幂等。
- Restore 测试。
- `frontend/src/api/restore.js`。

### 2.4 Agent Runtime 负责人

负责：

- V11。
- Harness、Model Client、Tool SPI、Worker、Prompt、Trace。
- fake provider 和 Runtime 测试。
- Agent 相关 POM/config 最小改动。

### 2.5 Agent 业务负责人

负责：

- BioNote 只读 Tools。
- Read model、Artifact Schema、Evidence Validator。
- Run/Artifact API。
- `frontend/src/api/agentRuns.js`。

### 2.6 前端负责人

负责：

- RecordDetail 的全部版本/Diff/Restore/Record Summary UI。
- ProjectDetail 的 Project Progress UI。
- 组件测试、可访问性和 E2E 页面选择器。

如果有两名前端成员，必须按“RecordDetail 所有者”和“ProjectDetail 所有者”划分，不按 Diff 按钮/Restore 按钮拆分。

## 3. 依赖关系

```text
P2-1 Foundation State/Events
        |
        +------> P2-2A Revision/Diff ------> P2-2B Restore
        |                    |
        |                    +-------------> P2-3 Agent Tools
        |
        +------> P2-2C Agent Runtime ------> P2-3 Agent Tools
                                             |
                                             v
                                  P2-4 Frontend Integration
                                             |
                                             v
                                  P2-5 Quality/Demo
```

实际并行：

- Foundation 合并后，Revision/Diff、Agent Runtime 可完全并行。
- Restore 可按冻结接口开发，但集成测试依赖 Diff。
- Agent Tools 可先写工具 DTO 和 fake repository 测试，完整运行依赖 Diff + Runtime。
- 前端可依据冻结 API 使用 mock 开发，但不得自行改变字段。

## 4. 文件归属矩阵

| 路径                                 | 唯一所有者         | 其他成员规则                       |
| ------------------------------------ | ------------------ | ---------------------------------- |
| `backend/.../domain/record/**`       | 基础负责人         | 只调用，不修改                     |
| `backend/.../collaboration/event/**` | 基础负责人         | 只新增已批准 event handler         |
| `backend/.../revision/**`            | 版本负责人         | Restore/Agent 只依赖公开接口       |
| `V9__...`                            | 版本负责人         | 禁止修改                           |
| `backend/.../restore/**`             | 恢复负责人         | 其他模块不修改                     |
| `V10__...`                           | 恢复负责人         | 禁止修改                           |
| `backend/.../agent/runtime           | model              | prompt                             | trace/**`        | Runtime 负责人             | 业务负责人使用扩展接口 |
| `backend/.../agent/tool/bionote      | report             | api/**`                            | Agent 业务负责人 | Runtime 负责人不写业务工具 |
| `V11__...`                           | Runtime 负责人     | 禁止修改                           |
| `V12__...`                           | 集成负责人         | 只在最终查询验证后提交             |
| `RecordDetailMvpPage.jsx`            | Record 前端负责人  | 其他人只提供组件/API               |
| `ProjectDetailMvpPage.jsx`           | Project 前端负责人 | 其他人只提供组件/API               |
| `frontend/src/api/index.js`          | 集成负责人         | 分支不同时编辑；在报告列出待导出项 |
| `frontend/src/domain/enums.js`       | 集成负责人         | 状态常量放模块文件，最后汇总       |
| `frontend/src/router/index.jsx`      | 集成负责人         | 非必要不新增路由                   |
| `Sidebar.jsx`                        | 集成负责人         | 不增加 AI 顶级入口                 |
| `backend/pom.xml`                    | Runtime/集成负责人 | 其他成员先提依赖需求               |
| `application*.yml`                   | Runtime/集成负责人 | 其他成员不重排配置                 |
| README                               | 集成负责人         | 各模块提供片段/完成报告            |

## 5. Contract-first 交付物

开始并行编码前，集成负责人创建或确认：

- `RevisionDtos`。
- `RevisionDiffService` 接口。
- `RecordActionPolicy`。
- `AgentTool` SPI。
- `AgentArtifact` Schema 文件路径。
- 新错误码。
- API endpoint 和 migration 编号。

接口可以先只有定义和测试 fake。接口一旦并行使用，变更必须：

1. 更新 `01-phase2-domain-api-contract.md`。
2. 通知所有受影响负责人。
3. 同一合并窗口更新 producer/consumer contract test。
4. 禁止只在一个分支静默修改。

## 6. 分支与提交建议

使用小而明确的分支，例如：

```text
codex/phase2-foundation-events
codex/phase2-revision-diff
codex/phase2-restore
codex/phase2-agent-runtime
codex/phase2-agent-reports
codex/phase2-record-ui
codex/phase2-project-ui
codex/phase2-integration
```

每个分支保持：

- 只含本模块文件。
- migration 与实现/测试同一 PR。
- 不夹带格式化、依赖升级或第一阶段无关重构。
- 提交信息指出契约/API/数据库影响。

## 7. 推荐合并顺序

1. 文档与公共契约。
2. Foundation State/Events。
3. Revision Query/Diff + V9。
4. Agent Runtime + V11。它与 3 可交换，但都在 Agent Tools 前。
5. Restore + V10。
6. Agent Tools/Reports。
7. Record UI。
8. Project UI。
9. Shared frontend exports/enums/router/config。
10. V12、全量测试、README、demo seed。

注意 Flyway 按编号执行，与 Git 合并顺序无关；V9/V10/V11 内容不得互换。

## 8. 每个模块的完成包

负责人交付给集成者：

1. 修改文件列表。
2. 公共接口/DTO 变化。
3. migration 名称和表/索引。
4. 新配置项。
5. 实际运行的测试和结果。
6. 需要集成负责人修改的共享热点清单。
7. 已知限制。
8. 一条模块人工 smoke 步骤。

未提供这些信息的分支不进入最终集成。

## 9. 冲突预防规则

- 不在并行分支移动/重命名现有顶层 package。
- 不对现有一行式 Service 做全文件格式化。
- 不同时修改 `RecordDetailMvpPage`；所有 record UI 由一个负责人合成。
- 不让 Restore/Agent 各写一套 Diff DTO。
- 不让 Runtime/Agent 业务各写一套 `AgentRunService`。
- 不让每个模块各自在 `application.yml` 顶层新增重复 `agent` 配置。
- 不创建临时 V99 migration；使用固定编号。
- 不在前端页面自行拼 URL；API 模块完成后由集成者统一 export。

## 10. Contract Test

建议加入轻量契约测试：

- JSON 序列化 snapshot/diff/restore/agent artifact 的固定 fixture。
- OpenAPI snapshot 或 endpoint smoke。
- Prompt output schema 与 Java DTO Validator 一致性。
- Agent tool definition schema 能反序列化到对应 input DTO。
- 前端 API mock fixture 与后端 DTO 字段一致。

契约 fixture 放在明确共享目录，由集成负责人维护；模块分支更新需说明原因。

## 11. 每日/每轮集成建议

不要等全部模块完成后一次性合并。建议：

1. Foundation 合并即建立所有分支新基线。
2. Revision DTO/接口合并后，Restore 和 Agent Tools 立即 rebase/merge。
3. Runtime SPI 合并后，Agent Tools 立即接入真实 registry。
4. 每次模块合并运行 backend 全测和 frontend build。
5. 页面合并后立即跑受影响 E2E，不等最终批次。

## 12. 阻塞处理

以下情况必须暂停相关模块并由集成负责人裁决：

- 同一状态/权限在两份文档解释不同。
- 需要修改另一个模块拥有的 migration。
- API 字段无法满足已冻结用例。
- 恢复需要改变已完成记录不可变规则。
- Agent 工具需要写操作或跨项目数据。
- provider 限制导致必须改变 Harness 公共抽象。

不得用分支私有 workaround 绕过契约。

## 13. 最终责任

模块负责人对局部测试负责；集成负责人对以下结果负责：

- 文档、代码、OpenAPI、前端 API 一致。
- V1-V11，以及实际存在时的 V12，在 H2/MySQL 都可迁移。
- 第一阶段回归不破坏。
- 所有权限在后端执行。
- 完整 E2E 和演示脚本可运行。
- README 不把 fake、未完成或未验证能力描述成生产完成。
