# 02：批次一——工程基础、认证与个人资料

## 本批次目标

建立后续所有模块依赖的真实运行底座，并把现有静态认证完全替换为后端认证。完成后，用户能够注册、使用邮箱登录、刷新恢复会话、退出、查看和修改个人资料。

本批次不实现项目、模板和记录业务；这些入口可暂时展示明确的“后续批次尚未接入”空状态，但不能继续假装 mock 操作成功。

## 1. 开始前检查

- 阅读公共契约。
- 检查 `frontend/package.json`、`src/api/client.js`、`src/store/authStore.js`、登录/注册/个人中心页面。
- 不读取 P3/P5 参考项目；本批次与指定参考范围无关。
- 记录当前可用的 Node/JDK/Maven/Docker 版本，并在 README 写出最低要求。

## 2. 后端骨架

在 `backend/` 创建 Spring Boot 工程：

- Java 17，Maven Wrapper。
- 依赖：Web、Validation、Security、Data JPA、MySQL、Flyway、JWT 实现、springdoc、测试。
- 环境：`application.yml` 只放非敏感默认值；数据库密码、JWT Secret、上传目录来自环境变量。
- 提供 `application-dev.yml` 和 `application-test.yml`。
- 新增根目录 `compose.yml`，至少能启动 MySQL 8，并带 healthcheck 和持久卷。
- 更新 `.gitignore`，排除 `.env`、数据库卷、本地上传目录、后端构建产物、前端 `dist` 和测试报告；提交 `.env.example` 而不是 `.env`。
- CORS 只允许配置的前端 origin；开发默认 `http://localhost:5173`。
- 建立统一 `ApiErrorResponse`、异常处理、分页响应和 traceId。
- `/actuator/health` 或等价健康端点可以匿名访问；其余业务默认需要认证。

## 3. 数据库与身份模型

Flyway 首个迁移创建 `users`。要求：

- 邮箱标准化后唯一，数据库唯一索引兜底。
- `display_name` 非空，建议 1–50 字符。
- 密码最少 8 位，服务端 BCrypt 哈希。
- `avatar_storage_key` 可空；头像与实验附件分开存储。
- 时间使用 UTC，实体启用审计时间。
- 添加 `version` 乐观锁字段。

不要在启动代码中静默修改表结构；JPA 使用 `validate`。

## 4. 认证接口

实现：

- `POST /api/v1/auth/register`
  - 输入 `displayName`、`email`、`password`。
  - trim/lowercase 邮箱后检查唯一。
  - 返回用户和 access token；不得返回 password hash。
- `POST /api/v1/auth/login`
  - 只接收 `email` 和 `password`，不支持用户名。
  - 登录失败使用统一消息，避免泄露邮箱是否存在。
- `GET /api/v1/auth/me`
  - 返回当前用户 ID、显示名、邮箱、头像 URL、注册时间。
- `POST /api/v1/auth/logout`
  - 返回 204；前端必须清除本地 token 和用户缓存。

JWT：

- `sub` 使用不可变 user ID，包含 `jti`、签发和过期时间。
- Secret 不得硬编码或提交真实值。
- access token 有合理短期有效期；MVP 不要求 refresh token。
- 401 时前端清理会话、保存原访问地址并跳转登录；重新登录后可回到原页。

## 5. 个人资料与头像

实现：

- `PUT /api/v1/users/me`：只允许修改 `displayName`。
- `POST /api/v1/users/me/avatar`：multipart 单图上传，建议最大 2 MB，只允许 JPEG/PNG/WebP。
- `GET /api/v1/users/{userId}/avatar`：返回正确 MIME；无头像时前端使用姓名首字 fallback。

头像存储要求：

- 随机 storage key，禁止使用原文件名作为路径。
- 验证扩展名、MIME 和实际图片签名；拒绝 SVG。
- 替换头像后可清理旧头像文件，但失败不能破坏当前头像引用。

用户 ID、邮箱和注册时间在个人资料 UI 中只读。修改邮箱、找回密码和注销账户不做。

## 6. 前端改造

### 6.1 统一 API 客户端

- 将 `src/api/client.js` 改为唯一真实客户端。
- 基础地址来自 `VITE_API_BASE_URL`，默认 `/api/v1`。
- 请求自动带 `Authorization: Bearer ...`。
- 统一处理 JSON、204、Blob、字段错误、网络错误和 401。
- 删除业务调用中的 `mockResponse` 使用；可先保留文件但不能再由认证链路引用。

### 6.2 认证 store

重写 `authStore`：

- 删除 `DEMO_USERS`、固定密码和静态 token。
- `restoreSession` 调用 `/auth/me`，而不是盲目信任 localStorage 用户对象。
- `login`、`register`、`logout` 使用真实 API。
- 明确 `loading`、`error` 状态，避免路由闪烁。

### 6.3 页面

- 登录页字段改为邮箱 + 密码，删除用户名登录提示和测试账号假逻辑。
- 注册页字段与后端一致，含前后端校验错误展示。
- 个人中心增加“编辑资料”表单和头像上传；保存成功后立即更新侧栏用户卡。
- 登录/注册提交时禁用重复点击；密码不写回页面或日志。
- ProtectedRoute 在恢复会话期间展示加载态，401 后正确跳转。

### 6.4 非 MVP 导航处理

本批次即可从 Sidebar 和 router 隐藏 AI 助手。不要删除用户可能还要保留的页面文件，直到批次六统一清理，但 MVP 中不能显示可点击的假入口。

## 7. 测试要求

后端至少覆盖：

- 注册成功、邮箱标准化唯一、重复邮箱冲突。
- 密码保存后不是明文。
- 邮箱登录成功、用户名/错误密码登录失败。
- 未认证访问 `/auth/me` 和资料更新返回 401。
- 用户只能修改自己的资料。
- 头像空文件、超限、错误类型被拒绝；正常图片可读取。

前端至少覆盖：

- 登录表单只提交邮箱。
- 401 清理会话并跳转。
- 注册字段错误正确展示。
- 修改资料后 store 和页面同步。

## 8. 验收命令

根据实际 wrapper 调整，但最终 README 必须能执行：

```bash
docker compose up -d mysql
cd backend && ./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run build
```

Windows 下同步提供 `mvnw.cmd` 用法。

## 9. 人工验收

1. 注册 `User@Example.com` 后，使用 `user@example.com` 再注册会被拒绝。
2. 刷新浏览器仍能通过 `/auth/me` 恢复登录。
3. 手工篡改/清除 token 后访问受保护页会回到登录页。
4. 修改显示名和头像后，个人中心及侧栏同时刷新。
5. 退出后，浏览器返回受保护页不能看到旧业务内容。

## 10. 本批次禁止事项

- 不使用静态账号兜底。
- 不把 JWT Secret、数据库密码写入仓库。
- 不在前端保存密码。
- 不提前实现项目/模板/记录业务。
