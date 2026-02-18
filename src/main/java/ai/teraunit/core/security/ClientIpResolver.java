package ai.teraunit.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(@Value("${teraunit.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(HttpServletRequest request) {
        if (!trustForwardedHeaders) {
            return request.getRemoteAddr();
        }

        String forwarded = request.getHeader("Forwarded");
        String ip = parseForwardedFor(forwarded);
        if (ip != null)
            return ip;

        String xff = request.getHeader("X-Forwarded-For");
        ip = firstCommaSeparated(xff);
        if (ip != null)
            return ip;

        String xri = request.getHeader("X-Real-IP");
        ip = normalizeIp(xri);
        if (ip != null)
            return ip;

        return request.getRemoteAddr();
    }

    private static String firstCommaSeparated(String headerValue) {
        if (headerValue == null)
            return null;
        String first = headerValue.split(",", 2)[0].trim();
        return normalizeIp(first);
    }

    private static String parseForwardedFor(String forwarded) {
        if (forwarded == null || forwarded.isBlank())
            return null;

        // Very small parser for Forwarded: for=1.2.3.4;proto=https, for="[2001:db8::1]"
        // We take the first "for=" occurrence.
        String lower = forwarded.toLowerCase();
        int idx = lower.indexOf("for=");
        if (idx < 0)
            return null;

        String remainder = forwarded.substring(idx + 4);
        String token = remainder.split(";", 2)[0].split(",", 2)[0].trim();

        // Remove optional quotes
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            token = token.substring(1, token.length() - 1);
        }

        // Remove brackets around IPv6
        if (token.startsWith("[") && token.contains("]")) {
            token = token.substring(1, token.indexOf(']'));
        }

        return normalizeIp(token);
    }

    private static String normalizeIp(String ip) {
        if (ip == null)
            return null;
        String trimmed = ip.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed))
            return null;
        return trimmed;
    }
}
