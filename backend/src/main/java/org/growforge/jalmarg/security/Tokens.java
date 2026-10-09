package org.growforge.jalmarg.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random secrets and their hashes. */
public final class Tokens {
    private Tokens() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 256-bit random bearer token, URL-safe. */
    public static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** 6-digit code from a cryptographically secure generator (000000–999999). */
    public static String newOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
