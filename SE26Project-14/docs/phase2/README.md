# BioNote 第二阶段：版本治理与 Agent Runtime 实现指南

本目录用于指导 code agent 在第一阶段 MVP 之上实现第二阶段。第二阶段不是继续堆叠页面，而是围绕以下工程主线形成完整闭环：

```text
不可变提交快照
  -> 领域感知 Diff
  -> 可预览、可审计、带并发保护的恢复
  -> 类型化领域事件与证据读取工具
  -> 可扩展、可观测、可验证的 Agent Harness
  -> 记录总结与项目进展报告
```

最终交付应体现版本治理、事务一致性、可扩展框架、对象级权限、AI 可观测性和自动化验证，而不是“调用一次模型 API”。

## 1. 文档阅读规则

每个实现批次开始前，code agent 必须完整阅读：

1. `docs/01-product-overview.md`
2. `docs/agent-guidelines/00-master-contract.md`
3. `docs/agent-guidelines/01-domain-api-contract.md`
4. `docs/phase2/00-phase2-master-contract.md`
5. `docs/phase2/01-phase2-domain-api-contract.md`
6. 当前批次文档

若第一阶段文档写有“AI、历史 Diff、恢复不实现”，该限制只表示第一阶段范围；第二阶段中由 `00-phase2-master-contract.md` 明确启用的能力覆盖这些范围排除。角色、权限、记录状态、已完成记录不可变、项目归档不可逆等第一阶段不变量继续有效。

不得只阅读当前批次后自行发明第二套 revision、状态名、Agent 表或 API。

## 2. 文档与批次

| 顺序 | 文档                                      | 主要交付                                          | 建议负责人           |
| ---- | ----------------------------------------- | ------------------------------------------------- | -------------------- |
| 0    | `00-phase2-master-contract.md`            | 总范围、架构、非目标、工作纪律                    | 全员阅读             |
| 0    | `01-phase2-domain-api-contract.md`        | 统一模型、权限、状态、API、错误码、迁移编号       | 全员阅读             |
| 1    | `02-foundation-state-events.md`           | 状态策略、类型化领域事件、审计适配层              | 集成/后端负责人      |
| 2A   | `03-revision-diff.md`                     | 修订摘要、详情、规范化与领域感知 Diff             | 版本模块负责人       |
| 2B   | `04-restore-preview-execution.md`         | 恢复预览、事务恢复、幂等和恢复审计                | 恢复模块负责人       |
| 2C   | `05-agent-harness-runtime.md`             | Agent SPI、运行循环、工具注册、trace、prompt 版本 | Agent Runtime 负责人 |
| 3    | `06-agent-tools-progress-reports.md`      | 只读工具、记录总结、项目进展报告、证据校验        | Agent 业务负责人     |
| 4    | `07-frontend-version-agent-experience.md` | 版本比较/恢复 UI、Agent 报告 UI、运行轨迹         | 前端负责人           |
| 5    | `08-integration-quality-demo.md`          | 全量测试、安全、性能、演示、交付门禁              | 集成负责人           |
| 协作 | `09-team-ownership-merge-plan.md`         | 文件归属、依赖图、合并顺序、冲突处理              | 项目负责人           |

批次 2A、2B、2C 只有在批次 1 的接口和基线代码合并后才可并行。批次 2B 可以先依据 `01-phase2-domain-api-contract.md` 编写测试和服务骨架，但最终必须复用 2A 的 Diff 结果模型，不能自行建立第二套差异计算。

## 3. 推荐给 code agent 的任务开场指令

```text
请完整阅读 docs/phase2/README.md 要求的公共文档，并仅实现
docs/phase2/XX-*.md 对应批次。

要求：
- 开始前检查工作区和已经合并的前置批次，不覆盖用户或其他成员的修改。
- 严格遵守本批次文件归属；共享热点文件只提交最小改动或交由集成负责人处理。
- 数据库只新增本批次预分配的 Flyway migration，不改 V1-V8 和其他批次迁移。
- 先补测试，再实现服务、API 和 UI；权限、状态、事务与冲突不能只在前端处理。
- 不提前实现其他批次，也不创建平行模型、临时 API 或业务 mock。
- 实际运行批次验收命令，报告修改文件、测试结果和剩余风险。
```

## 4. 第二阶段完成标准

全部批次完成后，必须能够稳定演示：

1. 用户查看一条记录的分页修订历史，并打开任意 R1/R2/R3 详情。
2. 用户选择两个修订，查看固定字段、模板字段、正文、附件和审核信息的结构化 Diff。
3. 当前工作副本处于可编辑状态时，创建者可以预览“恢复后将发生什么”。
4. 创建者确认恢复后，系统在事务中复制旧快照、同步附件、递增乐观锁并记录来源；旧 revision 永不改变。
5. 恢复后的工作副本再次提交，生成新的递增 revision，而不是覆盖历史编号。
6. 已完成记录、归档项目、非创建者、非项目成员和过期版本请求均被正确拒绝。
7. 用户可以触发记录总结或项目进展报告，HTTP 请求不阻塞等待模型完成。
8. Agent 通过白名单只读工具收集信息，每一步工具调用、结果摘要、耗时、模型与 prompt 版本可追踪。
9. Agent 输出符合严格 Schema，每个事实性结论可引用当前用户有权访问的项目、记录、revision、review 或 diff 证据。
10. Agent 失败、超时、超过步数、模型返回非法结构和证据失效都有确定状态，不产生伪成功报告。
11. 在未配置真实模型密钥时，应用仍可启动；自动化测试使用确定性的 fake model。
12. 第一阶段全部测试继续通过，第二阶段新增权限、冲突、幂等、提示注入和 E2E 测试通过。

## 5. 第二阶段明确不做

- 不为每次自动保存生成 revision；`ExperimentRecord.version` 仍是并发令牌，不是用户历史版本。
- 不修改、删除或重新编号已存在的 `record_revisions`。
- 不重新开启或原地修改 `COMPLETED` 记录。
- 不取消项目归档，也不在归档项目中恢复工作副本。
- 不比较二进制附件正文，不做 Office/OCR、图片识别或向量检索。
- 不实现能写数据库、自动审核、自动恢复、自动修改记录的 Agent 工具。
- 不实现开放式聊天、任意 SQL、任意 HTTP、代码执行或文件系统工具。
- 不保存或展示模型隐藏推理过程；trace 只记录请求元数据、工具调用、工具结果摘要、验证结果和最终产物。
- 不引入多 Agent 协作、模型路由、长期语义记忆、定时日报、WebSocket 流式输出或可视化 prompt 编辑器。
- 不把审计表宣传为完整 Event Sourcing；系统仍以当前业务表为事实状态，以不可变 revision 和 audit event 提供追溯。

## 6. 总体验收命令

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

如果批次新增了专用测试脚本，必须同步写入对应批次文档和根 README。不得用“理论上通过”代替实际结果。
