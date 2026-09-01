package client.request;

import client.entity.McpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

/**
 * 标准 MCP 客户端（JSON-RPC 2.0）
 *
 * 交互流程：token → initialize → tools/list → tools/call
 * 认证：凭证换 Token（POST /api/auth/tokenApply），后续请求带 Authorization 头
 *
 * author Hao
 * date 2026/7/21 16:42
 */
@Service
@RequiredArgsConstructor
public class McpClient {

    /** 与 server 约定的协议版本 */
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.client-id}")
    private String clientId;

    @Value("${mcp.client-secret}")
    private String clientSecret;

    @Value("${mcp.path}")
    private String path;

    /** 通讯协议模式：sse（流式）/ sync（同步 JSON），对应服务端不同端点 */
    @Value("${mcp.mode:sse}")
    private String mode;

    /** 直连 server 时可配置固定 token 跳过 tokenApply（走网关时留空） */
    @Value("${mcp.token:}")
    private String token;

    /** 调用方名称（initialize clientInfo.name，服务端按此建立会话拆分日志） */
    @Value("${mcp.client-name:mcp-client}")
    private String clientName;

    /** 身份模式：token（gateway tokenApply 换 JWT）/ channel（内网 X-Channel-Id） */
    @Value("${mcp.auth-mode:token}")
    private String authMode;

    /** channel 模式身份（X-Channel-Id 头） */
    @Value("${mcp.channel-id:}")
    private String channelId;

    /** channel 模式凭证（who-am-i 头，网关要求固定值 hao） */
    @Value("${mcp.who-am-i:hao}")
    private String whoAmI;

    /** initialize 时服务端下发的会话 ID（后续请求携带，服务端按此识别调用方） */
    private String sessionId;

    /**
     * 完整标准流程：token → initialize → tools/list → tools/call
     */
    public void run() {
        try {
            // 1️⃣ token
            String token = auth();
            System.out.println("[1/4] token = " + token);

            // 2️⃣ initialize 握手
            JsonNode init = initialize();
            System.out.println("[2/4] initialize => " + pretty(init));

            // 3️⃣ tools/list 工具列表
            JsonNode tools = listTools();
            System.out.println("[3/4] tools/list => " + pretty(tools));

            // 4️⃣ tools/call 调用工具（按协议模式选工具：sync 端点没有流式工具）
            JsonNode result = "sync".equals(mode)
                    ? callTool("calculator", Map.of("expression", "(1+2)*3"))
                    : callTool("weather", Map.of("city", "宁波"));
            System.out.println("[4/4] tools/call => " + pretty(result));

            // 5️⃣ GET 类工具演示（按协议模式）
            if ("sync".equals(mode)) {
                JsonNode getResult = callTool("getUser", Map.of("userId", "u-1001"));
                System.out.println("[5/5] tools/call getUser => " + pretty(getResult));
            } else {
                // sse/ndjson：实时行情流（每秒 1 块，10s 停 5s，续发，30s 释放连接）
                // 网关未授权 getQuote 时降级为 echo，保证链路可跑通
                try {
                    JsonNode quote = callTool("getQuote", Map.of("symbol", "AAPL"));
                    System.out.println("[5/5] tools/call getQuote => " + pretty(quote));
                } catch (Exception e) {
                    System.out.println("[5/5] getQuote 网关未授权 (" + e.getMessage() + ")，降级调用 echo");
                    JsonNode echo = callTool("echo", Map.of("text", "hello mcp"));
                    System.out.println("[5/5] tools/call echo => " + pretty(echo));
                }
            }

            // 6️⃣ 全工具遍历调用（10 个工具逐一测试；流式工具取前 3 帧）
            testAllTools();

        } catch (Exception e) {
            System.err.println("MCP 调用失败: " + e.getMessage());
        } finally {
            // 规范：客户端不再需要会话时显式终止（DELETE + Mcp-Session-Id）
            close();
        }
    }

    /** 显式终止会话（DELETE /mcp + Mcp-Session-Id） */
    public void close() {
        if (sessionId != null) {
            try {
                webClient.delete()
                        .uri(path)
                        .headers(this::applyAuth)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();
                System.out.println("[会话] 已发送 DELETE 终止会话 " + sessionId.substring(0, 8) + "...");
            } catch (Exception e) {
                System.out.println("[会话] 终止失败: " + e.getMessage());
            } finally {
                sessionId = null;
            }
        }
    }

    /**
     * 全工具遍历调用：从 tools/list 结果逐个调用
     *  - 同步工具：等待完整结果
     *  - 流式工具：取前 3 帧即断开（避免 30s 长流阻塞）
     */
    public void testAllTools() {
        System.out.println("\n===== [6/6] 全工具遍历调用 =====");
        JsonNode toolsResp = listTools();
        JsonNode tools = toolsResp.get("result").get("tools");

        for (JsonNode tool : tools) {
            String name = tool.get("name").asText();
            Map<String, Object> args = buildArgs(name);
            try {
                if (STREAM_TOOLS.contains(name)) {
                    JsonNode preview = streamPreview(name, args, 3);
                    System.out.println("[全量] " + name + "（流式前3帧）=> " + preview);
                } else {
                    JsonNode resp = callTool(name, args);
                    JsonNode text = resp.get("result").get("content").get(0).get("text");
                    System.out.println("[全量] " + name + " => " + text);
                }
            } catch (Exception e) {
                System.out.println("[全量] " + name + " 失败: " + e.getMessage());
            }
        }
    }

    /** 流式工具名单 */
    private static final java.util.Set<String> STREAM_TOOLS = java.util.Set.of("weather", "echo", "getQuote", "getMetrics");

    /** 按工具名构造演示参数 */
    private Map<String, Object> buildArgs(String name) {
        return switch (name) {
            case "weather" -> Map.of("city", "宁波");
            case "echo" -> Map.of("text", "hello mcp");
            case "getQuote" -> Map.of("symbol", "AAPL");
            case "getMetrics" -> Map.of("metric", "cpu");
            case "calculator" -> Map.of("expression", "(1+2)*3");
            case "translate" -> Map.of("text", "hello");
            case "getUser" -> Map.of("userId", "u-1002");
            case "getOrder" -> Map.of("orderId", "o-1002");
            case "getStock" -> Map.of("code", "600519");
            default -> Map.of();
        };
    }

    /** 流式工具预览：只取前 N 帧即断开 */
    private JsonNode streamPreview(String name, Map<String, Object> args, int frames) {
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(this::applyAuth)
                .bodyValue(new McpRequest(
                        "2.0",
                        UUID.randomUUID().toString(),
                        "tools/call",
                        Map.of("name", name, "arguments", args)
                ))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(ev -> ev.data() != null)
                .take(frames)
                .map(ev -> parseJson(ev.data()))
                .blockLast();
    }

    /**
     * 1. 获取身份凭证
     *  - channel 模式：返回 X-Channel-Id（不发 tokenApply），请求带 who-am-i 头
     *  - token 模式：凭证换 Token，POST /api/auth/tokenApply
     */
    public String auth() {
        if ("channel".equals(authMode)) {
            if (channelId == null || channelId.isBlank()) {
                throw new IllegalStateException("channel 模式未配置 mcp.channel-id");
            }
            return channelId;
        }

        JsonNode resp;
        try {
            resp = webClient.post()
                    .uri("/api/auth/tokenApply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "clientId", clientId,
                            "clientSecret", clientSecret
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            // 登录防爆破：clientId+IP 超限返回 429
            if (e.getStatusCode() == HttpStatusCode.valueOf(429)) {
                throw new IllegalStateException("登录请求超限（429），请稍后重试");
            }
            throw new IllegalStateException("tokenApply 失败: HTTP " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }

        if (resp == null) {
            throw new IllegalStateException("tokenApply 响应为空");
        }
        JsonNode tokenNode = resp.path("token");
        if (tokenNode.isMissingNode() || tokenNode.isNull()) {
            throw new IllegalStateException("tokenApply 未返回 token: " + resp);
        }
        this.token = tokenNode.asText();
        return this.token;
    }

    /** 2. initialize 握手（记住服务端下发的 Mcp-Session-Id，后续请求携带） */
    public JsonNode initialize() {
        ResponseEntity<JsonNode> resp = webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(this::applyAuth)
                .bodyValue(new McpRequest(
                        "2.0",
                        UUID.randomUUID().toString(),
                        "initialize",
                        Map.of(
                                "protocolVersion", PROTOCOL_VERSION,
                                "capabilities", Map.of(),
                                "clientInfo", Map.of(
                                        "name", clientName,
                                        "version", "1.0.0"
                                )
                        )
                ))
                .retrieve()
                .toEntity(JsonNode.class)
                .block();

        if (resp == null || resp.getBody() == null) {
            throw new IllegalStateException("initialize 响应为空");
        }
        this.sessionId = resp.getHeaders().getFirst("Mcp-Session-Id");
        return resp.getBody();
    }

    /** 3. tools/list 工具列表 */
    public JsonNode listTools() {
        return post(new McpRequest(
                "2.0",
                UUID.randomUUID().toString(),
                "tools/list",
                Map.of()
        ));
    }

    /** 4. tools/call 同步调用工具 */
    public JsonNode callTool(String name, Map<String, Object> args) {
        return post(new McpRequest(
                "2.0",
                UUID.randomUUID().toString(),
                "tools/call",
                Map.of(
                        "name", name,
                        "arguments", args
                )
        ));
    }

    /** 统一身份头：token 模式带 Bearer；channel 模式带 X-Channel-Id + who-am-i；有会话附带 Mcp-Session-Id */
    private void applyAuth(org.springframework.http.HttpHeaders headers) {
        if ("channel".equals(authMode)) {
            headers.set("X-Channel-Id", channelId);
            headers.set("who-am-i", whoAmI);
        } else {
            headers.set("Authorization", "Bearer " + token);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            headers.set("Mcp-Session-Id", sessionId);
        }
    }

    /** 发送 JSON-RPC 请求；按 mcp.mode 消费 SSE 流 / NDJSON 流 / 同步 JSON
     *  会话失效（404）时自动重新 initialize 并重试一次 */
    private JsonNode post(McpRequest request) {
        try {
            return doPost(request);
        } catch (WebClientResponseException e) {
            // 规范：会话失效服务端返回 404，客户端应重开会话
            if (e.getStatusCode() == HttpStatusCode.valueOf(404) && sessionId != null) {
                System.out.println("[会话] 服务端会话失效(404)，重新 initialize...");
                sessionId = null;
                initialize();
                return doPost(request);
            }
            throw e;
        }
    }

    private JsonNode doPost(McpRequest request) {
        JsonNode resp;
        if ("sync".equals(mode)) {
            resp = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuth)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } else if ("ndjson".equals(mode)) {
            resp = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .headers(this::applyAuth)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(JsonNode.class)
                    .blockLast();
        } else {
            // SSE 模式（单端点内容协商）：Accept 双声明，网关/服务端可能返回同步 JSON 或 SSE 流，
            // 按 Content-Type 自动协商解析
            resp = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .headers(this::applyAuth)
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        String contentType = response.headers().contentType()
                                .map(MediaType::toString)
                                .orElse("");
                        if (contentType.contains("text/event-stream")) {
                            // SSE 流：过滤无 data 事件（keepalive 等），取最后一个事件
                            return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                                    .filter(ev -> ev.data() != null)
                                    .map(ev -> parseJson(ev.data()))
                                    .last();
                        }
                        return response.bodyToMono(JsonNode.class);
                    })
                    .block();
        }

        if (resp == null) {
            throw new IllegalStateException("MCP 响应为空");
        }
        // JSON-RPC 错误
        if (resp.has("error")) {
            throw new IllegalStateException("MCP error: " + resp.get("error"));
        }
        return resp;
    }

    /** 解析 SSE data（JSON 字符串）为 JsonNode */
    private JsonNode parseJson(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            throw new IllegalStateException("SSE data 解析失败: " + data + " -> " + e.getMessage());
        }
    }

    private String pretty(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }
}
