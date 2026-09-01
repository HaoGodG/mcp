package client.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * author Hao
 * date 2026/7/22 17:19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentEvent {

    private String type;  // token / tool_start / tool_result / final
    private Object data;

}
