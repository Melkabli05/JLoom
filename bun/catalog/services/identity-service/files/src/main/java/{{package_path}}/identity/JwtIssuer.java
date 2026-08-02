package {{package}}.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class JwtIssuer {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URLD = Base64.getUrlDecoder();

    private final byte[] secret;
    private final String issuer;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtIssuer(byte[] secret, String issuer, long ttlSeconds, Clock clock) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("HS256 secret must be at least 32 bytes");
        }
        this.secret = secret;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public String issue(String subject) {
        return issue(subject, List.of());
    }

    public String issue(String subject, List<String> roles) {
        Instant now = clock.instant();
        long exp = now.getEpochSecond() + ttlSeconds;
        String header = B64URL.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8));
        StringBuilder payloadJson = new StringBuilder();
        payloadJson.append("{\"sub\":\"").append(escape(subject)).append('"');
        payloadJson.append(",\"iss\":\"").append(escape(issuer)).append('"');
        payloadJson.append(",\"iat\":").append(now.getEpochSecond());
        payloadJson.append(",\"exp\":").append(exp);
        if (!roles.isEmpty()) {
            payloadJson.append(",\"roles\":[");
            for (int i = 0; i < roles.size(); i++) {
                if (i > 0) {
                    payloadJson.append(',');
                }
                payloadJson.append('"').append(escape(roles.get(i))).append('"');
            }
            payloadJson.append(']');
        }
        payloadJson.append('}');
        String payload = B64URL.encodeToString(payloadJson.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = B64URL.encodeToString(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    public String verify(String token) {
        int first = token.indexOf('.');
        int second = token.indexOf('.', first + 1);
        if (first <= 0 || second <= first + 1) {
            throw new IllegalArgumentException("malformed JWT");
        }
        String headerB64 = token.substring(0, first);
        String payloadB64 = token.substring(first + 1, second);
        String signatureB64 = token.substring(second + 1);
        String signingInput = token.substring(0, second);
        byte[] expectedSig = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actualSig;
        try {
            actualSig = B64URLD.decode(signatureB64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed signature", e);
        }
        if (!java.security.MessageDigest.isEqual(expectedSig, actualSig)) {
            throw new IllegalArgumentException("signature mismatch");
        }
        String payloadJson = new String(B64URLD.decode(payloadB64), StandardCharsets.UTF_8);
        long exp = readJsonLong(payloadJson, "exp");
        if (exp <= clock.instant().getEpochSecond()) {
            throw new IllegalArgumentException("JWT expired");
        }
        return readJsonString(payloadJson, "sub");
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long readJsonLong(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) throw new IllegalArgumentException("missing field: " + key);
        int start = i + needle.length();
        int end = start;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
        return Long.parseLong(json.substring(start, end));
    }

    private static String readJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) throw new IllegalArgumentException("missing field: " + key);
        int start = i + needle.length();
        int end = json.indexOf('"', start);
        StringBuilder sb = new StringBuilder();
        for (int p = start; p < end; p++) {
            char c = json.charAt(p);
            if (c == '\\' && p + 1 < end) {
                char n = json.charAt(++p);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static byte[] generateSecret() {
        byte[] secret = new byte[32];
        new java.security.SecureRandom().nextBytes(secret);
        return secret;
    }
}
