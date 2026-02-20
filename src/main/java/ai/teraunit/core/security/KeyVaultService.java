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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class KeyVaultService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${TERA_VAULT_KEY}")
    private String base64KeysRaw;

    private SecretKey primaryKey;
    private List<SecretKey> allKeys;

    @PostConstruct
    public void init() {
        List<String> keys = parseKeyList(base64KeysRaw);
        if (keys.isEmpty()) {
            throw new SecurityException("VAULT_KEY_NOT_CONFIGURED");
        }

        List<SecretKey> decodedKeys = new ArrayList<>(keys.size());
        for (String b64 : keys) {
            try {
                byte[] decoded = Base64.getDecoder().decode(b64);
                if (decoded.length != 32) {
                    throw new SecurityException("VAULT_KEY_INVALID_LENGTH");
                }
                decodedKeys.add(new SecretKeySpec(decoded, "AES"));
            } catch (IllegalArgumentException e) {
                throw new SecurityException("VAULT_KEY_INVALID_BASE64");
            }
        }

        this.allKeys = List.copyOf(decodedKeys);
        this.primaryKey = this.allKeys.getFirst();
    }

    private static List<String> parseKeyList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        // Support rotation by allowing a comma/semicolon/newline separated list:
        //   TERA_VAULT_KEY="<newB64>,<oldB64>"
        String normalized = raw.replace('\r', ',').replace('\n', ',').replace(';', ',').trim();
        String[] parts = normalized.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = (p == null) ? "" : p.trim();
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return List.copyOf(out);
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, primaryKey, spec);

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
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptedPayload);
        } catch (Exception e) {
            throw new SecurityException("VAULT_ACCESS_DENIED: Integrity Check Failed");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        if (byteBuffer.remaining() < GCM_IV_LENGTH + 1) {
            throw new SecurityException("VAULT_ACCESS_DENIED: Integrity Check Failed");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        for (SecretKey candidateKey : allKeys) {
            try {
                final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, candidateKey, spec);
                return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // Try next key.
            }
        }

        throw new SecurityException("VAULT_ACCESS_DENIED: Integrity Check Failed");
    }
}
