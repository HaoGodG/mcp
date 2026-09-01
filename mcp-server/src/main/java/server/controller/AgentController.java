package server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import server.entity.AgentEvent;
import server.entity.AgentRequest;
import server.service.AgentService;

/**
 * author Hao
 * date 2026/7/22 17:02
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    @PostMapping(value = "/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEvent>> run(@RequestBody AgentRequest request) {

        return agentService.run(request.getQuery())
                .map(event -> ServerSentEvent.builder(event).build());

    }
}
