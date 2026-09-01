package sdkserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sdkserver.crypto.RsaCryptoUtil;
import sdkserver.data.SdkDataStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SDK 服务端：标准 MCP 端点（直接提供，支持 RSA 全报文加密）
 *
 *  - POST /mcp  initialize / tools/list / tools/call（同步工具，直接实现）
 *  - 明文（标准客户端）与加密（X-Encrypted: rsa，SDK 客户）两种报文均支持
 *
 * 内置同步工具：date / calculator / translate / getUser / getStock
 *
 * author Hao
 * date 2026/8/19
 */
@RestController
@RequestMapping("/mcp")
public class McpEndpointController {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String ENCRYPTED_HEADER = "X-Encrypted";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${crypto.rsa.private-key}")
    private String rsaPrivateKey;

    @PostMapping
    public Map<String, Object> handle(
            @RequestBody String rawBody,
            @RequestHeader(value = ENCRYPTED_HEADER, required = false) String encrypted) {

        Map<String, Object> request = parse(rawBody, encrypted);
        String method = (String) request.get("method");
        String id = (String) request.get("id");

        try {
            switch (method) {
                case "initialize":
                    return ok(id, Map.of(
                            "protocolVersion", PROTOCOL_VERSION,
                            "capabilities", Map.of("tools", Map.of("listChanged", false)),
                            "serverInfo", Map.of("name", "mcp-sdk-server", "version", "1.0.0")
                    ));

                case "tools/list":
                    return ok(id, Map.of("tools", tools()));

                case "tools/call": {
                    Map<String, Object> params = (Map<String, Object>) request.get("params");
                    String name = (String) params.get("name");
                    Map<String, Object> args = (Map<String, Object>) params.get("arguments");
                    return ok(id, content(call(name, args)));
                }

                default:
                    return err(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            return err(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // 内置工具
    // ---------------------------------------------------------------

    private List<Map<String, Object>> tools() {
        return List.of(
                tool("date", "获取当前日期时间", Map.of("format", Map.of("type", "string", "description", "时间格式，默认 yyyy-MM-dd HH:mm:ss")), List.of()),
                tool("calculator", "四则运算，支持 + - * / 和括号", Map.of("expression", Map.of("type", "string", "description", "数学表达式")), List.of("expression")),
                tool("translate", "中英互译（mock 词典）", Map.of("text", Map.of("type", "string", "description", "待翻译文本")), List.of("text")),
                tool("getUser", "查询用户信息", Map.of("userId", Map.of("type", "string", "description", "用户 ID")), List.of("userId")),
                tool("getStock", "查询股票快照", Map.of("code", Map.of("type", "string", "description", "股票代码")), List.of("code"))
        );
    }

    private Map<String, Object> tool(String name, String desc, Map<String, Object> props, List<String> required) {
        return Map.of(
                "name", name,
                "description", desc,
                "inputSchema", Map.of("type", "object", "properties", props, "required", required)
        );
    }

    private Object call(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "date" -> Map.of("date",
                    new SimpleDateFormat(args != null && args.get("format") != null
                            ? String.valueOf(args.get("format")) : "yyyy-MM-dd HH:mm:ss").format(new Date()));

            case "calculator" -> {
                String expression = args != null ? String.valueOf(args.get("expression")) : "";
                yield Map.of("expression", expression, "result", eval(expression));
            }

            case "translate" -> {
                String text = args != null ? String.valueOf(args.get("text")) : "";
                Map<String, String> dict = Map.of(
                        "hello", "你好", "你好", "hello",
                        "world", "世界", "世界", "world",
                        "thank you", "谢谢", "谢谢", "thank you");
                yield Map.of("source", text, "translation", dict.getOrDefault(text, "[未收录] " + text));
            }

            case "getUser" -> {
                String userId = args != null ? String.valueOf(args.get("userId")) : "";
                Map<String, Object> user = SdkDataStore.USERS.get(userId);
                if (user == null) {
                    throw new IllegalArgumentException("用户不存在: " + userId);
                }
                yield user;
            }

            case "getStock" -> {
                String code = args != null ? String.valueOf(args.get("code")) : "";
                Map<String, Object> stock = SdkDataStore.STOCKS.get(code);
                if (stock == null) {
                    throw new IllegalArgumentException("未知股票: " + code);
                }
                double base = (double) stock.get("basePrice");
                double price = base * (1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.02);
                yield Map.of(
                        "code", code,
                        "name", stock.get("name"),
                        "industry", stock.get("industry"),
                        "price", Math.round(price * 100.0) / 100.0,
                        "changePercent", Math.round((price - base) / base * 1000.0) / 10.0
                );
            }

            default -> throw new IllegalArgumentException("unknown tool: " + toolName);
        };
    }

    /** 简易四则运算（复用递归下降） */
    private double eval(String expression) {
        String s = expression.replaceAll("\\s", "");
        return new Parser(s).parse();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
        }

        private double parse() {
            double v = term();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                double r = term();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                double r = factor();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }

        private double factor() {
            if (s.charAt(pos) == '(') {
                pos++;
                double v = parse();
                pos++;
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    // ---------------------------------------------------------------
    // 响应包装
    // ---------------------------------------------------------------

    private Map<String, Object> parse(String rawBody, String encrypted) {
        try {
            String plain = (encrypted != null && !encrypted.isBlank())
                    ? RsaCryptoUtil.decrypt(rsaPrivateKey, rawBody)
                    : rawBody;
            return objectMapper.readValue(plain, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("报文解析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> content(Object result) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", toJson(result))),
                "isError", false
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private Map<String, Object> ok(String id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    private Map<String, Object> err(String id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }
}
