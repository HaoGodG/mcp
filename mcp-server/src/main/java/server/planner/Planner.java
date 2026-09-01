package server.planner;

import org.springframework.stereotype.Component;
import server.entity.ToolCall;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * author Hao
 * date 2026/7/22 17:20
 */
@Component
public class Planner {

    public List<ToolCall> plan(String query) {
        List<ToolCall> tools = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return List.of(new ToolCall("echo", Map.of("text", "参数错误")));
        }
        if (query.contains("今天")) {
            tools.add(new ToolCall("date", Map.of("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))));
        }

        if (query.contains("天气")) {
            tools.add(new ToolCall("weather", Map.of("city", "宁波")));
        }

        return tools;

    }
}
