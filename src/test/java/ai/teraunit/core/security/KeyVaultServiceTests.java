package ai.teraunit.core.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KeyVaultServiceTests {

    @Test
    void vaultAcceptsBase64Key_encryptDecryptRoundTrip() throws Exception {
        KeyVaultService vault = new KeyVaultService();

        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) i;
        }
        String base64 = Base64.getEncoder().encodeToString(keyBytes);

        setPrivateField(vault, "base64Key", base64);
        vault.init();

        String plain = "hello";
        String enc = vault.encrypt(plain);
        assertNotNull(enc);
        assertEquals(plain, vault.decrypt(enc));
    }

    @Test
    void vaultAcceptsHexKey_encryptDecryptRoundTrip() throws Exception {
        KeyVaultService vault = new KeyVaultService();

        // 32 bytes in hex (64 chars)
        String hex = "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f";

        setPrivateField(vault, "base64Key", hex);
        vault.init();

        String plain = "hello";
        String enc = vault.encrypt(plain);
        assertNotNull(enc);
        assertEquals(plain, vault.decrypt(enc));
    }

    private static void setPrivateField(Object target, String fieldName, String value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
