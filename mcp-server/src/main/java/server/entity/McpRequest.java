package server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * author Hao
 * date 2026/7/21 16:36
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class McpRequest {
    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private Object params;
}
