package sdkserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SDK 服务端（独立进程）
 * 直接提供 SDK 能力：/personalize（个性化）+ /mcp（加密 MCP），不依赖 mcp-server
 *
 * author Hao
 * date 2026/8/19
 */
@SpringBootApplication
public class SdkServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SdkServerApplication.class, args);
    }
}
