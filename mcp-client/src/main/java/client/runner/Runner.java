package client.runner;

import client.request.McpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后自动执行标准 MCP 流程：
 * token → initialize → tools/list → tools/call
 *
 * author Hao
 * date 2026/7/21 16:45
 */
@Component
@RequiredArgsConstructor
public class Runner implements CommandLineRunner {

    private final McpClient client;

    @Override
    public void run(String... args) {
        client.run();
    }
}
