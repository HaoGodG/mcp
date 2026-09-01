#!/bin/bash
# Nacos 2.3.2 启动脚本（standalone 单机）
# 用法: ./nacos.sh start|stop|status
# 依赖: JAVA_HOME 或系统 java 8+
set -e

NACOS_HOME="${NACOS_HOME:-$HOME/tools/nacos}"
JAVA_HOME="${JAVA_HOME:-/Users/hao/Library/Java/JavaVirtualMachines/ms-17.0.17/Contents/Home}"

case "$1" in
  start)
    export JAVA_HOME
    cd "$NACOS_HOME/bin"
    sh startup.sh -m standalone
    echo "Nacos 启动中，等待就绪..."
    for i in $(seq 1 60); do
      code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' http://127.0.0.1:8848/nacos/v1/console/health/readiness 2>/dev/null || true)
      [ "$code" = "200" ] && echo "✅ Nacos 就绪: http://localhost:8848/nacos" && exit 0
      sleep 2
    done
    echo "⚠️ 等待超时，请查看日志: $NACOS_HOME/logs/start.out"
    ;;
  stop)
    cd "$NACOS_HOME/bin"
    sh shutdown.sh
    ;;
  status)
    code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' http://127.0.0.1:8848/nacos/v1/console/health/readiness 2>/dev/null || echo 000)
    echo "Nacos: $([ "$code" = "200" ] && echo '运行中 ✅' || echo '未运行 ❌')"
    ;;
  *)
    echo "用法: $0 start|stop|status"
    ;;
esac
