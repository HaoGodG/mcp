package client.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * author Hao
 * date 2026/7/22 17:18
 */
@Data
@AllArgsConstructor
public class AgentRequest {
    private String id;
    private String query;
}
