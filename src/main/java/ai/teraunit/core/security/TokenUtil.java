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
        // Keep it simple but resilient to copy/paste formats.
        String cleaned = apiKey.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();

        // Common copy/paste mistake: pasting the full HTTP header
        // Authorization: Bearer <token>
        if (cleaned.regionMatches(true, 0, "Authorization:", 0, 14)) {
            int idx = cleaned.indexOf(':');
            cleaned = (idx >= 0) ? cleaned.substring(idx + 1).trim() : cleaned;
        }

        // Common copy/paste mistake: including the scheme prefix
        if (cleaned.regionMatches(true, 0, "Bearer ", 0, 7)) {
            cleaned = cleaned.substring(7).trim();
        }

        // Strip surrounding quotes (people often copy JSON/env values)
        while (cleaned.length() >= 2) {
            char first = cleaned.charAt(0);
            char last = cleaned.charAt(cleaned.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }

        // Also strip surrounding backticks (Markdown / chat copy)
        while (cleaned.length() >= 2 && cleaned.startsWith("`") && cleaned.endsWith("`")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Tokens should never contain whitespace; remove any accidental
        // spaces/newlines/tabs.
        cleaned = cleaned.replaceAll("\\s+", "");

        // Strip common wrappers and trailing punctuation from copy/paste
        cleaned = cleaned.replaceAll("^[<\\[\\(\\{]+", "");
        cleaned = cleaned.replaceAll("[>\\]\\)\\}]+$", "");
        cleaned = cleaned.replaceAll("[\\.,;:]+$", "");

        return cleaned;
    }

    /**
     * Sanitizes a human-typed identifier (e.g., SSH key name).
     * Unlike API keys, these may legitimately contain spaces, so we:
     * - remove invisible/control chars
     * - normalize non-breaking spaces
     * - normalize common Unicode dash variants to '-'
     * - trim and collapse repeated whitespace
     */
    public static String sanitizeHumanIdentifier(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.replaceAll("[\\p{Cc}\\p{Cf}]", "");
        cleaned = cleaned.replace('\u00A0', ' '); // NBSP

        // Normalize common Unicode dashes to ASCII '-'
        cleaned = cleaned
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2015', '-')
                .replace('\u2212', '-');

        cleaned = cleaned.trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
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
