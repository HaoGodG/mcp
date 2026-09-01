package client.config;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos 客户端配置（用于从注册中心发现 mcp-server 地址）
 *
 * author Hao
 * date 2026/8/18
 */
@Configuration
public class NacosConfig {

    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Bean(destroyMethod = "")
    public NamingService namingService() throws Exception {
        return NamingFactory.createNamingService(serverAddr);
    }
}
