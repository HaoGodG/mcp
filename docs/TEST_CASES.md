# MCP 测试案例登记表

> 维护：2026-09-03
> 用法：用户按案例名称指定测试，例如"测 mcp-server 入站"或"测 sdk-server 出站"
> 网关接口以最新文档为准（mcp-gateway/docs/接口文档.md）：对外 nginx `:8088`，内网 Nacos 发现 `open-cloud-mcpgateway`

---

## 案例 A：mcp-server 入站（token）

**标识**：`http://127.0.0.1:8088/mcp-server`

| 项 | 值 |
|---|---|
| 寻址方式 | **直连 nginx**（nacos.enabled=false） |
| 访问地址 | `http://127.0.0.1:8088/mcp-server`（nginx → 网关 → nacos://mcp-server） |
| 身份 | **token 模式**：`Authorization: Bearer <JWT>`（tokenApply 凭证 `client-from-out`/`dev-secret`） |
| client 参数 | `--nacos.enabled=false --mcp.base-url=http://127.0.0.1:8088 --mcp.path=/mcp-server --mcp.mode=sse --mcp.auth-mode=token --mcp.client-id=client-from-out --mcp.client-secret=dev-secret` |
| 后端 | mcp-server（8081），serverInfo: mcp-server |
| 工具 | 10 个全量（tools/list 按用户勾选过滤；client-from-out 勾选 weather/getQuote/getStock） |
| 验证状态 | ✅ 通过（2026-08-20 ~ 2026-09-01，多次 + 10 笔/s × 10min 100%） |

---

## 案例 B：sdk-server 出站（channel）

**标识**：`nacos://open-cloud-mcpgateway/sdk-server`

| 项 | 值 |
|---|---|
| 寻址方式 | **Nacos 服务发现**（发现 open-cloud-mcpgateway 网关实例） |
| 访问地址 | `nacos://open-cloud-mcpgateway/sdk-server` → 发现网关 + 路径 `/sdk-server` |
| 身份 | **channel 模式**：`X-Channel-Id: client-channel-out` + `who-am-i: hao`（跳过 tokenApply） |
| client 参数 | `--nacos.enabled=true --nacos.service-name=open-cloud-mcpgateway --mcp.path=/sdk-server --mcp.mode=sync --mcp.auth-mode=channel --mcp.channel-id=client-channel-out --mcp.who-am-i=hao` |
| 后端 | sdk-server（8084），serverInfo: mcp-sdk-server |
| 工具 | 5 个同步（date/calculator/translate/getUser/getStock，全开） |
| 验证状态 | ✅ 通过（2026-08-30 ~ 2026-09-01，多次 + 10 笔/s × 10min 100%） |

> ⚠️ 注意：sdk-server 仅同步工具，client 需用 `mode=sync`；channel 用户是 `client-channel-out`（permissions.yml 在册，sdk-server 全开），`who-am-i: hao` 为固定通道凭证头

---

## 测试执行模板

```bash
# 案例 A（mcp-server 入站）
cd mcp-client && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 \
  --nacos.enabled=false --mcp.base-url=http://127.0.0.1:8088 --mcp.path=/mcp-server \
  --mcp.mode=sse --mcp.auth-mode=token --mcp.client-id=client-from-out --mcp.client-secret=dev-secret"

# 案例 B（sdk-server 出站）
cd mcp-client && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 \
  --nacos.enabled=true --nacos.service-name=open-cloud-mcpgateway --mcp.path=/sdk-server \
  --mcp.mode=sync --mcp.auth-mode=channel --mcp.channel-id=client-channel-out --mcp.who-am-i=hao"
```

---

## 持续/QPS 测试

| 场景 | 脚本 | 结果 |
|---|---|---|
| 3-5s/笔持续 | `scripts/sustained-test.sh [每链路请求数]` | ✅ 16/16 |
| 混合 10min（异常注入 + sdk-server 重启 3 次） | 手工编排 | ✅ A 55/55，B 53/55（2 次失败=重启窗口 502，预期） |
| 3-5 笔/s × 10min | `/tmp/qps-test.sh 600` | ✅ 1978/1978，100% |
| 10 笔/s × 10min | `/tmp/qps-test.sh 600` | ✅ 5214/5214，100% |

---

## 案例登记规则

- 新案例经用户确认后登记至此（名称、寻址、参数、工具、验证状态）
- 用户指定案例名称 → 按登记参数执行 → 更新验证状态