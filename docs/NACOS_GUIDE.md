# Nacos 服务注册发现接入指南

本工程（mcp 系列）使用 Nacos 2.3.2 做服务注册发现。本文档说明：
1. Nacos 控制台与连接信息
2. mcp-server 注册方式（已实现，参考实现）
3. **其他工程如何注册上来**（重点）

---

## 1. 基础设施信息

| 项 | 值 |
|---|---|
| Nacos 版本 | 2.3.2（standalone 单机，嵌入式存储） |
| **控制台 URL** | `http://localhost:8848/nacos` |
| 控制台账号 | `nacos` / `nacos` |
| **注册/发现地址** | `127.0.0.1:8848`（对外部署则换服务器 IP） |
| 健康检查 | `http://127.0.0.1:8848/nacos/v1/console/health/readiness` |
| 启动脚本 | `scripts/nacos.sh start\|stop\|status` |
| 本机安装目录 | `~/tools/nacos` |

> 注意：Nacos 2.x 使用 gRPC，客户端还需连通 **9848**（主端口+1000）端口，本机部署无需额外配置。

---

## 2. 本工程服务注册现状

| 服务 | 端口 | Nacos 注册 | 说明 |
|---|---|---|---|
| `mcp-server` | 8081 | ✅ 注册 | 纯标准 MCP（/mcp + /mcp/sync + /mcp/ndjson），10 工具 |
| `sdk-server` | 8084 | ❌ 不注册 | SDK 能力（个性化 /personalize + 加密 MCP），**直连对外提供服务**，面向外部/互联网客户 |
| `mcp-client` | 8090 | ✅ 发现 | 标准 MCP 消费者，从 Nacos 发现目标服务 |
| `mcp-sdk` | jar | — | 客户端库，单 URL 指向 sdk-server |

- mcp-server 实例：`192.168.8.137:8081`（自动探测本机 IP，等待连接 UP 后注册）
- sdk-server 定位：独立进程、直连提供服务，**不经过注册中心**（控制台服务列表看不到属正常）

---

## 3. 其他工程注册接入（重点）

### 3.1 添加依赖

```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.3.2</version>
</dependency>
```

> ⚠️ 若工程使用 Spring Boot 3.x，需将 jackson 系列显式压到 2.15+（nacos-client 传递的低版本 jackson 会破坏 Spring Boot）：
> ```xml
> <dependency>
>     <groupId>com.fasterxml.jackson.core</groupId>
>     <artifactId>jackson-databind</artifactId>
>     <version>2.17.2</version>
> </dependency>
> <dependency>
>     <groupId>com.fasterxml.jackson.core</groupId>
>     <artifactId>jackson-core</artifactId>
>     <version>2.17.2</version>
> </dependency>
> <dependency>
>     <groupId>com.fasterxml.jackson.core</groupId>
>     <artifactId>jackson-annotations</artifactId>
>     <version>2.17.2</version>
> </dependency>
> ```

### 3.2 配置

```yaml
nacos:
  enabled: true
  server-addr: 127.0.0.1:8848      # Nacos 地址（对外部署换 IP）
  service-name: your-service-name  # 服务名，如 my-mcp-tool
# 注册端口取 server.port（Spring Boot 自动）
```

### 3.3 注册代码（启动时注册，等待连接就绪）

```java
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.DatagramSocket;
import java.net.InetAddress;

@Component
public class NacosRegistryRunner implements ApplicationRunner {

    @Value("${nacos.enabled:true}")
    private boolean enabled;
    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;
    @Value("${nacos.service-name}")
    private String serviceName;
    @Value("${server.port}")
    private int port;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        NamingService naming = NamingFactory.createNamingService(serverAddr);

        // 等待连接就绪（异步建连，立即注册会报 STARTING）
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if ("UP".equals(naming.getServerStatus())) break;
            Thread.sleep(500);
        }

        naming.registerInstance(serviceName, localIp(), port);
        System.out.println("已注册到 Nacos: " + serviceName + " -> " + localIp() + ":" + port);
    }

    private String localIp() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
```

### 3.4 验证注册成功

```bash
# 直接查 Nacos API
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=your-service-name"
# 期望: {"hosts":[{"ip":"192.168.8.137","port":8082,"healthy":true,...}]}

# 或控制台查看
# http://localhost:8848/nacos → 服务管理 → 服务列表 → your-service-name
```

---

## 4. 本工程 client 如何发现你的服务

mcp-client 的发现逻辑在 `client/config/NacosDiscoveryService.java`：

```yaml
# mcp-client/application.yml
nacos:
  enabled: true
  server-addr: 127.0.0.1:8848
  service-name: mcp-server   # ← 改成你要发现的服务名
```

- 启动时从 Nacos 取一个健康实例作为 base-url
- 发现失败自动回退 `mcp.base-url` 配置

---

## 5. 常见问题

| 问题 | 处理 |
|---|---|
| 注册报 `Client not connected, current status:STARTING` | 等待连接 UP 后再注册（见 3.3） |
| 启动报 jackson `EnumFeature` 缺失 | 按 3.1 压制 jackson 版本到 2.17.2 |
| 实例注册了但 `healthy: false` | 检查服务端口是否真的可访问（Nacos 会心跳探测） |
| 服务注销不掉 | 进程 kill -9 后等心跳超时（默认 15s）；正常退出走 shutdown 钩子 |
