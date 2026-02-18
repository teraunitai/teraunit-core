package ai.teraunit.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class TokenUtil {

    private static final SecureRandom RNG = new SecureRandom();

    private TokenUtil() {
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(((value == null) ? "" : value).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean constantTimeEquals(String a, String b) {
        byte[] aa = ((a == null) ? "" : a).getBytes(StandardCharsets.UTF_8);
        byte[] bb = ((b == null) ? "" : b).getBytes(StandardCharsets.UTF_8);

        if (aa.length != bb.length) {
            // still do a comparison to reduce timing differences
            int max = Math.max(aa.length, bb.length);
            byte[] pa = new byte[max];
            byte[] pb = new byte[max];
            System.arraycopy(aa, 0, pa, 0, aa.length);
            System.arraycopy(bb, 0, pb, 0, bb.length);
            return MessageDigest.isEqual(pa, pb) && aa.length == bb.length;
        }

        return MessageDigest.isEqual(aa, bb);
    }

    public static String sanitizeApiKey(String apiKey) {
        if (apiKey == null)
            return "";

        // Strip common invisible/control chars (includes zero-width spaces) and trim.
        // Keep it simple to avoid altering legitimate tokens.
        String cleaned = apiKey
                .replaceAll("[\\p{Cc}\\p{Cf}]", "")
                .trim();

        // Also remove whitespace inside if it’s clearly accidental (newlines/tabs)
        cleaned = cleaned.replace("\r", "").replace("\n", "").replace("\t", "");
        return cleaned;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit((b) & 0xF, 16));
        }
        return sb.toString();
    }
}
