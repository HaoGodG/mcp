package server.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置业务数据（内存 mock 数据源）
 *
 * 具象化场景数据：
 *  - 用户库：3 个用户（不同角色/状态）
 *  - 订单库：5 笔订单（不同用户/金额/状态/时间）
 *  - 股票池：6 只股票（代码/名称/基础价/行业）
 *  - 城市天气：4 个城市 3 天预报
 *  - 用户画像：3 组兴趣标签（驱动个性化推荐）
 *
 * author Hao
 * date 2026/8/19
 */
public final class MockDataStore {

    /** 用户库（userId -> 用户信息） */
    public static final Map<String, Map<String, Object>> USERS = new ConcurrentHashMap<>();

    /** 订单库（orderId -> 订单信息） */
    public static final Map<String, Map<String, Object>> ORDERS = new ConcurrentHashMap<>();

    /** 股票池（code -> 基础行情） */
    public static final Map<String, Map<String, Object>> STOCKS = new ConcurrentHashMap<>();

    /** 城市天气（city -> 3 天预报） */
    public static final Map<String, Map<String, Object>> WEATHER = new ConcurrentHashMap<>();

    static {
        // ---------- 用户 ----------
        USERS.put("u-1001", Map.of("userId", "u-1001", "name", "张三", "role", "admin", "status", "ACTIVE", "email", "zhangsan@mcp.dev"));
        USERS.put("u-1002", Map.of("userId", "u-1002", "name", "李四", "role", "user", "status", "ACTIVE", "email", "lisi@mcp.dev"));
        USERS.put("u-1003", Map.of("userId", "u-1003", "name", "王五", "role", "viewer", "status", "DISABLED", "email", "wangwu@mcp.dev"));

        // ---------- 订单 ----------
        ORDERS.put("o-1001", Map.of("orderId", "o-1001", "userId", "u-1001", "amount", 199.00, "status", "PAID", "createdAt", "2026-08-01 10:30:00"));
        ORDERS.put("o-1002", Map.of("orderId", "o-1002", "userId", "u-1002", "amount", 1299.00, "status", "SHIPPED", "createdAt", "2026-08-05 14:20:00"));
        ORDERS.put("o-1003", Map.of("orderId", "o-1003", "userId", "u-1001", "amount", 59.90, "status", "COMPLETED", "createdAt", "2026-07-28 09:15:00"));
        ORDERS.put("o-1004", Map.of("orderId", "o-1004", "userId", "u-1002", "amount", 3999.00, "status", "PAID", "createdAt", "2026-08-10 16:45:00"));
        ORDERS.put("o-1005", Map.of("orderId", "o-1005", "userId", "u-1003", "amount", 88.00, "status", "CANCELLED", "createdAt", "2026-07-20 11:00:00"));

        // ---------- 股票池（基础价 / 行业 / 昨收） ----------
        STOCKS.put("AAPL", Map.of("code", "AAPL", "name", "苹果", "basePrice", 180.00, "industry", "科技"));
        STOCKS.put("TSLA", Map.of("code", "TSLA", "name", "特斯拉", "basePrice", 250.00, "industry", "汽车"));
        STOCKS.put("600519", Map.of("code", "600519", "name", "贵州茅台", "basePrice", 1688.00, "industry", "白酒"));
        STOCKS.put("000001", Map.of("code", "000001", "name", "平安银行", "basePrice", 11.50, "industry", "银行"));
        STOCKS.put("300750", Map.of("code", "300750", "name", "宁德时代", "basePrice", 210.00, "industry", "新能源"));
        STOCKS.put("00700", Map.of("code", "00700", "name", "腾讯控股", "basePrice", 380.00, "industry", "互联网"));

        // ---------- 城市天气（3 天预报） ----------
        WEATHER.put("宁波", Map.of("city", "宁波", "forecast", List.of(
                Map.of("day", "今天", "weather", "晴", "temperature", 26, "humidity", 60, "wind", "东风3级"),
                Map.of("day", "明天", "weather", "多云", "temperature", 24, "humidity", 65, "wind", "东南风2级"),
                Map.of("day", "后天", "weather", "小雨", "temperature", 22, "humidity", 80, "wind", "北风4级")
        )));
        WEATHER.put("上海", Map.of("city", "上海", "forecast", List.of(
                Map.of("day", "今天", "weather", "阴", "temperature", 28, "humidity", 75, "wind", "南风3级"),
                Map.of("day", "明天", "weather", "雷阵雨", "temperature", 27, "humidity", 85, "wind", "西南风4级"),
                Map.of("day", "后天", "weather", "多云", "temperature", 29, "humidity", 70, "wind", "东风2级")
        )));
        WEATHER.put("北京", Map.of("city", "北京", "forecast", List.of(
                Map.of("day", "今天", "weather", "晴", "temperature", 31, "humidity", 35, "wind", "西北风3级"),
                Map.of("day", "明天", "weather", "晴", "temperature", 32, "humidity", 30, "wind", "北风2级"),
                Map.of("day", "后天", "weather", "多云", "temperature", 30, "humidity", 40, "wind", "东风3级")
        )));
        WEATHER.put("深圳", Map.of("city", "深圳", "forecast", List.of(
                Map.of("day", "今天", "weather", "阵雨", "temperature", 30, "humidity", 82, "wind", "东南风3级"),
                Map.of("day", "明天", "weather", "大雨", "temperature", 28, "humidity", 90, "wind", "东风5级"),
                Map.of("day", "后天", "weather", "多云", "temperature", 29, "humidity", 72, "wind", "南风2级")
        )));

    }

    private MockDataStore() {
    }
}
