package client.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Nacos 服务发现组件（client 角色：消费者，从注册中心寻址 mcp-server）
 *
 * author Hao
 * date 2026/8/18
 */
@Slf4j
@Service
public class NacosDiscoveryService {

    private final NamingService namingService;

    @Value("${nacos.enabled:false}")
    private boolean enabled;

    @Value("${nacos.service-name:mcp-server}")
    private String serviceName;

    public NacosDiscoveryService(NamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * 从 Nacos 发现服务地址（http://ip:port），失败自动重试
     *
     * @return 发现的地址；未启用或重试仍失败返回 null（调用方回退配置地址）
     */
    public String discover() {
        if (!enabled) {
            log.info("Nacos 发现未启用，使用配置地址");
            return null;
        }

        // 重试 3 次：应对实例注册时序抖动 / 客户端缓存未同步
        Exception last = null;
        for (int i = 1; i <= 3; i++) {
            try {
                Instance instance = namingService.selectOneHealthyInstance(serviceName);
                String url = "http://" + instance.getIp() + ":" + instance.getPort();
                log.info("Nacos 发现服务 {} -> {}", serviceName, url);
                return url;
            } catch (Exception e) {
                last = e;
                log.warn("Nacos 发现第 {} 次失败: {}", i, e.getMessage());
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("Nacos 发现重试 3 次仍失败，回退配置地址: {}", last != null ? last.getMessage() : "未知错误");
        return null;
    }
}
