# MCP 测试案例登记表

> 维护：2026-08-20
> 用法：用户按案例名称指定测试，例如"测 mcp-server 入站"或"测 sdk-server 出站"

---

## 案例 1：mcp-server 入站

**标识**：`http://127.0.0.1:8088/mcp-server`

| 项 | 值 |
|---|---|
| 寻址方式 | **直连**（nacos.enabled=false，不走服务发现） |
| 访问地址 | `http://127.0.0.1:8088/mcp-server`（**nginx 8088 → 网关 8080** → mcp-server） |
| 认证 | tokenApply 换 JWT → `Authorization: Bearer` |
| client 参数 | `--nacos.enabled=false --mcp.base-url=http://127.0.0.1:8088 --mcp.path=/mcp-server --mcp.mode=sse --mcp.client-id=dev-client-1 --mcp.client-secret=dev-secret` |
| 后端 | mcp-server（8081），serverInfo: mcp-server |
| 工具（网关 catalog） | weather / getQuote / getStock（3 个） |
| 验证状态 | ✅ 通过（2026-08-20，端口 8080 时代）；8088 复测中 |

---

## 案例 2：sdk-server 出站

**标识**：`nacos://open-cloud-mcpgateway/sdk-server`

| 项 | 值 |
|---|---|
| 寻址方式 | **Nacos 服务发现**（发现 open-cloud-mcpgateway 网关实例） |
| 访问地址 | `nacos://open-cloud-mcpgateway/sdk-server` → 发现网关 `http://192.168.8.137:8080` + 路径 `/sdk-server` |
| 认证 | tokenApply 换 JWT → `Authorization: Bearer` |
| client 参数 | `--nacos.enabled=true --nacos.service-name=open-cloud-mcpgateway --mcp.base-url=http://localhost:8080 --mcp.path=/sdk-server --mcp.mode=sync --mcp.client-id=dev-client-1 --mcp.client-secret=dev-secret` |
| 后端 | sdk-server（8084），serverInfo: mcp-sdk-server |
| 工具 | date / calculator / translate / getUser / getStock（5 个同步工具） |
| 验证状态 | ✅ 通过（2026-08-20）：token→init→list→calculator→getUser→全工具遍历（5/5） |

> ⚠️ 注意：sdk-server 仅同步工具，client 需用 `mode=sync`（sse 模式调 weather 会报 unknown tool）

---

## 测试执行模板

用户说"测 XXX"，按案例参数执行：

```bash
# 案例 1（mcp-server 入站）
cd mcp-client && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 \
  --nacos.enabled=false --mcp.base-url=http://127.0.0.1:8080 --mcp.path=/mcp-server \
  --mcp.mode=sse --mcp.client-id=dev-client-1 --mcp.client-secret=dev-secret"

# 案例 2（sdk-server 出站）
cd mcp-client && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 \
  --nacos.enabled=true --nacos.service-name=open-cloud-mcpgateway \
  --mcp.base-url=http://localhost:8080 --mcp.path=/sdk-server \
  --mcp.mode=sync --mcp.client-id=dev-client-1 --mcp.client-secret=dev-secret"
```

---

## 案例登记规则

- 新案例经用户确认后登记至此（名称、寻址、参数、工具、验证状态）
- 用户指定案例名称 → 按登记参数执行 → 更新验证状态
