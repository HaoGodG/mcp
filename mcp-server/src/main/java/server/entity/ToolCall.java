package server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * author Hao
 * date 2026/7/22 17:20
 */
@Data
@AllArgsConstructor
public class ToolCall {
    private String name;
    private Map<String, Object> args;
}
