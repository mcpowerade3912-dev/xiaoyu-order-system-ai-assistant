package com.xiaoyu.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SettingCrypto {
    private static final String PREFIX = "enc:";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SettingCrypto(@Value("${settings.encryption-key}") String secret) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(digest, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置加密失败", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置解密失败，请重新设置 API Key", e);
        }
    }
}
