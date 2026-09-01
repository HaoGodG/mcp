package sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * mcp-server SDK（零外部框架依赖：仅 JDK + Jackson）
 *
 * 能力：
 *  - 个性化操作（/personalize）：X-SDK-Key 头鉴权
 *  - 同步 MCP 全报文加密调用（/mcp/sync + X-Encrypted: rsa）：RSA 公钥分段加密整个 JSON-RPC 报文，
 *    服务端用私钥解密，支持任意长度报文
 *
 * 用法：
 *   McpSdkClient sdk = new McpSdkClient("http://localhost:8081", "internal-sdk-key-2026");
 *
 * author Hao
 * date 2026/8/14
 */
public class McpSdkClient {

    /** SDK 调用凭证头（个性化入口） */
    private static final String SDK_KEY_HEADER = "X-SDK-Key";
    /** 加密报文标识头 */
    private static final String ENCRYPTED_HEADER = "X-Encrypted";

    /** 与 mcp-server 约定的协议版本 */
    private static final String PROTOCOL_VERSION = "2025-06-18";

    /** RSA 2048：单块明文上限（字节） */
    private static final int PLAIN_BLOCK = 245;

    /** mcp-server 内置 RSA 公钥（Base64，与 server 私钥配对） */
    private static final String PUBLIC_KEY_B64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArnnhHX9CRUvctQ1eYG/hUrgs1M73KI9iMwOQUl1BK9G6zP7sg6J9dOM/rX/WVAIWwZmIqvh9WXDJYyhY16KFUI/zyi1hQKnivrSqRORztNRC79NbfHV/fC5WEDow3JbdrWkvcMbiLrTHo4anzP5nyL1IHN/3dpRur29r7l7JZ+DlNH7sZsm3P+NGfBCvT3/Byy+MQccghF+d0AeH+pInxDg9yZTlRJW8u0g0pXIE+Latq3B4ngSLYeEsQESFXEmzPBak6QRj6hFrNbTy2aKimY7G2SClq+mQWtinuC11EE7xMnWK3G7vJdDCsZpIgSiByVExSYRjPM+5NPJD4/XJmwIDAQAB";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String sdkKey;
    private final PublicKey publicKey;

    /**
     * @param baseUrl mcp-server 地址，如 http://localhost:8081
     * @param sdkKey  SDK 调用凭证（与 server 的 personalize.sdk-key 一致）
     */
    public McpSdkClient(String baseUrl, String sdkKey) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.sdkKey = sdkKey;
        this.publicKey = loadPublicKey(PUBLIC_KEY_B64);
    }

    // ---------------------------------------------------------------
    // 个性化操作（/personalize，X-SDK-Key 鉴权）
    // ---------------------------------------------------------------

    /** 获取用户个性化偏好（首次返回默认偏好） */
    public Map<String, Object> getPreferences(String userId) {
        JsonNode resp = request("/personalize/preferences", Map.of("userId", userId), false);
        return objectMapper.convertValue(resp.get("preferences"), Map.class);
    }

    /** 更新用户个性化偏好（服务端合并），返回更新后的完整偏好 */
    public Map<String, Object> updatePreferences(String userId, Map<String, Object> preferences) {
        JsonNode resp = request("/personalize/preferences/update", Map.of(
                "userId", userId,
                "preferences", preferences
        ), false);
        return objectMapper.convertValue(resp.get("preferences"), Map.class);
    }

    /** 基于偏好的个性化推荐 */
    public List<Map<String, Object>> recommend(String userId) {
        JsonNode resp = request("/personalize/recommendations", Map.of("userId", userId), false);
        return objectMapper.convertValue(resp.get("recommendations"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
    }

    // ---------------------------------------------------------------
    // 标准 MCP 全报文加密调用（POST /mcp + X-Encrypted: rsa，与标准端点统一）
    // 服务端 /mcp 同时支持明文（标准客户端）与加密（SDK）两种报文，互不影响
    // ---------------------------------------------------------------

    /** 标准 MCP initialize（全报文 RSA 加密） */
    public JsonNode initializeSecure() {
        return request("/mcp", Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "mcp-sdk", "version", "1.0.0")
                )
        ), true);
    }

    /** 标准 MCP tools/list（全报文 RSA 加密） */
    public JsonNode listToolsSecure() {
        return request("/mcp", Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/list",
                "params", Map.of()
        ), true);
    }

    /** 标准 MCP 工具调用（全报文 RSA 加密） */
    public JsonNode callSyncSecure(String toolName, Map<String, Object> args) {
        return request("/mcp", Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", args
                )
        ), true);
    }

    // ---------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------

    /**
     * 发送 POST 请求
     *
     * @param encrypted true 表示全报文 RSA 加密（Content-Type: text/plain + X-Encrypted: rsa）
     */
    private JsonNode request(String path, Map<String, Object> body, boolean encrypted) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String payload = encrypted ? encrypt(json) : json;
            String contentType = encrypted ? "text/plain" : "application/json";

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", contentType)
                    .header(SDK_KEY_HEADER, sdkKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (encrypted) {
                builder.header(ENCRYPTED_HEADER, "rsa");
            }

            HttpResponse<String> resp = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            JsonNode node = objectMapper.readTree(resp.body());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("SDK 调用失败 HTTP " + resp.statusCode()
                        + ": " + resp.body());
            }
            // 业务错误（个性化入口返回 code/message）
            if (node.has("code") && node.has("message")) {
                throw new IllegalStateException("SDK 调用失败: " + node.get("message").asText());
            }
            // JSON-RPC 错误
            if (node.has("error")) {
                throw new IllegalStateException("MCP error: " + node.get("error"));
            }
            return node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SDK 调用异常: " + e.getMessage(), e);
        }
    }

    /** RSA 公钥分段加密（每段 245 字节，密文 Base64 拼接） */
    private String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] plainBytes = plainText.getBytes();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < plainBytes.length; i += PLAIN_BLOCK) {
                int len = Math.min(PLAIN_BLOCK, plainBytes.length - i);
                byte[] block = cipher.doFinal(plainBytes, i, len);
                sb.append(Base64.getEncoder().encodeToString(block));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败: " + e.getMessage(), e);
        }
    }

    private static PublicKey loadPublicKey(String keyB64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyB64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("加载 RSA 公钥失败: " + e.getMessage(), e);
        }
    }
}
