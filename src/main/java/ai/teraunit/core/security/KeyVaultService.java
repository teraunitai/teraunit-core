package ai.teraunit.core.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class KeyVaultService {

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${TERA_VAULT_KEY}")
    private String base64Key;

    private SecretKey masterKey;

    @PostConstruct
    public void init() {
        String raw = sanitizeSecretValue(base64Key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "TERA_VAULT_KEY is missing. Set it to a Base64-encoded 32-byte key (recommended) " +
                            "or a 64-char hex key.");
        }

        byte[] decodedKey = decodeVaultKey(raw);
        // For AES-256 we require 32 bytes. (AES also supports 16/24 bytes, but we
        // intentionally enforce 256-bit here.)
        if (decodedKey.length != (AES_KEY_SIZE / 8)) {
            throw new IllegalStateException(
                    "TERA_VAULT_KEY must decode to 32 bytes for AES-256-GCM. " +
                            "Got " + decodedKey.length + " bytes. " +
                            "If you generated hex via `openssl rand -hex 32`, convert it to Base64 " +
                            "or paste the 64-hex chars directly (supported).");
        }

        this.masterKey = new SecretKeySpec(decodedKey, "AES");
    }

    private static String sanitizeSecretValue(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();

        // Strip surrounding quotes/backticks (Render/CLI copy-paste)
        while (cleaned.length() >= 2) {
            char first = cleaned.charAt(0);
            char last = cleaned.charAt(cleaned.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }
        while (cleaned.length() >= 2 && cleaned.startsWith("`") && cleaned.endsWith("`")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Keys should not contain whitespace/newlines
        cleaned = cleaned.replaceAll("\\s+", "");
        return cleaned;
    }

    private static byte[] decodeVaultKey(String sanitized) {
        // Support 64-char hex (32 bytes)
        if (sanitized.matches("(?i)^[0-9a-f]{64}$")) {
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) {
                int hi = Character.digit(sanitized.charAt(i * 2), 16);
                int lo = Character.digit(sanitized.charAt(i * 2 + 1), 16);
                out[i] = (byte) ((hi << 4) + lo);
            }
            return out;
        }

        // Default: Base64
        try {
            return Base64.getDecoder().decode(sanitized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "TERA_VAULT_KEY is not valid Base64 and not 64-char hex. " +
                            "Provide Base64 of 32 bytes (recommended) or 64 hex chars.");
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine IV + CipherText (IV is needed for decryption)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("VAULT_ENCRYPTION_FAILURE", e);
        }
    }

    public String decrypt(String encryptedPayload) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedPayload);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("VAULT_ACCESS_DENIED: Integrity Check Failed");
        }
    }
}
