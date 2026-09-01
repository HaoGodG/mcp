package server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * author Hao
 * date 2026/7/22 17:19
 */
@Data
@AllArgsConstructor
public class AgentEvent {

    private String type;  // token / tool_start / tool_result / final
    private Object data;

}
