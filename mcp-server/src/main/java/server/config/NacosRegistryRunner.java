package server.config;

import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 启动时向 Nacos 注册 mcp-server 服务
 *
 * author Hao
 * date 2026/8/18
 */
@Slf4j
@Component
public class NacosRegistryRunner implements ApplicationRunner {

    private final NamingService namingService;

    @Value("${nacos.enabled:true}")
    private boolean enabled;

    @Value("${nacos.service-name:mcp-server}")
    private String serviceName;

    @Value("${server.port:8081}")
    private int port;

    public NacosRegistryRunner(NamingService namingService) {
        this.namingService = namingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Nacos 注册已禁用 (nacos.enabled=false)，跳过注册");
            return;
        }
        try {
            // 等待 Nacos 客户端连接就绪（异步建连，直接注册会报 STARTING）
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                if ("UP".equals(namingService.getServerStatus())) {
                    break;
                }
                Thread.sleep(500);
            }

            String ip = localIp();
            namingService.registerInstance(serviceName, ip, port);
            log.info("已注册到 Nacos: {} -> {}:{}", serviceName, ip, port);
        } catch (Exception e) {
            log.error("Nacos 注册失败: {}", e.getMessage(), e);
        }
    }

    /** 获取本机局域网 IP（UDP 连接技巧，不发真实流量） */
    private String localIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
