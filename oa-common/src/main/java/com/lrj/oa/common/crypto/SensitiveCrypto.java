package com.lrj.oa.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 敏感字段的<b>密文列 + 哈希列</b>方案。
 *
 * <p>手机号、身份证这类字段既要能展示（需可逆），又要能精确查询（需可比较）。
 * 只加密则查不了，只哈希则展示不了。所以两列都存：
 * <ul>
 *   <li>{@code *_enc} —— AES-256-GCM 密文，带随机 IV，同一明文每次密文都不同（防频率分析）；</li>
 *   <li>{@code *_hash} —— HMAC-SHA256 确定性哈希，同一明文恒定，可建索引精确查；
 *       用 HMAC 而非裸 SHA256，是因为手机号空间只有 10^11，裸哈希可被彩虹表反推。</li>
 * </ul>
 *
 * <p>密钥从环境变量注入，<b>不入库、不进 git</b>。
 */
@Component
public class SensitiveCrypto {

    private static final Logger log = LoggerFactory.getLogger(SensitiveCrypto.class);

    private static final String DEV_KEY = "ZGV2LW9ubHktZGF0YS1rZXktMzJieXRlcy1sb25nISE=";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public SensitiveCrypto(@Value("${oa.crypto.data-key:}") String base64Key) {
        String key = (base64Key == null || base64Key.isBlank()) ? DEV_KEY : base64Key;
        if (key.equals(DEV_KEY)) {
            log.warn("""

                ╔══════════════════════════════════════════════════════════════════╗
                ║  oa.crypto.data-key 未配置，正在使用【开发默认密钥】              ║
                ║  手机号/身份证等敏感字段的密文可被任何拿到本仓库的人解开          ║
                ║  上线前必须注入：OA_CRYPTO_DATA_KEY=$(openssl rand -base64 32)   ║
                ╚══════════════════════════════════════════════════════════════════╝""");
        }
        byte[] raw = Base64.getDecoder().decode(key);
        if (raw.length != 32) {
            throw new IllegalStateException("oa.crypto.data-key 必须是 base64 编码的 32 字节（AES-256）");
        }
        this.aesKey = new SecretKeySpec(raw, "AES");
        // HMAC 用独立派生的密钥，避免同一密钥同时用于加密与认证
        this.hmacKey = new SecretKeySpec(sha256(concat(raw, "oa-hmac".getBytes(StandardCharsets.UTF_8))), "HmacSHA256");
    }

    /** 加密。null 进 null 出，方便直接喂给实体 setter。 */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return concat(iv, ct);          // IV 前置存储
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    public String decrypt(byte[] stored) {
        if (stored == null || stored.length <= GCM_IV_LEN) return null;
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(stored, 0, iv, 0, GCM_IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(stored, GCM_IV_LEN, stored.length - GCM_IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段解密失败（密钥是否换过？）", e);
        }
    }

    /** 确定性哈希，供索引精确查询。 */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段哈希失败", e);
        }
    }

    private static byte[] sha256(byte[] in) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
