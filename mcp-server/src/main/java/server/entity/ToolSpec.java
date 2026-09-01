package server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * MCP tools/list 返回的单个工具定义
 * author Hao
 * date 2026/8/14
 */
@Data
@AllArgsConstructor
public class ToolSpec {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}
