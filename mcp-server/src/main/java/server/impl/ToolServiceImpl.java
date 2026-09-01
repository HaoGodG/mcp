package server.impl;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import server.data.MarketSimulator;
import server.data.MockDataStore;
import server.entity.ToolSpec;
import server.service.ToolService;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工具按通讯协议分组（具象业务场景，数据来自 MockDataStore / MarketSimulator）：
 *  - stream 端点（SSE / NDJSON）：weather 城市预报、echo 打字机、getQuote 实时行情、getMetrics 实时指标
 *  - sync 端点（同步 JSON）：date / calculator / translate / getUser / getOrder / getStock
 *
 * author Hao
 * date 2026/7/22 16:11
 */
@Service
public class ToolServiceImpl implements ToolService {

    /** SSE 流式端点工具集 */
    private static final List<ToolSpec> STREAM_TOOL_SPECS = List.of(
            new ToolSpec(
                    "weather",
                    "查询城市未来 3 天天气（支持 宁波/上海/北京/深圳，流式逐天推送）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("city", Map.of("type", "string", "description", "城市名称：宁波/上海/北京/深圳")),
                            "required", List.of("city")
                    )
            ),
            new ToolSpec(
                    "echo",
                    "回显输入的文本（流式逐字推送，打字机效果）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("text", Map.of("type", "string", "description", "要回显的文本")),
                            "required", List.of("text")
                    )
            ),
            new ToolSpec(
                    "getQuote",
                    "实时行情流：AAPL/TSLA/600519/000001/300750/00700，每秒 1 次随机游走，10 秒后暂停 5 秒，续推 15 秒，30 秒释放连接",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("symbol", Map.of("type", "string", "description", "股票代码，如 AAPL / 600519")),
                            "required", List.of("symbol")
                    )
            ),
            new ToolSpec(
                    "getMetrics",
                    "实时指标流：cpu/mem/net，每秒 1 次采样，10 秒后暂停 5 秒，续推 15 秒，30 秒释放连接",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("metric", Map.of("type", "string", "description", "指标名：cpu/mem/net")),
                            "required", List.of("metric")
                    )
            )
    );

    /** 同步端点工具集 */
    private static final List<ToolSpec> SYNC_TOOL_SPECS = List.of(
            new ToolSpec(
                    "date",
                    "获取当前日期时间",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("format", Map.of("type", "string", "description", "时间格式，默认 yyyy-MM-dd HH:mm:ss")),
                            "required", List.of()
                    )
            ),
            new ToolSpec(
                    "calculator",
                    "计算四则运算表达式，支持 + - * / 和括号，如 (1+2)*3",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("expression", Map.of("type", "string", "description", "数学表达式")),
                            "required", List.of("expression")
                    )
            ),
            new ToolSpec(
                    "translate",
                    "中英互译（mock 词典：hello/你好/world/世界/good morning/早上好 等）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "text", Map.of("type", "string", "description", "待翻译文本"),
                                    "target", Map.of("type", "string", "description", "目标语言 en/zh，默认 en")
                            ),
                            "required", List.of("text")
                    )
            ),
            new ToolSpec(
                    "getUser",
                    "查询用户信息（用户库：u-1001 张三/u-1002 李四/u-1003 王五）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("userId", Map.of("type", "string", "description", "用户 ID")),
                            "required", List.of("userId")
                    )
            ),
            new ToolSpec(
                    "getOrder",
                    "查询订单信息（订单库：o-1001 ~ o-1005，状态 PAID/SHIPPED/COMPLETED/CANCELLED）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("orderId", Map.of("type", "string", "description", "订单 ID")),
                            "required", List.of("orderId")
                    )
            ),
            new ToolSpec(
                    "getStock",
                    "查询股票实时快照（与 getQuote 同源行情：价格/涨跌幅/最高最低/成交量）",
                    Map.of(
                            "type", "object",
                            "properties", Map.of("code", Map.of("type", "string", "description", "股票代码，如 600519 / AAPL")),
                            "required", List.of("code")
                    )
            )
    );

    @Override
    public List<ToolSpec> listTools(String protocol) {
        return switch (protocol) {
            case PROTOCOL_STREAM -> STREAM_TOOL_SPECS;
            case PROTOCOL_SYNC -> SYNC_TOOL_SPECS;
            default -> throw new IllegalArgumentException("unknown protocol: " + protocol);
        };
    }

    @Override
    public List<ToolSpec> listAllTools() {
        List<ToolSpec> all = new java.util.ArrayList<>(STREAM_TOOL_SPECS);
        all.addAll(SYNC_TOOL_SPECS);
        return all;
    }

    @Override
    public boolean isStreamTool(String toolName) {
        return STREAM_TOOL_SPECS.stream().anyMatch(t -> t.getName().equals(toolName));
    }

    @Override
    public Object call(String toolName, Map<String, Object> args) {

        switch (toolName) {

            case "date": {
                String format = args != null && args.get("format") != null
                        ? String.valueOf(args.get("format"))
                        : "yyyy-MM-dd HH:mm:ss";
                return Map.of(
                        "date", new SimpleDateFormat(format).format(new Date())
                );
            }

            case "calculator": {
                String expression = args != null ? String.valueOf(args.get("expression")) : "";
                double value = eval(expression);
                return Map.of(
                        "expression", expression,
                        "result", value
                );
            }

            case "translate": {
                String text = args != null ? String.valueOf(args.get("text")) : "";

                // mock 词典：中英对照
                Map<String, String> dict = Map.of(
                        "hello", "你好",
                        "你好", "hello",
                        "world", "世界",
                        "世界", "world",
                        "good morning", "早上好",
                        "早上好", "good morning",
                        "thank you", "谢谢",
                        "谢谢", "thank you"
                );
                String translation = dict.getOrDefault(text, "[未收录] " + text);
                return Map.of(
                        "source", text,
                        "translation", translation
                );
            }

            case "getUser": {
                String userId = args != null ? String.valueOf(args.get("userId")) : "";
                Map<String, Object> user = MockDataStore.USERS.get(userId);
                if (user == null) {
                    throw new IllegalArgumentException("用户不存在: " + userId);
                }
                return user;
            }

            case "getOrder": {
                String orderId = args != null ? String.valueOf(args.get("orderId")) : "";
                Map<String, Object> order = MockDataStore.ORDERS.get(orderId);
                if (order == null) {
                    throw new IllegalArgumentException("订单不存在: " + orderId);
                }
                return order;
            }

            case "getStock": {
                String code = args != null ? String.valueOf(args.get("code")) : "";
                return MarketSimulator.snapshot(code);
            }

            default:
                throw new IllegalArgumentException("sync 端点无此工具: " + toolName);
        }
    }

    @Override
    public Flux<Object> callStream(String toolName, Map<String, Object> args) {

        switch (toolName) {

            case "date":
                // 旧 agent 链路兼容
                return Flux.just(Map.<String, Object>of(
                        "date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                )).cast(Object.class);

            case "weather": {
                String city = args != null ? String.valueOf(args.get("city")) : "宁波";
                Map<String, Object> data = MockDataStore.WEATHER.get(city);
                if (data == null) {
                    return Flux.just(Map.<String, Object>of(
                            "error", "未知城市: " + city + "（支持 宁波/上海/北京/深圳）"
                    )).cast(Object.class);
                }
                List<Map<String, Object>> forecast = (List<Map<String, Object>>) data.get("forecast");
                return Flux.fromIterable(forecast)
                        .map(day -> Map.<String, Object>of(
                                "city", city,
                                "day", day.get("day"),
                                "weather", day.get("weather"),
                                "temperature", day.get("temperature"),
                                "humidity", day.get("humidity"),
                                "wind", day.get("wind")
                        ))
                        .concatWith(Flux.just(Map.<String, Object>of(
                                "city", city,
                                "done", true
                        )))
                        .cast(Object.class);
            }

            case "echo": {
                String text = args != null ? String.valueOf(args.get("text")) : "";
                return Flux.interval(Duration.ofMillis(200))
                        .take(Math.max(text.length(), 1))
                        .map(i -> Map.<String, Object>of(
                                "chunk", text.substring(0, i.intValue() + 1)
                        ))
                        .concatWith(Flux.just(Map.<String, Object>of(
                                "done", true
                        )))
                        .cast(Object.class);
            }

            case "getQuote": {
                String symbol = args != null ? String.valueOf(args.get("symbol")) : "AAPL";
                if (!MockDataStore.STOCKS.containsKey(symbol)) {
                    return Flux.just(Map.<String, Object>of(
                            "error", "未知股票: " + symbol + "（支持 AAPL/TSLA/600519/000001/300750/00700）"
                    )).cast(Object.class);
                }
                // 时间线：0-10s 每秒 1 tick → 10-15s 暂停 → 15-30s 每秒 1 tick → 30s 释放连接
                return Flux.concat(
                        Flux.interval(Duration.ofSeconds(1))
                                .take(10)
                                .map(i -> MarketSimulator.nextTick(symbol)),
                        Flux.interval(Duration.ofSeconds(1))
                                .take(15)
                                .delaySubscription(Duration.ofSeconds(5))
                                .map(i -> MarketSimulator.nextTick(symbol))
                ).cast(Object.class);
            }

            case "getMetrics": {
                String metric = args != null ? String.valueOf(args.get("metric")) : "cpu";
                return Flux.concat(
                        Flux.interval(Duration.ofSeconds(1))
                                .take(10)
                                .map(i -> metricTick(metric, i.intValue())),
                        Flux.interval(Duration.ofSeconds(1))
                                .take(15)
                                .delaySubscription(Duration.ofSeconds(5))
                                .map(i -> metricTick(metric, i.intValue()))
                ).cast(Object.class);
            }

            default:
                return Flux.just(Map.<String, Object>of(
                        "error", "stream 端点无此工具: " + toolName
                )).cast(Object.class);
        }
    }

    /** 指标采样：cpu 波动 / mem 递增 / net 随机 */
    private Map<String, Object> metricTick(String metric, int step) {
        return switch (metric) {
            case "cpu" -> Map.<String, Object>of(
                    "metric", "cpu",
                    "value", 20 + ThreadLocalRandom.current().nextInt(70),
                    "unit", "%"
            );
            case "mem" -> Map.<String, Object>of(
                    "metric", "mem",
                    "value", 45 + step,
                    "unit", "%"
            );
            case "net" -> Map.<String, Object>of(
                    "metric", "net",
                    "value", ThreadLocalRandom.current().nextInt(50, 900),
                    "unit", "MB/s"
            );
            default -> Map.<String, Object>of(
                    "metric", metric,
                    "error", "未知指标（支持 cpu/mem/net）"
            );
        };
    }

    /**
     * 四则运算表达式求值（递归下降，支持 + - * / 和括号）
     */
    private static double eval(String expression) {
        String s = expression.replaceAll("\\s", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("expression 为空");
        }
        Parser parser = new Parser(s);
        double value = parser.parse();
        if (parser.pos != s.length()) {
            throw new IllegalArgumentException("非法表达式: " + expression);
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
        }

        private double parse() {
            return expr();
        }

        private double expr() {
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
                double v = expr();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                pos++;
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("非法字符: " + s.charAt(pos));
            }
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
