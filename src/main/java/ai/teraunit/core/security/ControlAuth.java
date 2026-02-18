package ai.teraunit.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ControlAuth {

    private final List<String> controlTokens;

    public ControlAuth(@Value("${teraunit.control-token:}") String controlToken) {
        this.controlTokens = parseTokens(controlToken);
    }

    public void requireControlToken(HttpServletRequest request) {
        if (controlTokens.isEmpty()) {
            throw new SecurityException("CONTROL_TOKEN_NOT_CONFIGURED");
        }

        String provided = request.getHeader("X-Tera-Control-Token");
        if (provided == null || provided.isBlank()) {
            // Support Authorization: Bearer <token> as an alternate
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = auth.substring(7).trim();
            }
        }

        String candidate = (provided == null) ? "" : provided.trim();
        boolean ok = false;
        for (String token : controlTokens) {
            ok |= TokenUtil.constantTimeEquals(token, candidate);
        }

        if (!ok) {
            throw new SecurityException("CONTROL_TOKEN_INVALID");
        }
    }

    private static List<String> parseTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        // Accept comma, newline, or space separated tokens.
        String normalized = raw.replace('\n', ',').replace('\r', ',').replace(' ', ',').trim();
        String[] parts = normalized.split(",");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String t = (p == null) ? "" : p.trim();
            if (!t.isBlank()) {
                tokens.add(t);
            }
        }
        return List.copyOf(tokens);
    }
}
