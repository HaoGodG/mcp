package sdkserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sdkserver.data.SdkDataStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDK 服务端：个性化入口（独立进程直接提供）
 *
 *  - POST /personalize/preferences         获取偏好（含画像）
 *  - POST /personalize/preferences/update  更新偏好（合并，按 userId 独立）
 *  - POST /personalize/recommendations     画像+偏好驱动推荐（含评分）
 *
 * 鉴权：X-SDK-Key
 *
 * author Hao
 * date 2026/8/19
 */
@RestController
@RequestMapping("/personalize")
public class PersonalizeController {

    private static final String SDK_KEY_HEADER = "X-SDK-Key";

    @Value("${personalize.sdk-key}")
    private String sdkKey;

    private final Map<String, Map<String, Object>> preferencesStore = new ConcurrentHashMap<>();

    private static final Map<String, Object> DEFAULT_PREFERENCES = Map.of(
            "theme", "light",
            "language", "zh-CN",
            "notifyEnabled", true,
            "recommendCategories", List.of("tech", "sports")
    );

    private static final Map<String, List<Map<String, Object>>> CONTENT_LIBRARY = Map.of(
            "tech", List.of(
                    Map.of("id", "rec-tech-1", "category", "tech", "title", "AI 前沿技术周报", "summary", "本周大模型与智能体技术进展", "score", 9.2),
                    Map.of("id", "rec-tech-2", "category", "tech", "title", "云原生架构实践指南", "summary", "K8s + Service Mesh 落地经验", "score", 8.7)
            ),
            "finance", List.of(
                    Map.of("id", "rec-fin-1", "category", "finance", "title", "市场行情分析", "summary", "A 股三大指数走势与资金流向", "score", 8.9),
                    Map.of("id", "rec-fin-2", "category", "finance", "title", "基金定投策略入门", "summary", "长期定投的择时与仓位管理", "score", 8.1)
            ),
            "sports", List.of(
                    Map.of("id", "rec-spo-1", "category", "sports", "title", "本周体育赛事精选", "summary", "英超/西甲/篮球焦点战前瞻", "score", 9.0),
                    Map.of("id", "rec-spo-2", "category", "sports", "title", "马拉松训练计划", "summary", "从 5km 到全马的 16 周训练表", "score", 8.4)
            ),
            "music", List.of(
                    Map.of("id", "rec-mus-1", "category", "music", "title", "本周热门歌单", "summary", "华语/欧美/日韩榜单 Top 20", "score", 8.8),
                    Map.of("id", "rec-mus-2", "category", "music", "title", "深夜爵士精选", "summary", "适合工作学习的轻音乐合集", "score", 8.5)
            )
    );

    @PostMapping("/preferences")
    public ResponseEntity<?> getPreferences(
            @RequestHeader(value = SDK_KEY_HEADER, required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!auth(key)) {
            return unauthorized();
        }
        String userId = body.get("userId");
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "profile", SdkDataStore.USER_PROFILES.getOrDefault(userId, List.of()),
                "preferences", preferencesStore.getOrDefault(userId, DEFAULT_PREFERENCES)
        ));
    }

    @PostMapping("/preferences/update")
    public ResponseEntity<?> updatePreferences(
            @RequestHeader(value = SDK_KEY_HEADER, required = false) String key,
            @RequestBody Map<String, Object> body) {
        if (!auth(key)) {
            return unauthorized();
        }
        String userId = String.valueOf(body.get("userId"));
        Map<String, Object> updates = (Map<String, Object>) body.get("preferences");
        Map<String, Object> merged = new ConcurrentHashMap<>(
                preferencesStore.getOrDefault(userId, DEFAULT_PREFERENCES));
        merged.putAll(updates);
        preferencesStore.put(userId, merged);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "updated", updates,
                "preferences", merged
        ));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<?> recommend(
            @RequestHeader(value = SDK_KEY_HEADER, required = false) String key,
            @RequestBody Map<String, String> body) {
        if (!auth(key)) {
            return unauthorized();
        }
        String userId = body.get("userId");

        Map<String, Object> prefs = preferencesStore.getOrDefault(userId, DEFAULT_PREFERENCES);
        List<String> categories = new java.util.ArrayList<>(
                (List<String>) prefs.getOrDefault("recommendCategories", List.of()));
        for (String c : SdkDataStore.USER_PROFILES.getOrDefault(userId, List.of())) {
            if (!categories.contains(c)) {
                categories.add(c);
            }
        }

        List<Map<String, Object>> items = categories.stream()
                .filter(CONTENT_LIBRARY::containsKey)
                .flatMap(cat -> CONTENT_LIBRARY.get(cat).stream())
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    double score = ((Number) item.get("score")).doubleValue()
                            + (categories.indexOf(item.get("category")) == 0 ? 0.5 : 0);
                    enriched.put("score", Math.round(score * 10.0) / 10.0);
                    return enriched;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "profile", SdkDataStore.USER_PROFILES.getOrDefault(userId, List.of()),
                "recommendations", items
        ));
    }

    private boolean auth(String key) {
        return sdkKey != null && sdkKey.equals(key);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "message", "invalid or missing X-SDK-Key"
        ));
    }
}
