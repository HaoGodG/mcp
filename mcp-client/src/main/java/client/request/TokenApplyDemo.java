package client.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import nbcb.cfca.sadk.algorithm.common.PKIException;
import nbcb.cfca.sadk.lib.crypto.JCrypto;
import nbcb.cfca.sadk.lib.crypto.Session;
import nbcb.cfca.sadk.system.Mechanisms;

public class TokenApplyDemo {

    private static final String GATEWAY_URL =
            "http://127.0.0.1:8080/api/auth/tokenApply";

    private static final String RSA_APP_KEY =
            "37bc1d17_e7d5_4b90_bda6_d36ef76cfcba";

    private static final String SM2_APP_KEY =
            "da7124ee_4d92_42b1_a73c_8212823a682f";

    private static final String CERT_APP_KEY =
            "替换为 CERT 模式 appKey";

    // 替换为真实私钥
    private static final String RSA_PRIVATE_KEY_BASE64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCiPizjrxztp1ZzJYlkeercm2JG6e9jkuW7+o/8G/n9GCqqrTCTqnMSp92aVRFIyASMyT3UsAxMDA0lTT3jkWk7Bo7zuimXIDGRsyVeyMUzbtInn2NLCPufyOLqSHkDDfy+Y46ND1X2TjJ1xmxDSc6HeDHHfMhcU/UemcliMYMAJXQVXptJ4eYH7i5Gr40xYfXRN9ujWT35IRyB8rveRmsZlKAccnkrQoz3gBOpLC0UVRoPGoSVb8q1vA8ORamFh5+3oNuxC3ax3lvX7DzN2G5pg7TLgGTfj1Zd4+XxrMpzJpInun/zG1D5ko+3/cU8651Tz3nUTgcTVY2mtmu3EFSpAgMBAAECggEAehnk2q2tTcYEL8BuOCnw8XoXalsgiIXDU8dsmUXMzAdmBPE2f8tlswKWqlGfInE1y2agm/KqLolbU1lgXGXRFlUHPLI8Hrph089JLp5WmzCqBJvVtGDcThJ3g+5q9DuQnRB79fk2rpmHLE/apoFjZ1yzyfhFKgcdkJwYjUfJ2U8g0puF3WDiacOEvB7+a/vybyBBqyZp00dQaPo8Fg/LdUjM9gCi9ExI3hwg3RdQ0KYTfWigIJ+EcLdidH3zzmmmGkxOvZ9kwsVbn2c225TnUnEYV9GJFVzTLwCVmHaQcQ2US25/7e9CWhuPNJhjBsPEQnmfI5G5ccsKbHbm37h+qQKBgQDqnSlvwHu2rxbz0s/+Gn1lPuJS10RgyrqV98WYiud7U3VK1XhlxLQxnBflHu3vJ/uvLItwqjUEYMSbUDzifzKBIp2k6tVkZn7knAo8VJc1I3aqqe03tM+Kn7bvwpFjPv5AokdoAifo/7VIte4IoGsi5j8EZijixaouC7KgvX7CSwKBgQCxCDKNObPAR6aKOwzmOHEI+PcTX4I4Y2NsXksiBCKV2aMK/6xpjlwg8UTUivgG4nZRHLtqnM4FNPwA0UnaV2i53kHQoL8QfMDrFvQ/9/lXtqN2kFlB6dlhsaMKLANDXw+i6V7lV9a4/aS3VmWbwA/vYFxOyXB9kfDxt6NZQBlMWwKBgQDOoyGt1cCgxFHY6qJa0gYDuIEqKYOGJMh18cbNdfovuvAhuybRq5Bx6WN6X+V8sKCSRw+BachMVNaVXPRjIVjgOBbU/Ch6x3OX8n9pZ6/OE7Ae3I+cctog5E8BmULoQME7ODLgPpXcN+v5YJOIcZIrKNP0Ee6M3T/oUlFAFeahRQKBgBNc0iqgJQjizVRRIRgNFE/m6x8zUwrX1AgGSDFwQlghdbO+Qx1IdMslmGGm0XnvSwGUIuuGOwJWyTlNqsY2yT2LEae/7SXgfzk3SX+1n4/4aeiN4TVUXnJQ+4QhTDoSK6Ol4rsy4ElbdKiYyoQ+fX+xCmeToLobPD8z1qnsWNgBAoGAZyLM8HAlWlbDQuSpLZdAqrKy0wY0eA2nXJBz53ygf/PaMvbMhgAv8T4l7e+xOWw7ShuIWWK2dwu2stdujdA36xWkKGrPGSh6dqKe5HUGYoOUviy0XaQF6sPLc3wanr6B+rFX3XrdlqnKVyU40tBsQQCgnTqqwueGOtw/uvX9eJs=";

    // 替换为真实 SM2 私钥
    private static final String SM2_PRIVATE_KEY_BASE64 = "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgEEQMz7zloaogtcFzEnYbxeTXoJ5HXEcCrA2qVRy9FPmgCgYIKoEcz1UBgi2hRANCAATVjAoxBZMw3apF0A5HbegpE8q1+CazPP88iNCYP1uG1SSOfR2pvTA37sIZLKw/EV2IJf8R4FOyIbqUDQdubd37";

    // classpath 中的 PKCS#8 格式 SM2 私钥（文件内容为 Base64 文本）
    private static final String CERT_PRIVATE_KEY_RESOURCE =
            "77667c76_3503_4c04_95f7_fc10938c7942.sm2";

    private static final Session CERT_SESSION = createCertSession();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        WebClient webClient = WebClient.builder().build();

        JsonNode rsaResult = rsaToken(webClient);

        System.out.println("RSA token response: " + rsaResult);

        JsonNode sm2Result = sm2token(webClient);

        System.out.println("SM2 token response: " + sm2Result);

        JsonNode certResult = certToken(webClient);
        System.out.println("CERT token response: " + certResult);
    }

    public static JsonNode sm2token(WebClient webClient) throws Exception {
        JsonNode sm2Result = request(
                webClient,
                SM2_APP_KEY,
                "SM2",
                SM2_PRIVATE_KEY_BASE64
        );
        return sm2Result;
    }

    public static JsonNode rsaToken(WebClient webClient) throws Exception {
        JsonNode rsaResult = request(
                webClient,
                RSA_APP_KEY,
                "RSA",
                RSA_PRIVATE_KEY_BASE64
        );
        return rsaResult;
    }

    public static JsonNode certToken(WebClient webClient) throws Exception {
        return request(webClient, CERT_APP_KEY, "CERT", readPrivateKeyResource());
    }

    private static JsonNode request(WebClient webClient,
                                    String appKey,
                                    String algorithm,
                                    String privateKeyBase64) throws Exception {

        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomKey = UUID.randomUUID().toString().replace("-", "");

        // 按字段名升序排列：appKey、randomKey、timestamp
        Map<String, String> canonicalFields = new TreeMap<>();
        canonicalFields.put("appKey", appKey);
        canonicalFields.put("randomKey", randomKey);
        canonicalFields.put("timestamp", timestamp);

        String canonicalJson = OBJECT_MAPPER.writeValueAsString(canonicalFields);

        String signature = sign(
                algorithm,
                privateKeyBase64,
                canonicalJson
        );

        Map<String, String> requestBody = Map.of(
                "appKey", base64(appKey),
                "timestamp", base64(timestamp),
                "randomKey", base64(randomKey)
        );

        return webClient.post()
                .uri(GATEWAY_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("signature", signature)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private static String sign(String algorithm,
                               String privateKeyBase64,
                               String content) throws Exception {

        PrivateKey privateKey;

        if ("CERT".equalsIgnoreCase(algorithm)) {
            if (CERT_SESSION == null) {
                throw new IllegalStateException("CERT 会话初始化失败");
            }
            PrivateKey certPrivateKey = createGMPrivateKey(privateKeyBase64);
            byte[] signed = new nbcb.cfca.sadk.util.Signature().p1SignMessage(
                    String.valueOf(Mechanisms.M_SM3_SM2),
                    content.getBytes(StandardCharsets.UTF_8),
                    certPrivateKey,
                    CERT_SESSION
            );
            return Base64.getEncoder().encodeToString(signed);
        }

        if ("RSA".equalsIgnoreCase(algorithm)) {
            privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(
                            Base64.getDecoder().decode(privateKeyBase64)
                    ));
        } else if ("SM2".equalsIgnoreCase(algorithm)) {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            privateKey = KeyFactory.getInstance("EC", "BC")
                    .generatePrivate(new PKCS8EncodedKeySpec(
                            Base64.getDecoder().decode(privateKeyBase64)
                    ));
        } else {
            throw new IllegalArgumentException("不支持的签名算法: " + algorithm);
        }

        Signature signer;

        if ("RSA".equalsIgnoreCase(algorithm)) {
            signer = Signature.getInstance("SHA256withRSA");
        } else {
            signer = Signature.getInstance("SM3withSM2", "BC");
        }

        signer.initSign(privateKey);
        signer.update(content.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static PrivateKey createGMPrivateKey(String privateKeyBase64) throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        return KeyFactory.getInstance("EC", "BC")
                .generatePrivate(new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(privateKeyBase64)
                ));
    }

    private static String readPrivateKeyResource() throws IOException {
        ClassLoader classLoader = TokenApplyDemo.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(CERT_PRIVATE_KEY_RESOURCE)) {
            if (input == null) {
                throw new IOException("找不到 CERT 私钥资源: " + CERT_PRIVATE_KEY_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", "");
        }
    }

    private static Session createCertSession() {
        try {
            String deviceName = JCrypto.JSOFT_LIB;
            JCrypto.getInstance().initialize(deviceName, null);
            return JCrypto.getInstance().openSession(deviceName);
        } catch (PKIException e) {
            System.err.println("generate cfca session exception: " + e.getMessage());
            return null;
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }
}
