package client.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置：base-url 优先从 Nacos 发现 mcp-server 地址
 *
 * - nacos.enabled=true 时，从 Nacos 取一个健康实例覆盖 base-url（服务发现寻址）
 * - 否则使用 application.yml 的 mcp.base-url（直连 / 网关场景）
 *
 * author Hao
 * date 2026/7/21 16:41
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${mcp.base-url}")
    private String baseUrl;

    @Bean
    public WebClient webClient(NacosDiscoveryService discoveryService) {
        String target = baseUrl;

        String discovered = discoveryService.discover();
        if (discovered != null) {
            target = discovered;
        } else {
            log.info("使用配置地址 {}", baseUrl);
        }

        return WebClient.builder()
                .baseUrl(target)
                .build();
    }
}
