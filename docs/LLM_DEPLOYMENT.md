# LLM 配置与部署迁移

BioNote 的真实 LLM provider 为 `openai-compatible`。本地默认 profile 已启用真实调用；服务器使用显式的 `real-llm` profile，避免未挂载密钥时意外向外部服务发送数据。

## 配置文件格式

创建一个仅后端进程可读的文件，例如 `/etc/bionote/llm`：

```text
base_url (OpenAI): https://your-provider.example/v1/
api_key: YOUR_API_KEY
model: YOUR_MODEL_NAME
```

`base_url (OpenAI)` 应指向兼容 OpenAI Chat Completions API 的根路径。后端会在其后请求 `chat/completions`。

本地从仓库根目录启动时会读取 `./llm`；从 `backend/` 启动时会读取 `../llm`。不要把包含真实密钥的 `llm` 提交到版本库或复制进 Docker 镜像。

## 本地启用

项目默认使用 `local` profile，该 profile 已设置：

```yaml
agent:
  enabled: true
  provider: openai-compatible
```

因此在 `backend/` 运行以下命令后，页面发起的 Agent 请求会使用 `../llm`：

```powershell
.\mvnw.cmd spring-boot:run
```

## 普通服务器或 systemd

把配置文件放到应用目录之外，并通过环境变量指定绝对路径。将数据库 profile 与真实 LLM profile 组合启用：

```bash
export LLM_CONFIG_PATH=/etc/bionote/llm
export DEV_SEED_ENABLED=false
java -jar app.jar --spring.profiles.active=dev,real-llm
```

systemd 服务可使用：

```ini
Environment=LLM_CONFIG_PATH=/etc/bionote/llm
Environment=SPRING_PROFILES_ACTIVE=dev,real-llm
Environment=DEV_SEED_ENABLED=false
```

请将文件权限限制为运行后端的系统用户可读，例如 Linux 上使用 `chmod 600 /etc/bionote/llm`。

## Docker 部署

以只读文件挂载配置，不要在构建阶段 `COPY` 密钥：

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev,real-llm \
  -e LLM_CONFIG_PATH=/run/secrets/bionote-llm \
  -e DEV_SEED_ENABLED=false \
  -v /srv/bionote/llm:/run/secrets/bionote-llm:ro \
  bionote-backend
```

Docker Compose 对应配置：

```yaml
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: dev,real-llm
      LLM_CONFIG_PATH: /run/secrets/bionote-llm
      DEV_SEED_ENABLED: "false"
    volumes:
      - /srv/bionote/llm:/run/secrets/bionote-llm:ro
```

## Kubernetes 部署

建议把完整配置文件存入 Secret，并以文件形式挂载：

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: dev,real-llm
  - name: LLM_CONFIG_PATH
    value: /run/secrets/bionote/llm
  - name: DEV_SEED_ENABLED
    value: "false"
volumeMounts:
  - name: llm-config
    mountPath: /run/secrets/bionote
    readOnly: true
volumes:
  - name: llm-config
    secret:
      secretName: bionote-llm
```

Secret 中的键名应为 `llm`，内容使用上面的三行格式。

## 更换服务商、模型或密钥

后端在每次真实模型调用前重新读取服务地址和密钥；新创建的 Agent Run 或问答会读取当前模型名。仅轮换同一服务商的 API Key 时，替换文件后通常无需重启。

推荐先生成完整的新文件，再原子替换旧文件，避免后端恰好读到写入一半的配置。已经创建或排队的 Agent Run 会保留创建时的模型名；正在进行中的 HTTP 请求也不会被切换。因此跨服务商或修改模型名时，应先停止接收新 Agent 请求，等待队列和运行中任务结束，再替换配置并恢复服务。容器平台若通过 Secret 更新挂载文件，也应确认其更新方式保留同一路径。

如需紧急停止真实调用，设置 `AGENT_ENABLED=false` 并重启后端。自动化测试仍通过 `application-test.yml` 使用 deterministic fake provider，不会消耗真实模型额度。
