package client.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 响应（JSON-RPC 2.0）
 * author Hao
 * date 2026/7/21 16:38
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {
    private String jsonrpc = "2.0";
    private String id;
    private Object result;
    private Object error;
}
