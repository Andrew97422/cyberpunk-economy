package ru.andrew.accessservice.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic lookup hash for PINs. We need O(1) index lookup by raw PIN
 * (so PIN-only login works), but we don't want to store the raw value, so
 * we hash with SHA-256 over the trimmed PIN. The cryptographic verification
 * is still done with BCrypt on top — this is just an indexable handle.
 */
@Component
public class PinHasher {

    public String lookupHash(String rawPin) {
        if (rawPin == null) {
            throw new IllegalArgumentException("rawPin is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawPin.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
