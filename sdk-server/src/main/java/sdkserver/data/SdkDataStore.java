package sdkserver.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDK 服务端内置数据（独立提供，不依赖 mcp-server）
 *
 * author Hao
 * date 2026/8/19
 */
public final class SdkDataStore {

    /** 用户库 */
    public static final Map<String, Map<String, Object>> USERS = new ConcurrentHashMap<>();

    /** 股票池 */
    public static final Map<String, Map<String, Object>> STOCKS = new ConcurrentHashMap<>();

    /** 用户画像（驱动个性化推荐） */
    public static final Map<String, List<String>> USER_PROFILES = new ConcurrentHashMap<>();

    static {
        USERS.put("u-1001", Map.of("userId", "u-1001", "name", "张三", "role", "admin", "status", "ACTIVE"));
        USERS.put("u-1002", Map.of("userId", "u-1002", "name", "李四", "role", "user", "status", "ACTIVE"));
        USERS.put("u-1003", Map.of("userId", "u-1003", "name", "王五", "role", "viewer", "status", "DISABLED"));

        STOCKS.put("AAPL", Map.of("code", "AAPL", "name", "苹果", "basePrice", 180.00, "industry", "科技"));
        STOCKS.put("600519", Map.of("code", "600519", "name", "贵州茅台", "basePrice", 1688.00, "industry", "白酒"));

        USER_PROFILES.put("u-1001", List.of("tech", "finance"));
        USER_PROFILES.put("u-1002", List.of("sports", "music"));
        USER_PROFILES.put("u-1003", List.of("finance", "sports"));
    }

    private SdkDataStore() {
    }
}
