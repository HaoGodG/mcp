package server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import server.entity.AgentEvent;
import server.entity.ToolCall;
import server.planner.Planner;

import java.util.List;

/**
 * author Hao
 * date 2026/7/22 17:21
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final Planner planner;
    private final ToolService toolService;

    public Flux<AgentEvent> run(String query) {

        // 1️⃣ 规划调用哪些 tool
        List<ToolCall> tools = planner.plan(query);

        // 2️⃣ 每个 tool 一个流
        List<Flux<AgentEvent>> toolFluxes = tools.stream()
                .map(tool -> {

                    // tool start 事件
                    Flux<AgentEvent> start = Flux.just(
                            new AgentEvent("tool_start", tool.getName())
                    );

                    // tool 执行流
                    Flux<AgentEvent> stream = toolService
                            .callStream(tool.getName(), tool.getArgs())
                            .map(res -> new AgentEvent("tool_result", res))
                            .onErrorResume(e -> Flux.just(
                                    new AgentEvent("tool_error", e.getMessage())
                            ));

                    return start.concatWith(stream);
                })
                .toList();

        // 3️⃣ 并行执行
        Flux<AgentEvent> merged = Flux.merge(toolFluxes);

        // 4️⃣ 最终结果（可选）
        Flux<AgentEvent> end = Flux.just(
                new AgentEvent("final", "done")
        );

        return merged.concatWith(end);
    }
}
