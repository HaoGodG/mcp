#!/bin/bash
# ======================================================================
# mcp-server 四场景使用测试脚本
#   场景 1: SSE 流式（天气多城市 / echo 打字机 / getQuote 行情流 / getMetrics 指标流）
#   场景 2: NDJSON 裸流（天气 / getQuote）
#   场景 3: 同步 JSON（date / calculator / translate / getUser / getOrder / getStock）
#   场景 4: 个性化入口（sdk-server 独立服务，X-SDK-Key 鉴权 + RSA 加密）
# 用法: ./demo-scenarios.sh [server-url] [sdk-server-url]
#   默认 server=http://localhost:8081  sdk-server=http://localhost:8084
# ======================================================================
set -e
BASE="${1:-http://localhost:8081}"
SDK_BASE="${2:-http://localhost:8084}"
KEY="internal-sdk-key-2026"
echo "===== 场景测试 | MCP: $BASE | SDK: $SDK_BASE ====="

# ---------------------------------------------------------------
echo ""
echo "════════ 场景 1：SSE 流式 ════════"

echo "--- 1.1 weather 上海（3 天预报逐天推送）---"
curl -s -N -X POST "$BASE/mcp" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"weather","arguments":{"city":"上海"}}}'

echo "--- 1.2 echo 打字机（逐字推送）---"
curl -s -N -X POST "$BASE/mcp" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"echo","arguments":{"text":"hello mcp"}}}'

echo "--- 1.3 getQuote AAPL（30s 行情流，先看前 4 帧）---"
curl -s -N -X POST "$BASE/mcp" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"getQuote","arguments":{"symbol":"AAPL"}}}' \
  | head -8

# ---------------------------------------------------------------
echo ""
echo "════════ 场景 2：NDJSON 裸流 ════════"
echo "--- 2.1 weather 北京（逐行 JSON）---"
curl -s -N -X POST "$BASE/mcp/ndjson" -H 'Content-Type: application/json' -H 'Accept: application/x-ndjson' \
  -d '{"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"weather","arguments":{"city":"北京"}}}'

# ---------------------------------------------------------------
echo ""
echo "════════ 场景 3：同步 JSON ════════"

echo "--- 3.1 date ---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"5","method":"tools/call","params":{"name":"date","arguments":{}}}'
echo ""

echo "--- 3.2 calculator (1+2)*3 ---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"6","method":"tools/call","params":{"name":"calculator","arguments":{"expression":"(1+2)*3"}}}'
echo ""

echo "--- 3.3 translate hello ---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"7","method":"tools/call","params":{"name":"translate","arguments":{"text":"hello"}}}'
echo ""

echo "--- 3.4 getUser u-1002（李四）---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"8","method":"tools/call","params":{"name":"getUser","arguments":{"userId":"u-1002"}}}'
echo ""

echo "--- 3.5 getOrder o-1002 ---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"9","method":"tools/call","params":{"name":"getOrder","arguments":{"orderId":"o-1002"}}}'
echo ""

echo "--- 3.6 getStock 600519（茅台快照，与 getQuote 同源行情）---"
curl -s -X POST "$BASE/mcp/sync" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":"10","method":"tools/call","params":{"name":"getStock","arguments":{"code":"600519"}}}'
echo ""

# ---------------------------------------------------------------
echo ""
echo "════════ 场景 4：个性化入口（SDK 专用，X-SDK-Key）════════"

echo "--- 4.1 获取偏好 + 画像（u-1001）---"
curl -s -X POST "$SDK_BASE/personalize/preferences" -H 'Content-Type: application/json' -H "X-SDK-Key: $KEY" \
  -d '{"userId":"u-1001"}'
echo ""

echo "--- 4.2 更新偏好（推荐分类改为 music）---"
curl -s -X POST "$SDK_BASE/personalize/preferences/update" -H 'Content-Type: application/json' -H "X-SDK-Key: $KEY" \
  -d '{"userId":"u-1001","preferences":{"recommendCategories":["music"],"theme":"dark"}}'
echo ""

echo "--- 4.3 个性化推荐（基于新偏好 + 画像）---"
curl -s -X POST "$SDK_BASE/personalize/recommendations" -H 'Content-Type: application/json' -H "X-SDK-Key: $KEY" \
  -d '{"userId":"u-1001"}'
echo ""

echo "--- 4.4 无 X-SDK-Key（应 403）---"
curl -s -X POST "$SDK_BASE/personalize/preferences" -H 'Content-Type: application/json' \
  -d '{"userId":"u-1001"}'
echo ""

echo ""
echo "===== 测试完成 ====="
