package ai.teraunit.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenUtilTests {

    @Test
    void sanitizeApiKey_stripsHeaderBearerWhitespaceQuotesAndPunctuation() {
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey(" abcDEF123 \n"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("Bearer abcDEF123"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("Authorization: Bearer abcDEF123"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("\"abcDEF123\""));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("'abcDEF123'"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("`abcDEF123`"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("<abcDEF123>"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("abcDEF123."));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("abcDEF123,"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("abcDEF123;"));
        assertEquals("abcDEF123", TokenUtil.sanitizeApiKey("abc DEF\t123"));
    }
}
