# BioNote Frontend

React + Vite 前端，使用真实 `/api/v1` 服务，不包含业务 mock、假成功或独立 AI 聊天页。完整启动、演示和后端配置见根目录 `README.md`。

第二阶段入口：

- 记录 `?tab=history`：Revision History、详情、R1/R2/Working Copy、Diff、Restore Preview。
- 记录 `?tab=summary`：Record Summary、Evidence、Trace Replay、cancel/rerun。
- 项目 `?tab=progress`：Project Progress、历史 Artifact、Evidence、Trace Replay。

Run 以 2/3/5 秒退避轮询，终态或卸载时停止。Replay 只展开后端保存的脱敏 step，不重新调用模型或工具。Revision 与 Agent 面板使用懒加载。

```powershell
npm.cmd ci
npm.cmd run dev
npm.cmd run lint
npm.cmd test -- --run
npm.cmd run build
npm.cmd run test:e2e
```

Playwright 需要本机 MySQL 3306 可用；首次运行执行 `npx.cmd playwright install chromium`。E2E 覆盖 Revision/Diff/Restore、权限、Agent 成功、Evidence/Trace，以及 invalid output、unknown tool、timeout、非法 Evidence 和 cancel。开发服务器把 `/api` 代理到 `http://localhost:8080`。
