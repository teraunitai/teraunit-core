package ai.teraunit.core.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String method = safeLogValue(request.getMethod());
        String path = safeLogValue(request.getRequestURI());

        String remoteAddr = safeLogValue(request.getRemoteAddr());
        String xForwardedFor = safeLogValue(request.getHeader("X-Forwarded-For"));
        String userAgent = safeLogValue(request.getHeader("User-Agent"));
        String host = safeLogValue(request.getHeader("Host"));
        String origin = safeLogValue(request.getHeader("Origin"));
        String referer = safeLogValue(request.getHeader("Referer"));

        log.warn(
            "405 Method Not Allowed: method={} path={} remoteAddr={} xff={} host={} origin={} referer={} ua={}",
                method,
                path,
                remoteAddr,
                xForwardedFor,
                host,
                origin,
                referer,
                userAgent);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "METHOD_NOT_ALLOWED");
        body.put("method", request.getMethod());
        body.put("path", request.getRequestURI());
        body.put("allowed", ex.getSupportedHttpMethods());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    private static String safeLogValue(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", " ")
                .trim();

        int maxLen = 300;
        if (cleaned.length() > maxLen) {
            return cleaned.substring(0, maxLen) + "…";
        }

        return cleaned;
    }
}
