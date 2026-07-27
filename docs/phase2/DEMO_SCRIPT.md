# BioNote 第二阶段 8–12 分钟演示脚本

## 准备

1. 以 `AGENT_ENABLED=true`、`AGENT_PROVIDER=fake` 启动 dev 环境。
2. 登录 `owner@example.com` 或 `member@example.com`；默认密码由 `DEV_SEED_PASSWORD` 控制。
3. 打开 `HepG2 缺氧响应 qPCR`。

## 0:00–3:00 Revision 与领域 Diff

1. 打开 `HepG2 缺氧 12 h VEGFA 表达分析（R2 复核）`。
2. 进入“版本历史”，指出 R1、R2、提交人、审核状态和当前/最终标记。
3. 选择 R1 → R2。
4. 展示固定标题、相对表达倍数/熔解曲线模板字段、正文块和新增 Markdown 附件。
5. 说明系统比较规范化领域结构，而不是原始 HTML 或二进制附件内容。

## 3:00–5:30 安全恢复

1. 在当前 `CHANGES_REQUESTED` Working Copy 选择 R1。
2. 打开 Restore Preview，切换“仅内容/包含附件”，观察 Diff、activate/soft-delete/keep 和 warning。
3. 确认恢复；说明 token 绑定 actor、record、revision、expected version、附件策略和前后 hash，且短时过期。
4. 返回历史，证明 R1/R2 和 Review 未改变，只有 Working Copy version 增加。
5. 修改后再次提交，形成下一连续 Rn；时间线显示 `RECORD_REVISION_RESTORED`。
6. 打开一个 `COMPLETED` 或归档项目记录，说明只能比较、不能恢复。

## 5:30–8:30 Project Progress 与 Trace

1. 以项目 OWNER 打开“智能进展”，选择时间范围并生成。
2. 观察 202 后的 `QUEUED/RUNNING/SUCCEEDED`。
3. 展示 progress、risk、next action、limitations，强调 `COMPLETED` 不等于实验结果成功。
4. 点击 Evidence 跳回记录、Revision 或 R1/R2 Diff。
5. 打开 Trace，展开已保存步骤：project overview、record list、activity、validation、artifact saved。
6. 说明 Trace 只含脱敏参数/结果摘要、hash、耗时和 token，不含 chain-of-thought。
7. 点击 rerun，证明旧 Artifact/Trace 保留且生成新 Run。

## 8:30–10:00 记录总结与可靠性

1. 进入记录“AI 记录总结”，由记录创建者生成 Record Summary。
2. 展示 record overview、revision list、revision diff 证据路径。
3. 打开 seed 中的 Evidence invalid 失败 Run，说明没有空白或伪成功 Artifact。
4. 取消一个排队 Run，展示 `CANCELLED` 和无 Artifact。
5. 用 outsider 访问复制的 Revision/Artifact/Trace URL，展示对象级拒绝。

## 收尾

- 新工具只需实现只读 `AgentTool` Bean，无需修改 Harness 主循环。
- fake provider 用于确定性自动化；真实 provider 通过 adapter 接入，但不能绕过 Tool、Schema、Evidence 或权限边界。
- 报告是可追溯派生物，不会写回记录、审核或恢复流程。
