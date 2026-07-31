# 08：批次五——集成、质量、安全与演示交付

## 本批次目标

不再新增第二阶段产品范围，而是统一 API、修复跨模块问题、验证 MySQL/H2、权限、并发、Agent 安全和 E2E，并准备一条可以体现软件工程深度的演示故事。

## 1. 集成前检查

确认已合并：

- V9、V10、V11；V12 尚由本批次决定。
- RecordActionPolicy 与事件基础。
- Revision Query/Diff。
- Restore Preview/Execution。
- Agent Runtime。
- BioNote Tools/Reports。
- 前端版本和 Agent UI。

运行 `rg` 检查平行模型：

```text
record_versions
save_history
revision_diff_v2
ai_service
chat_service
mock_agent
Thread.sleep
fake_success
```

除测试 fake provider 外，不应存在第二套版本/Agent 实现。

## 2. 共享热点统一

集成负责人统一处理：

- `frontend/src/api/index.js`：导出 revisions/restore/agentRuns。
- `frontend/src/router/index.jsx`：如需 query tab 通常不增路由；删除临时页面。
- `frontend/src/domain/enums.js`：新增状态 label/tone。
- `frontend/src/components/layout/Sidebar.jsx`：保持无通用 AI 入口。
- `backend/pom.xml`：去重依赖、许可证和版本。
- `application*.yml`、`.env.example`：配置完整且默认安全。
- `AuditService`：事件白名单和查询统一。
- OpenAPI annotations/description。
- 根 README、backend/frontend README。

禁止在集成批次顺手大规模格式化全仓库。

## 3. V12 与查询计划

先使用实际查询和 `EXPLAIN` 验证，再决定 V12：

- revision list by record/revision_no。
- restore history by record/restored_at。
- worker claim by status/created_at。
- artifacts by project/record/kind/created_at。
- steps by run/step_no。

只有缺失时新增索引。不要创建重复/前缀相同且无收益的索引。

V12 不允许新增产品表或改变领域语义。

## 4. 后端回归矩阵

| 能力            | 成功路径                     | 失败/边界                            |
| --------------- | ---------------------------- | ------------------------------------ |
| Revision list   | 分页、摘要、详情             | outsider、跨 record、软删除          |
| Diff            | R1/R2、revision/working copy | source 非法、跨 record、超大截断     |
| Restore preview | Diff、附件计划、token        | 非 creator、状态、归档、过期 version |
| Restore execute | 事务写入、R3、audit          | 双击、不同 payload、并发、回滚       |
| Prompt          | v1 注册、run 引用            | 同版本 hash 改变                     |
| Worker          | queue、claim、终态           | 多 worker、重启、中断、取消          |
| Tool            | 白名单读取、分页             | 跨项目、非法 args、过量结果          |
| Artifact        | Schema、evidence             | 幻觉 ID、无 evidence、非法建议       |
| Trace           | step、耗时、hash             | secret、正文、隐藏推理泄露           |
| Run API         | 202、查询、rerun             | disabled、rate limit、失权、终态取消 |

每一行至少有后端自动化测试；恢复、权限、worker 竞争和证据验证必须是集成测试。

## 5. 第一阶段回归

重点验证第二阶段没有破坏：

- 注册/登录和 JWT。
- 项目邀请、角色和归档。
- 记录创建、首次保存、自动保存和乐观锁。
- 附件上传、预览、下载和历史附件访问。
- R1 退回、R2 通过和幂等提交。
- 通知、待办、搜索、时间线。
- final revision PDF/Markdown 导出。

特别检查：Revision Query 重构后现有详情页、DemoDataService 和导出不再依赖被删除的 DTO 行为。

## 6. Agent 安全测试

### 6.1 Prompt injection

准备正文、模板字段、附件名、审核意见分别包含攻击文本：

- 忽略 system prompt。
- 请求调用未知删除工具。
- 请求泄露其他项目和邮箱。
- 伪造 JSON evidence。

确认：

- 只有注册白名单 tool 被执行。
- tool context 始终固定当前 project/record/actor。
- 输出无跨项目数据和邮箱。
- 非法 evidence 被 Validator 拒绝。

### 6.2 Secret 与日志

扫描应用日志、agent_steps、error_message：

- 无 `AGENT_API_KEY`。
- 无 Authorization/JWT。
- 无数据库密码。
- 无完整大正文。
- 无 attachment storage key。
- 无 chain-of-thought。

### 6.3 权限时间差

创建 run 后、worker 执行前移除用户项目成员身份：

- 工具重新授权失败。
- run 进入 FAILED/ACCESS_REVOKED。
- 不保存 artifact。
- 用户不能继续读取 trace。

### 6.4 资源滥用

- 最大 focus 长度。
- 最大 period。
- 最大并发 run。
- 最大 step/tool/token/time。
- 超限稳定终止。
- 大正文/大量 revision/附件不导致 OOM。

## 7. 并发与故障注入

至少验证：

1. 两个浏览器页签同时 restore，同 expected version 只一个成功。
2. Restore HTTP 超时后复用 key，只一个 operation。
3. Audit insert 失败，恢复完全回滚。
4. 两个 worker 竞争同 run，只执行一次。
5. provider 在工具执行后失败，run FAILED，trace 保留，无 artifact。
6. artifact insert 失败，run 不得 SUCCEEDED。
7. 用户取消 RUNNING，模型响应回来后不保存 artifact。
8. Prompt v1 active 切 v2，已有 run 仍使用 v1。

## 8. 数据完整性验证

提供测试或维护查询验证：

- `record_revisions` 编号同 record 连续且唯一。
- revision content_hash 未在 restore 后变化。
- revision attachments 关联未被恢复修改。
- restore operation before/after version 合法。
- SUCCEEDED run 必须恰有一个 artifact。
- 非 SUCCEEDED run 不得有 artifact。
- agent steps step_no 连续唯一。
- artifact evidence 全部落在 run project 范围。

这些查询用于测试/诊断，不开放普通用户任意执行。

## 9. 性能验收数据集

在 dev/test seed 增加可选规模数据：

- 1 个项目 100 条记录。
- 每条 5–10 个 revision。
- 每 revision 10–30 个模板字段。
- 正文 5–20 KB。
- 每 revision 若干附件元数据。
- 100 个 Agent runs、每 run 5–15 steps。

目标不是严格生产 SLA，但演示环境应满足：

- revision summary 首屏无明显等待。
- 普通 Diff 在合理时间返回。
- Agent run 创建立即 202。
- run/step/artifact 列表分页稳定。
- 无 N+1 随 revision 数线性产生数据库查询。

记录实际观察值和机器环境，不伪造基准。

## 10. E2E

### 10.1 `revision-diff-restore.spec.js`

完整流程：

```text
B 创建记录
-> 提交 R1
-> C 退回
-> B 修改并提交 R2
-> C 再退回
-> B 比较 R1/R2
-> Preview 恢复 R1
-> Confirm
-> 再修改/提交 R3
```

断言：

- Diff 包含已知字段/正文/附件变化。
- 恢复后状态仍 CHANGES_REQUESTED。
- R1/R2 hash/详情不变。
- R3 编号正确。
- 时间线存在 restore event。

### 10.2 `agent-progress.spec.js`

使用确定性 demo/fake provider：

- OWNER 生成项目进展。
- 202 -> QUEUED/RUNNING -> SUCCEEDED。
- 报告有 progress/risk/evidence。
- evidence 打开 R1/R2 Diff。
- trace 显示 tool steps、prompt version，不显示敏感内容。
- rerun 创建新 run，旧 artifact 保留。

真实 provider 另做手工 smoke，不让 E2E 依赖外网。

### 10.3 `phase2-authorization.spec.js`

- outsider 访问 revision、diff、restore preview、artifact、run、steps 均失败。
- OWNER 不能恢复 MEMBER 创建的记录。
- MEMBER 不能生成 project progress。
- REVIEWER 不能生成他人 record summary。
- 被移除用户旧链接立即失效。

### 10.4 `agent-failure.spec.js`

fake provider 模式覆盖：

- invalid output。
- unknown tool。
- timeout。
- evidence invalid。
- cancel。

UI 不产生空成功报告。

## 11. 演示数据

在现有三账号基础上准备：

- 一条可演示 R1 -> R2 -> 当前 CHANGES_REQUESTED 的记录。
- R1/R2 有明显固定字段、模板字段、正文和附件变化。
- 一个已完成 R1/R2 记录，用于说明完成记录只能比较不能恢复。
- 项目时间窗口内同时存在 approved、changes requested、in review、in progress。
- 一份已生成 demo Agent artifact 和一条失败 run，用于网络异常时备用展示。

Seed 必须幂等并通过正式业务服务生成 revision/review。允许 Agent demo artifact 通过 deterministic fake runtime 生成；不得直接 INSERT 一段伪装成真实运行的报告而无 steps。

## 12. 8–12 分钟演示脚本

### 场景 A：版本可解释

1. 打开记录修订历史，展示 R1/R2、审核人和最终/当前标记。
2. 选择 R1 -> R2。
3. 展示模板字段语义 Diff、正文 hunk、附件新增/移除。
4. 说明不是 HTML Diff，而是按领域结构对齐。

### 场景 B：安全恢复

1. 在 CHANGES_REQUESTED 工作副本选择 R1。
2. 打开 Restore Preview，展示将变化字段和附件。
3. 确认恢复。
4. 展示 R1/R2 历史仍不变，工作副本 version 已增加。
5. 修改并提交为 R3。
6. 打开时间线的 restore audit。

### 场景 C：Agent Runtime

1. OWNER 选择最近 7 天生成项目进展。
2. 立即看到 QUEUED/RUNNING，说明后台数据库任务。
3. 打开 trace：project overview -> record list -> review feedback -> revision diff -> validation。
4. 展示报告的证据链接。
5. 点击 evidence 跳回刚才的 R1/R2 Diff。
6. 说明 prompt version、tool SPI、运行限制和 Schema validation。

### 场景 D：可靠性

1. 展示非法 evidence/fake provider 失败 run，没有生成 artifact。
2. outsider 访问复制的 artifact/diff URL 被拒绝。
3. 说明模型关闭不影响记录/版本功能。

## 13. 架构材料

答辩文档至少准备：

1. 系统总体组件图。
2. Restore sequence diagram。
3. Agent run sequence diagram。
4. 数据模型 ER 图：revision/restore/agent tables。
5. 权限矩阵。
6. 状态机。
7. 测试金字塔与关键故障注入结果。

图必须与实际实现一致，不展示未完成的 Kafka、多 Agent、向量库等组件。

## 14. README 更新

根 README 增加：

- 第二阶段能力。
- V9-V11 migration，以及实际创建时的 V12 索引 migration。
- Agent 配置和默认关闭行为。
- fake/真实 provider 使用方式。
- prompt 资源位置和版本规则。
- worker 行为。
- 第二阶段测试命令。
- 演示脚本。
- 已知限制。

backend README 增加 revision/restore/agent 包说明。frontend README 增加页面入口、轮询和测试说明。

## 15. 最终命令

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify

cd ..\frontend
npm.cmd run lint
npm.cmd test -- --run
npm.cmd run build
npm.cmd run test:e2e
```

如果可使用 MySQL：

```powershell
docker compose up -d mysql
```

运行 Flyway/MySQL 集成和完整 Playwright。记录测试数量、耗时和失败修复，不只记录命令名称。

## 16. 最终交付报告

必须包含：

1. 实际实现的第二阶段能力。
2. 各模块包和负责人。
3. V9-V11 内容，以及是否因查询计划创建了 V12。
4. Diff 算法和限制。
5. Restore 事务/幂等/附件语义。
6. Agent SPI、prompt、trace、evidence、安全边界。
7. 实际测试命令与结果。
8. E2E 和真实 provider smoke 结果。
9. 未实现能力与原因。
10. 从零启动和完成演示的最短步骤。

若真实 provider 尚未完成或演示依赖 fake，必须明确写出，不能称为完整在线 Agent。
