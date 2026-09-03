# mcp-server 使用文档（场景化 · 单端点版）

> 更新：2026-08-19
> 直连：`http://localhost:8081/mcp`（**唯一标准端点**，Streamable HTTP 内容协商） ｜ 经网关：`http://localhost:8080`
> 一键测试：`./scripts/demo-scenarios.sh`

---

## 一、端点说明（最佳实践：单端点 + 内容协商）

| 端点 | 类型 | 说明 |
|---|---|---|
| **`POST /mcp`** | ⭐ 标准端点 | **所有方法都走这里**；响应类型由服务端按工具决定：同步工具 → `application/json`，流式工具 → `text/event-stream` |
| `GET /mcp` | 标准（可选） | SSE 通道：服务端主动推送（当前发心跳） |
| `POST /mcp/sync` | 兼容端点 | 仅同步工具（旧客户端/网关路由保留） |
| `POST /mcp/ndjson` | 扩展端点 | NDJSON 裸流（**非 MCP 标准**，数据管道类消费者用） |

**客户端接入只需一个 URL**：`POST /mcp`，`Accept: application/json, text/event-stream`（双声明），
服务端自动协商——这就是 MCP Streamable HTTP 官方推荐形态。

**工具全量 10 个**（`tools/list` 一次返回）：

| 类型 | 工具 |
|---|---|
| 流式（SSE 推送） | `weather`、`echo`、`getQuote`、`getMetrics` |
| 同步（JSON 返回） | `date`、`calculator`、`translate`、`getUser`、`getOrder`、`getStock` |

---

## 二、场景 1：SSE 流式（统一走标准端点 /mcp）

### 1.1 天气预报（weather）— 逐天推送 3 帧
```bash
curl -N -X POST http://localhost:8081/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"weather","arguments":{"city":"上海"}}}'
```
```
event:message  {"city":"上海","day":"今天","weather":"阴","temperature":28,"humidity":75,"wind":"南风3级"}
event:message  {"city":"上海","day":"明天","weather":"雷阵雨","temperature":27,...}
event:message  {"city":"上海","day":"后天","weather":"多云","temperature":29,...}
event:message  {"city":"上海","done":true}
```
支持城市：**宁波 / 上海 / 北京 / 深圳**（未知城市返回 error 帧）

### 1.2 打字机回显（echo）— 逐字推送
```bash
curl -N -X POST http://localhost:8081/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"echo","arguments":{"text":"hello mcp"}}}'
```
输出：`{"chunk":"h"}` → `{"chunk":"he"}` → … → `{"chunk":"hello mcp"}` → `{"done":true}`（200ms/字）

### 1.3 实时行情流（getQuote）— 30 秒长连接 ⭐
```bash
curl -N -X POST http://localhost:8081/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"getQuote","arguments":{"symbol":"AAPL"}}}'
```
| 阶段 | 行为 |
|---|---|
| 0-10s | 每秒 1 tick（随机游走：价格/涨跌幅/成交量） |
| 10-15s | 暂停（连接保持） |
| 15-30s | 每秒 1 tick 续推 |
| 30s | 流结束，连接释放 |

```
t=1s  {"code":"AAPL","name":"苹果","price":181.29,"changePercent":1.73,"volume":62491}
t=2s  {"code":"AAPL","name":"苹果","price":180.72,"changePercent":1.42,"volume":236615}
... 共 25 ticks，30.0s 释放
```
支持标的：**AAPL / TSLA / 600519（茅台）/ 000001（平安银行）/ 300750（宁德时代）/ 00700（腾讯）**

### 1.4 实时指标流（getMetrics）— 与行情同节奏
```bash
curl -N -X POST http://localhost:8081/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"getMetrics","arguments":{"metric":"cpu"}}}'
```
指标：`cpu`（20-90% 波动）/ `mem`（递增）/ `net`（50-900 MB/s 随机）

---

## 三、场景 2：NDJSON 裸流（扩展端点 /mcp/ndjson）

与场景 1 同工具集，区别是**逐行 JSON 无 SSE 格式**（无 event:/data: 包装，响应头 `Transfer-Encoding: chunked`）。

```bash
curl -N -X POST http://localhost:8081/mcp/ndjson -H "Content-Type: application/json" -H "Accept: application/x-ndjson" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"weather","arguments":{"city":"北京"}}}'
```
```
{"jsonrpc":"2.0","id":"1","result":{"content":[{"text":"{\"city\":\"北京\",\"day\":\"今天\",\"weather\":\"晴\",\"temperature\":31,...}"}]}}
{"jsonrpc":"2.0","id":"1","result":{...明天...}}
{"jsonrpc":"2.0","id":"1","result":{...后天...}}
{"jsonrpc":"2.0","id":"1","result":{..."done":true...}}
```

---

## 四、场景 3：同步 JSON（统一走标准端点 /mcp，服务端返回 JSON）

### 3.1 业务查询三件套（内置数据）
```bash
# 用户查询（u-1001 张三 / u-1002 李四 / u-1003 王五）
curl -s -X POST http://localhost:8081/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"getUser","arguments":{"userId":"u-1002"}}}'
# → {"name":"李四","role":"user","status":"ACTIVE","email":"lisi@mcp.dev","userId":"u-1002"}

# 订单查询（o-1001 ~ o-1005，状态 PAID/SHIPPED/COMPLETED/CANCELLED）
curl -s -X POST http://localhost:8081/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"getOrder","arguments":{"orderId":"o-1002"}}}'
# → {"orderId":"o-1002","userId":"u-1002","amount":1299.0,"status":"SHIPPED","createdAt":"2026-08-05 14:20:00"}

# 股票快照（与 getQuote 同源行情：价格/涨跌幅/最高最低/成交量）
curl -s -X POST http://localhost:8081/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"getStock","arguments":{"code":"600519"}}}'
# → {"code":"600519","name":"贵州茅台","industry":"白酒","price":1700.14,"changePercent":1.74,
#    "high":1717.15,"low":1654.41,"volume":3953735}
```

### 3.2 工具类
```bash
# 计算器：(1+2)*3 → 9
# 翻译：hello ↔ 你好（mock 词典）
# 日期：默认 yyyy-MM-dd HH:mm:ss
```
> 不存在的 userId/orderId/股票代码返回 JSON-RPC 错误（-32603 + 具体原因）
> 旧客户端可继续用 `/mcp/sync`（兼容端点，行为一致）

---

## 五、场景 4：个性化入口（独立服务 sdk-server，非 MCP 工具）

> 个性化已独立为 **sdk-server**（`http://localhost:8084`，独立进程直连，不经过注册中心）。
> 仅 `X-SDK-Key: internal-sdk-key-2026` 可调；Java 侧用 mcp-sdk（`dist/DEV_GUIDE.md`，单 URL 指向 sdk-server，支持 RSA 全报文加密）。

### 4.1 获取偏好 + 用户画像
```bash
curl -s -X POST http://localhost:8084/personalize/preferences \
  -H "Content-Type: application/json" -H "X-SDK-Key: internal-sdk-key-2026" \
  -d '{"userId":"u-1001"}'
```
```json
{"userId":"u-1001","profile":["tech","finance"],
 "preferences":{"theme":"light","language":"zh-CN","notifyEnabled":true,"recommendCategories":["tech","sports"]}}
```

### 4.2 更新偏好（合并，按 userId 独立存储）
```bash
curl -s -X POST http://localhost:8084/personalize/preferences/update \
  -H "Content-Type: application/json" -H "X-SDK-Key: internal-sdk-key-2026" \
  -d '{"userId":"u-1001","preferences":{"recommendCategories":["music"],"theme":"dark"}}'
```

### 4.3 个性化推荐（画像 + 偏好驱动，含匹配度评分）
```bash
curl -s -X POST http://localhost:8084/personalize/recommendations \
  -H "Content-Type: application/json" -H "X-SDK-Key: internal-sdk-key-2026" \
  -d '{"userId":"u-1001"}'
```
```
profile: [tech, finance]
tech    | AI 前沿技术周报    | score 9.7
tech    | 云原生架构实践指南 | score 9.2
finance | 市场行情分析      | score 8.9
finance | 基金定投策略入门   | score 8.1
```
> 偏好 `recommendCategories` 可覆盖推荐范围；内容库 4 分类 8 条；不同用户（u-1001/u-1002/u-1003）画像不同 → 推荐不同

### 4.4 SDK 方式（Java）
```java
McpSdkClient sdk = new McpSdkClient("http://localhost:8081", "internal-sdk-key-2026");
sdk.getPreferences("u-1001");
sdk.updatePreferences("u-1001", Map.of("theme", "dark"));
sdk.recommend("u-1001");
// 加密同步 MCP：sdk.callSyncSecure("calculator", Map.of("expression", "(1+2)*3"))
```
详见 `dist/DEV_GUIDE.md`

---

## 六、快速测试

```bash
# 一键跑全部四个场景（需 mcp-server 8081 + sdk-server 8084）
./scripts/demo-scenarios.sh

# 查看工具清单（标准端点，全量 10 个）
curl -s -X POST http://localhost:8081/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'
```

### client 全工具遍历测试（[6/6]）

mcp-client 启动后自动执行完整流程 + **全工具遍历**：
- `[1/4]` token → `[2/4]` initialize → `[3/4]` tools/list → `[4/4]` weather → `[5/5]` getQuote → `[6/6]` 全工具
- `[6/6]` 从 tools/list 逐个调用全部 10 个工具：同步工具等完整结果，流式工具取前 3 帧（避免 30s 长流阻塞）
- 配置：`mcp.client-name`（默认 mcp-client，写入 clientInfo.name，服务端按此建会话/拆日志）

```bash
# 跑 client 完整流程（直连 mcp-server，Nacos 发现）
cd mcp-client && mvn spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8090 --mcp.mode=sse --mcp.path=/mcp \
  --mcp.token=admin-token --mcp.base-url=http://localhost:8081 --nacos.service-name=mcp-server"
```

---

## 七、会话管理（Mcp-Session-Id，符合 MCP 规范）

| 阶段 | 行为 |
|---|---|
| **创建** | `initialize` 时服务端生成会话，响应头 `Mcp-Session-Id` 下发，记录调用方（clientInfo.name） |
| **使用** | 后续请求携带 `Mcp-Session-Id` 头 → 服务端关联调用方（用于日志拆分），并刷新最后访问时间 |
| **显式终止** | `DELETE /mcp` + `Mcp-Session-Id`（客户端不再需要时；mcp-client 流程结束自动发送） |
| **空闲回收** | 会话超时自动清理（`mcp.session-idle-timeout`，默认 30 分钟，每 5 分钟巡检） |
| **失效处理** | 带失效会话的请求 → **HTTP 404** → 客户端自动重新 initialize 并重试一次 |

```bash
# 手动验证会话生命周期
SID=$(curl -s -D - -o /dev/null -X POST http://localhost:8081/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"demo","version":"1.0"}}}' \
  | grep -i mcp-session-id | tr -d '\r' | awk '{print $2}')

curl -s -o /dev/null -w '带会话调用: %{http_code}\n' -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}'

curl -s -o /dev/null -w 'DELETE 终止: %{http_code}\n' -X DELETE http://localhost:8081/mcp -H "Mcp-Session-Id: $SID"

curl -s -o /dev/null -w '终止后再调(应404): %{http_code}\n' -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":"3","method":"tools/list","params":{}}'
```

---

## 八、日志（按调用方拆分，保留 7 天）

- **位置**：`mcp-server/logs/mcp-server/{调用方}/app-{yyyy-MM-dd}.log`（logback SiftingAppender）
- **调用方识别**：优先会话（Mcp-Session-Id → initialize 时 clientInfo.name），其次请求内 clientInfo.name
- **轮转**：按天滚动 + 保留 7 天（`maxHistory=7`，控制台同时输出）
- **每条请求日志**：`[MCP 请求] client=xxx method=tools/call id=... params={name=weather, arguments={city=宁波}}`
- 无会话的健康检查等请求进 `unknown` 目录

---

## 九、经网关访问（Nacos 服务发现）

> 网关接口以最新接口文档为准（`/Users/hao/IdeaProjects/mcp-gateway/mcp-gateway/docs/接口文档.md`）
> 对外入口：nginx `:8088`（唯一对外路径）；内网：Nacos 发现 `open-cloud-mcpgateway`

| 网关路由 | 对应后端 | 说明 |
|---|---|---|
| `POST /mcp-server` | `nacos://mcp-server/mcp`（标准端点） | mcp-server 全部 10 工具（tools/list 按用户勾选过滤） |
| `POST /sdk-server` | sdk-server 直连 8084（网关经 mcp-sdk RSA 加密通信） | 5 个同步工具 |

- 身份规则（业务路由统一）：`Authorization: Bearer <JWT>`（tokenApply 换）**或** `X-Channel-Id: <userId>` + `who-am-i: hao`（内网通道），二选一
- 客户端经网关：`tokenApply 换 JWT → Authorization: Bearer`（网关鉴权 + 工具 catalog 过滤）
- 网关 target 为 `nacos://mcp-server`：网关自身从 Nacos 发现后端实例
- 个性化入口（/personalize）为 sdk-server 直连接口，不经网关路由
