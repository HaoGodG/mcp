package server.service;

import reactor.core.publisher.Flux;
import server.entity.ToolSpec;

import java.util.List;
import java.util.Map;

/**
 * author Hao
 * date 2026/7/22 16:10
 */
public interface ToolService {

    /** 通讯协议：SSE 流式 */
    String PROTOCOL_STREAM = "stream";
    /** 通讯协议：同步 JSON */
    String PROTOCOL_SYNC = "sync";

    /** 按通讯协议返回对应的工具集 */
    List<ToolSpec> listTools(String protocol);

    /** 全量工具列表（单端点 tools/list 返回） */
    List<ToolSpec> listAllTools();

    /** 判断工具是否流式（决定单端点响应类型：SSE vs JSON） */
    boolean isStreamTool(String toolName);

    /** 同步执行工具调用（sync 端点 / 单端点同步工具） */
    Object call(String toolName, Map<String, Object> args);

    /** 流式执行工具调用（stream 端点 / 旧 agent 链路） */
    Flux<Object> callStream(String toolName, Map<String, Object> args);
}
