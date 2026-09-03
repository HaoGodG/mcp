# mcp

MCP（Model Context Protocol）服务端/客户端/网关生态的演示工程，包含四个模块。

```
┌─ mcp-server    标准 MCP 服务（Nacos 注册）
├─ sdk-server    SDK 能力服务（直连对外）
├─ mcp-client    MCP 客户端（Nacos 发现 + token/channel 双身份）
└─ mcp-sdk       SDK 客户端库（jar，单 URL 配置）
```

## 模块说明

| 模块 | 端口 | 作用 |
|------|------|------|
| **mcp-server** | 8081 | 纯标准 MCP 服务：单端点内容协商（同步工具返回 JSON / 流式工具返回 SSE），10 个工具（weather/echo/getQuote/getMetrics/date/calculator/translate/getUser/getOrder/getStock）；Mcp-Session-Id 会话管理；按调用方拆分日志（保留 7 天）；注册到 Nacos |
| **sdk-server** | 8084 | SDK 能力独立进程：个性化（`/personalize`，X-SDK-Key 鉴权）+ 加密 MCP（`/mcp`，RSA 全报文解密），内置 5 个同步工具；直连提供服务，不经过注册中心 |
| **mcp-client** | 8090 | MCP 客户端（启动即演示完整流程 + 全工具遍历）：支持 token（网关 tokenApply 换 JWT）与 channel（X-Channel-Id + who-am-i）两种身份模式；从 Nacos 发现目标服务；会话失效自动重开 |
| **mcp-sdk** | jar | SDK 客户端库（`dist/mcp-sdk-1.0.0.jar`）：单 URL 指向 sdk-server，自动拼接 `/mcp`（RSA 全报文加密调用）与 `/personalize`（个性化）；零框架依赖（JDK HttpClient + Jackson） |

## 快速启动

```bash
# 依赖：JDK 17+、Maven、Nacos（scripts/nacos.sh 或独立部署 8848）

# 1. Nacos（如未运行）
scripts/nacos.sh start

# 2. mcp-server（8081，自动注册 Nacos）
cd mcp-server && mvn spring-boot:run

# 3. sdk-server（8084，直连）
cd sdk-server && mvn spring-boot:run

# 4. mcp-client（8090，演示完整流程 + 全工具遍历；默认配置：token → 8088 → /mcp-server）
cd mcp-client && mvn spring-boot:run
# channel 模式示例（内网通道 → Nacos 发现网关 → /sdk-server）：
#   mvn spring-boot:run -Dspring-boot.run.arguments="--mcp.mode=sync --mcp.path=/sdk-server --mcp.auth-mode=channel --mcp.channel-id=client-channel-out --mcp.who-am-i=hao"

# 5. SDK 集成（外部工程引入）
# 见 dist/DEV_GUIDE.md
```

## 测试

```bash
# 四场景一键测试（mcp-server + sdk-server）
scripts/demo-scenarios.sh

# 持续请求测试（两链路 3-5s 一笔）
scripts/sustained-test.sh [每链路请求数]

# 测试案例登记（入站/出站）
docs/TEST_CASES.md
```

## 文档

| 文档 | 内容 |
|------|------|
| `docs/MCP_TOOLS.md` | mcp-server 使用文档（端点/工具/会话/日志/网关） |
| `docs/NACOS_GUIDE.md` | Nacos 注册发现接入指南 |
| `docs/TEST_CASES.md` | 测试案例登记表 |
| `dist/DEV_GUIDE.md` | mcp-sdk 开发手册 |