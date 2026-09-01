package sdkserver.crypto;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 非对称解密（与 mcp-sdk 内置公钥配对）
 *
 * author Hao
 * date 2026/8/19
 */
public class RsaCryptoUtil {

    /** RSA 2048：单块密文长度（字节） */
    private static final int CIPHER_BLOCK = 256;

    /** 用 Base64 私钥解密 Base64 密文（全报文，自动分段） */
    public static String decrypt(String privateKeyB64, String cipherB64) {
        try {
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyB64);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] cipherBytes = Base64.getDecoder().decode(cipherB64);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cipherBytes.length; i += CIPHER_BLOCK) {
                int len = Math.min(CIPHER_BLOCK, cipherBytes.length - i);
                byte[] block = cipher.doFinal(cipherBytes, i, len);
                sb.append(new String(block));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 解密失败: " + e.getMessage(), e);
        }
    }
}
