package io.fyeo.core;

import io.fyeo.core.exception.CryptographicException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Key Cloaker Utility.
 * 
 * Anonymizes physical object keys and filenames to prevent S3 bucket enumeration
 * and metadata leakage attacks.
 */
public final class KeyCloaker {

    private static final String HMAC_ALGO = "HmacSHA256";

    private KeyCloaker() {}

    /**
     * Generates a zero-knowledge, cryptographically opaque object key.
     * 
     * Output format: `obj_[16-char-hmac-hex].png`
     */
    public static String generateOpaqueKey(String originalFilename, String masterKey) {
        Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        Objects.requireNonNull(masterKey, "masterKey cannot be null");

        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hmac = mac.doFinal(originalFilename.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder("obj_");
            for (int i = 0; i < 8; i++) { // 16 hex characters
                sb.append(String.format("%02x", hmac[i]));
            }
            sb.append(".png");
            return sb.toString();
        } catch (Exception e) {
            throw new CryptographicException("Failed to generate opaque key", e);
        }
    }

    /**
     * Generates an innocent decoy asset key (e.g. system brand badge or UI icon).
     */
    public static String generateChameleonDecoyKey(int sequence) {
        String[] templates = {
            "asset_theme_badge_emerald_v3.png",
            "ui_nav_compliance_placeholder.png",
            "static_media_brand_vector_02.png",
            "assets_icons_verified_shield_dark.png"
        };
        int idx = Math.abs(sequence) % templates.length;
        return templates[idx];
    }
}
