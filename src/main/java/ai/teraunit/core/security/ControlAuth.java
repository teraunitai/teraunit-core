package ai.teraunit.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ControlAuth {

    private final String controlToken;

    public ControlAuth(@Value("${teraunit.control-token:}") String controlToken) {
        this.controlToken = (controlToken == null) ? "" : controlToken;
    }

    public void requireControlToken(HttpServletRequest request) {
        if (controlToken.isBlank()) {
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

        if (!TokenUtil.constantTimeEquals(controlToken, (provided == null) ? "" : provided.trim())) {
            throw new SecurityException("CONTROL_TOKEN_INVALID");
        }
    }
}
