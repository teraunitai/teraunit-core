package ai.teraunit.core.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyVaultServiceTests {

    private static String randomVaultKeyB64() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static KeyVaultService newVaultWithKeys(String keysRaw) throws Exception {
        KeyVaultService vault = new KeyVaultService();
        Field f = KeyVaultService.class.getDeclaredField("base64KeysRaw");
        f.setAccessible(true);
        f.set(vault, keysRaw);
        vault.init();
        return vault;
    }

    @Test
    void decrypt_allowsOldKeyAfterRotation_encryptUsesPrimary() throws Exception {
        String oldKey = randomVaultKeyB64();
        String newKey = randomVaultKeyB64();

        KeyVaultService oldVault = newVaultWithKeys(oldKey);
        String payload = oldVault.encrypt("hello");

        // Rotation setup: new key first (used for new encryptions), old key second (used for decrypting existing rows)
        KeyVaultService rotated = newVaultWithKeys(newKey + "," + oldKey);

        assertEquals("hello", rotated.decrypt(payload));
        assertEquals("world", rotated.decrypt(rotated.encrypt("world")));
    }
}
