package server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import server.entity.McpRequest;
import server.entity.McpResponse;
import server.service.ToolService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MCP 标准端点（Streamable HTTP 最佳实践：单端点 + 内容协商）
 *
 *  - POST /mcp            唯一标准端点（推荐客户端接入）
 *      initialize / tools/list            → application/json
 *      tools/call 同步工具                → application/json
 *      tools/call 流式工具（SSE）         → text/event-stream 逐帧推送
 *      （客户端 Accept 声明 application/json, text/event-stream，服务端按工具决定响应类型）
 *  - GET  /mcp            SSE 通道（服务端主动推送，规范可选）
 *  - POST /mcp/sync       兼容端点：仅同步工具（旧客户端/网关路由保留）
 *  - POST /mcp/ndjson     扩展端点：NDJSON 裸流（非 MCP 标准，数据管道类消费者用）
 *
 * 纯标准 MCP：不承载 SDK 私有能力（个性化/加密已独立到 sdk-server 8084）
 *
 * author Hao
 * date 2026/7/21 16:40
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    /** 服务端支持的 MCP 协议版本 */
    private static final String PROTOCOL_VERSION = "2025-06-18";

    /** NDJSON 媒体类型（chunked 逐行 JSON 流，扩展端点） */
    private static final String NDJSON_VALUE = "application/x-ndjson";

    private final ToolService toolService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------
    // 标准端点：POST /mcp（单端点 + 内容协商，手动写响应保证编码器选择可控）
    // ---------------------------------------------------------------

    /** MCP 会话头（initialize 下发，后续请求携带以关联调用方） */
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    /** 会话空闲超时（规范：服务端可随时终止会话） */
    @Value("${mcp.session-idle-timeout:30m}")
    private Duration sessionIdleTimeout;

    /** 会话表：sessionId -> 会话（initialize 时建立，空闲超时回收，DELETE 终止） */
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /** 会话条目：调用方 + 最后访问时间 */
    private static final class SessionEntry {
        final String clientName;
        volatile long lastAccess;

        SessionEntry(String clientName) {
            this.clientName = clientName;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    /** 定时清理过期会话（空闲超时） */
    @PostConstruct
    public void startSessionCleaner() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleaner");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::purgeExpiredSessions, 5, 5, TimeUnit.MINUTES);
        log.info("会话清理定时任务已启动（空闲超时 {}）", sessionIdleTimeout);
    }

    private void purgeExpiredSessions() {
        long timeout = sessionIdleTimeout.toMillis();
        sessions.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().lastAccess > timeout);
        log.info("会话清理完成，当前活跃会话: {}", sessions.size());
    }

    @PostMapping
    public Mono<Void> handle(
            ServerWebExchange exchange,
            @RequestBody String rawBody) {

        McpRequest request = parse(rawBody);
        String method = request.getMethod();
        String id = request.getId();

        // 会话失效检查（规范：对带失效会话的请求返回 404，客户端重开）
        String sessionId = exchange.getRequest().getHeaders().getFirst(SESSION_HEADER);
        if (sessionId != null && !sessionId.isBlank() && !"initialize".equals(method)) {
            SessionEntry entry = sessions.get(sessionId);
            if (entry == null) {
                log.info("[会话失效] session={} method={} -> 404", sessionId, method);
                return writeStatus(exchange, HttpStatus.NOT_FOUND, "{\"error\":\"session expired\",\"code\":404}");
            }
            entry.lastAccess = System.currentTimeMillis();
        }

        // 识别调用方：优先会话（Mcp-Session-Id 关联 initialize 时的 clientInfo.name），其次 clientInfo.name
        String clientName = resolveClientName(exchange, request);
        MDC.put("clientName", clientName);
        log.info("[MCP 请求] client={} method={} id={} params={}", clientName, method, id, request.getParams());
        MDC.remove("clientName");

        switch (method) {

            case "initialize": {
                // 建立会话：记录调用方（clientInfo.name），下发 Mcp-Session-Id
                String newSessionId = UUID.randomUUID().toString();
                sessions.put(newSessionId, new SessionEntry(clientName));
                McpResponse resp = ok(id, Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of("name", "mcp-server", "version", "1.0.0")
                ));
                return writeJson(exchange, resp, Map.of(SESSION_HEADER, newSessionId));
            }

            case "tools/list":
                return writeJson(exchange, ok(id, Map.of(
                        "tools", toolService.listAllTools()
                )));

            case "tools/call": {
                Map<String, Object> params = (Map<String, Object>) request.getParams();
                String name = (String) params.get("name");
                Map<String, Object> args = (Map<String, Object>) params.get("arguments");

                // 流式工具 → SSE 逐帧推送；同步工具 → JSON 单对象
                if (toolService.isStreamTool(name)) {
                    return writeSse(exchange, toolService.callStream(name, args)
                            .map(chunk -> ok(id, content(chunk)))
                            .onErrorResume(e -> Flux.just(err(id, -32603, "Internal error: " + e.getMessage()))));
                }
                try {
                    return writeJson(exchange, ok(id, content(toolService.call(name, args))));
                } catch (Exception e) {
                    return writeJson(exchange, err(id, -32603, "Internal error: " + e.getMessage()));
                }
            }

            default:
                return writeJson(exchange, err(id, -32601, "Method not found: " + method));
        }
    }

    /** 写 JSON 响应（同步） */
    private Mono<Void> writeJson(ServerWebExchange exchange, McpResponse resp) {
        return writeJson(exchange, resp, Map.of());
    }

    /** 写 JSON 响应（同步，可附加响应头，如 Mcp-Session-Id） */
    private Mono<Void> writeJson(ServerWebExchange exchange, McpResponse resp, Map<String, String> extraHeaders) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(resp);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            extraHeaders.forEach((k, v) -> exchange.getResponse().getHeaders().set(k, v));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }

    /** 写 SSE 流响应（流式工具逐帧推送） */
    private Mono<Void> writeSse(ServerWebExchange exchange, Flux<McpResponse> responses) {
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        return exchange.getResponse().writeWith(responses.map(resp -> {
            String frame = "event:message\ndata:" + toJson(resp) + "\n\n";
            return exchange.getResponse().bufferFactory().wrap(frame.getBytes());
        }));
    }

    // ---------------------------------------------------------------
    // SSE 通道：GET /mcp（服务端主动推送，规范可选；当前发心跳）
    // ---------------------------------------------------------------

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> channel() {
        // 预留：服务端主动推送通道（当前仅心跳，证明 GET 可开流）
        return Flux.interval(Duration.ofSeconds(5))
                .map(i -> ServerSentEvent.<String>builder("{\"type\":\"heartbeat\",\"ts\":" + System.currentTimeMillis() + "}").build());
    }

    // ---------------------------------------------------------------
    // 兼容端点：POST /mcp/sync（仅同步工具，旧客户端/网关路由保留）
    // ---------------------------------------------------------------

    @PostMapping(value = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public McpResponse sync(@RequestBody String rawBody) {

        McpRequest request = parse(rawBody);
        try {
            return dispatchSync(request);
        } catch (Exception e) {
            return err(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private McpResponse dispatchSync(McpRequest request) {
        String method = request.getMethod();
        String id = request.getId();

        switch (method) {
            case "initialize":
                return ok(id, Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of("name", "mcp-server", "version", "1.0.0")
                ));
            case "tools/list":
                return ok(id, Map.of(
                        "tools", toolService.listTools(ToolService.PROTOCOL_SYNC)
                ));
            case "tools/call": {
                Map<String, Object> params = (Map<String, Object>) request.getParams();
                String name = (String) params.get("name");
                Map<String, Object> args = (Map<String, Object>) params.get("arguments");
                return ok(id, content(toolService.call(name, args)));
            }
            default:
                return err(id, -32601, "Method not found: " + method);
        }
    }

    // ---------------------------------------------------------------
    // 扩展端点：POST /mcp/ndjson（NDJSON 裸流，非 MCP 标准）
    // ---------------------------------------------------------------

    @PostMapping(value = "/ndjson", produces = NDJSON_VALUE)
    public Flux<McpResponse> ndjson(@RequestBody McpRequest request) {

        String method = request.getMethod();
        String id = request.getId();

        if ("tools/call".equals(method)) {
            try {
                Map<String, Object> params = (Map<String, Object>) request.getParams();
                String name = (String) params.get("name");
                Map<String, Object> args = (Map<String, Object>) params.get("arguments");

                return toolService.callStream(name, args)
                        .map(chunk -> ok(id, content(chunk)))
                        .onErrorResume(e -> Flux.just(err(id, -32603, "Internal error: " + e.getMessage())));
            } catch (Exception e) {
                return Flux.just(err(id, -32603, "Internal error: " + e.getMessage()));
            }
        }
        return Flux.just(dispatchSync(request));
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    /** 解析报文（纯标准 MCP，明文 JSON-RPC） */
    private McpRequest parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, McpRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("报文解析失败: " + e.getMessage());
        }
    }

    /** 解析调用方名称：优先会话（Mcp-Session-Id → initialize 时记录的 clientInfo.name），
     *  其次当前请求的 clientInfo.name（initialize），缺省 unknown */
    private String resolveClientName(ServerWebExchange exchange, McpRequest request) {
        String sessionId = exchange.getRequest().getHeaders().getFirst(SESSION_HEADER);
        if (sessionId != null) {
            SessionEntry entry = sessions.get(sessionId);
            if (entry != null) {
                return entry.clientName;
            }
        }
        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            if (params != null && params.get("clientInfo") instanceof Map<?, ?> clientInfo) {
                Object name = clientInfo.get("name");
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            }
        } catch (Exception ignored) {
            // 解析失败按 unknown 处理
        }
        return "unknown";
    }

    // ---------------------------------------------------------------
    // 会话终止：DELETE /mcp + Mcp-Session-Id（规范：客户端不再需要时显式终止）
    // ---------------------------------------------------------------

    @DeleteMapping
    public Mono<Void> terminate(ServerWebExchange exchange) {
        String sessionId = exchange.getRequest().getHeaders().getFirst(SESSION_HEADER);
        if (sessionId != null && sessions.remove(sessionId) != null) {
            log.info("[会话终止] session={}", sessionId);
            return writeStatus(exchange, HttpStatus.OK, "{\"ok\":true}");
        }
        return writeStatus(exchange, HttpStatus.NOT_FOUND, "{\"error\":\"session not found\",\"code\":404}");
    }

    /** 写指定状态码的 JSON 响应（如 404 会话失效 / DELETE 终止结果） */
    private Mono<Void> writeStatus(ServerWebExchange exchange, HttpStatus status, String body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }

    /** 工具结果包装（MCP content 数组） */
    private Map<String, Object> content(Object result) {
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", toJson(result)
                )),
                "isError", false
        );
    }

    /** 包装为 SSE 事件 */
    private ServerSentEvent<McpResponse> sse(McpResponse resp) {
        return ServerSentEvent.<McpResponse>builder(resp)
                .event("message")
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private McpResponse ok(String id, Object result) {
        McpResponse resp = new McpResponse();
        resp.setId(id);
        resp.setResult(result);
        return resp;
    }

    private McpResponse err(String id, int code, String message) {
        McpResponse resp = new McpResponse();
        resp.setId(id);
        resp.setError(Map.of("code", code, "message", message));
        return resp;
    }
}
