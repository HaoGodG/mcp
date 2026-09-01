#!/bin/bash
# 持续请求测试：3-5s 一笔
#  链路 A：token（client-from-out）→ nginx 8088 → /mcp-server（getStock）
#  链路 B：channel（client-channel-out + who-am-i: hao）→ Nacos 发现网关 → /sdk-server（calculator）
# 用法: ./sustained.sh [每链路请求数]  默认 15

COUNT="${1:-15}"
A_OK=0; A_FAIL=0; B_OK=0; B_FAIL=0

TOKEN=$(curl -s -X POST http://127.0.0.1:8088/api/auth/tokenApply -H 'Content-Type: application/json' \
  -d '{"clientId":"client-from-out","clientSecret":"dev-secret"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
GW=$(curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=open-cloud-mcpgateway" \
  | python3 -c "import sys,json; h=json.load(sys.stdin)['hosts'][0]; print(h['ip']+':'+str(h['port']))")
echo "网关(发现): $GW  | 每链路 $COUNT 笔，间隔 3-5s"

for i in $(seq 1 $COUNT); do
  # ------ 链路 A：token → 8088/mcp-server ------
  start=$(python3 -c "import time; print(time.time())")
  code=$(curl -s -o /tmp/sust-a.json -w '%{http_code}' -X POST http://127.0.0.1:8088/mcp-server \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":"A-'$i'","method":"tools/call","params":{"name":"getStock","arguments":{"code":"600519"}}}' -m 8)
  end=$(python3 -c "import time; print(time.time())")
  cost=$(python3 -c "print(f'{$end-$start:.2f}')")
  if [ "$code" = "200" ]; then A_OK=$((A_OK+1)); echo "[A#$i] HTTP $code ${cost}s ✅"; else A_FAIL=$((A_FAIL+1)); echo "[A#$i] HTTP $code ${cost}s ❌ $(head -c 120 /tmp/sust-a.json)"; fi

  sleep $((RANDOM % 3 + 3))

  # ------ 链路 B：channel → Nacos 网关 → /sdk-server ------
  start=$(python3 -c "import time; print(time.time())")
  code=$(curl -s -o /tmp/sust-b.json -w '%{http_code}' -X POST "http://$GW/sdk-server" \
    -H "X-Channel-Id: client-channel-out" -H "who-am-i: hao" -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":"B-'$i'","method":"tools/call","params":{"name":"calculator","arguments":{"expression":"(1+2)*3"}}}' -m 8)
  end=$(python3 -c "import time; print(time.time())")
  cost=$(python3 -c "print(f'{$end-$start:.2f}')")
  if [ "$code" = "200" ]; then B_OK=$((B_OK+1)); echo "[B#$i] HTTP $code ${cost}s ✅"; else B_FAIL=$((B_FAIL+1)); echo "[B#$i] HTTP $code ${cost}s ❌ $(head -c 120 /tmp/sust-b.json)"; fi

  sleep $((RANDOM % 3 + 3))
done

echo ""
echo "════ 汇总 ════"
echo "链路 A（token→/mcp-server）: 成功 $A_OK / $COUNT，失败 $A_FAIL"
echo "链路 B（channel→/sdk-server）: 成功 $B_OK / $COUNT，失败 $B_FAIL"