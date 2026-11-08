package io.fyeo.core;

import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.EncryptedImagePayload;
import io.fyeo.core.model.MetadataEnvelope;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * F.Y.E.O (For Your Eyes Only) — Core Cryptographic Vault Facade
 * 
 * Part of the Java for the Street (JFS) ecosystem.
 * Enterprise Code. Grassroots Soul.
 * 
 * Direct, zero-framework, developer-friendly facade to encrypt, cloak, and inspect
 * documents using Visual AEAD and Chameleon Stego-AEAD.
 * 
 * @author Adageje Moshood (Java for the Street)
 */
public class FyeoVault {

    public static final String DEFAULT_MASTER_KEY = "JFS_FYEO_DEFAULT_KEY#2026";
    public static final int DEFAULT_MAX_PIXELS = 500_000;
    public static final String DEFAULT_WATERMARK_TEMPLATE = "CONFIDENTIAL • %username% • %timestamp% • Java for the Street 🛣️☕️";

    private final String masterKey;
    private final int maxPixels;
    private final String watermarkTemplate;

    public FyeoVault() {
        this(DEFAULT_MASTER_KEY, DEFAULT_MAX_PIXELS, DEFAULT_WATERMARK_TEMPLATE);
    }

    public FyeoVault(String masterKey) {
        this(masterKey, DEFAULT_MAX_PIXELS, DEFAULT_WATERMARK_TEMPLATE);
    }

    public FyeoVault(String masterKey, int maxPixels) {
        this(masterKey, maxPixels, DEFAULT_WATERMARK_TEMPLATE);
    }

    public FyeoVault(String masterKey, int maxPixels, String watermarkTemplate) {
        this.masterKey = (masterKey != null && !masterKey.isBlank()) ? masterKey : DEFAULT_MASTER_KEY;
        this.maxPixels = (maxPixels > 0) ? maxPixels : DEFAULT_MAX_PIXELS;
        this.watermarkTemplate = (watermarkTemplate != null && !watermarkTemplate.isBlank())
                ? watermarkTemplate
                : DEFAULT_WATERMARK_TEMPLATE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Cloaks a MetadataEnvelope containing document bytes and metadata into an innocent PNG carrier.
     */
    public ChameleonPayload cloakDocument(MetadataEnvelope envelope, byte[] customCarrierBytes) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        return ChameleonDecoyEngine.embedEnvelope(envelope, customCarrierBytes, this.masterKey);
    }

    /**
     * Cloaks raw document bytes into an innocent PNG carrier.
     */
    public ChameleonPayload cloakDocument(byte[] rawBytes, byte[] customCarrierBytes) {
        Objects.requireNonNull(rawBytes, "rawBytes cannot be null");
        return ChameleonDecoyEngine.embed(rawBytes, customCarrierBytes, this.masterKey);
    }

    /**
     * Uncloaks a MetadataEnvelope strictly in volatile RAM.
     */
    public MetadataEnvelope uncloakDocument(byte[] carrierBytes, String optionalKeyOverride) {
        Objects.requireNonNull(carrierBytes, "carrierBytes cannot be null");
        String key = (optionalKeyOverride != null && !optionalKeyOverride.isBlank()) ? optionalKeyOverride : this.masterKey;
        return ChameleonDecoyEngine.extractEnvelope(carrierBytes, key);
    }

    /**
     * Uncloaks raw document bytes strictly in volatile RAM.
     */
    public byte[] uncloakRawBytes(byte[] carrierBytes, String optionalKeyOverride) {
        Objects.requireNonNull(carrierBytes, "carrierBytes cannot be null");
        String key = (optionalKeyOverride != null && !optionalKeyOverride.isBlank()) ? optionalKeyOverride : this.masterKey;
        return ChameleonDecoyEngine.extract(carrierBytes, key);
    }

    /**
     * Scrambles document bytes into pure Visual AEAD static noise.
     */
    public EncryptedImagePayload protectDocument(byte[] rawBytes) {
        Objects.requireNonNull(rawBytes, "rawBytes cannot be null");
        return VisualAeadEngine.encrypt(rawBytes, this.masterKey, this.maxPixels);
    }

    /**
     * Restores Visual AEAD static noise back into original document bytes strictly in RAM.
     */
    public byte[] restoreDocument(byte[] scrambledBytes, byte[] hmacTag, byte[] salt) {
        Objects.requireNonNull(scrambledBytes, "scrambledBytes cannot be null");
        Objects.requireNonNull(hmacTag, "hmacTag cannot be null");
        Objects.requireNonNull(salt, "salt cannot be null");
        return VisualAeadEngine.decrypt(scrambledBytes, this.masterKey, hmacTag, salt);
    }

    /**
     * Formats a dynamic security watermark string for ephemeral viewports.
     */
    public String renderWatermark(String username, String documentId) {
        String user = (username != null && !username.isBlank()) ? username : "ANONYMOUS_REVIEWER";
        String doc = (documentId != null && !documentId.isBlank()) ? documentId : "UNKNOWN_DOC";
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        return this.watermarkTemplate
                .replace("%username%", user)
                .replace("%documentId%", doc)
                .replace("%timestamp%", timestamp);
    }

    public String getMasterKey() {
        return masterKey;
    }

    public int getMaxPixels() {
        return maxPixels;
    }

    public String getWatermarkTemplate() {
        return watermarkTemplate;
    }

    public static class Builder {
        private String masterKey = DEFAULT_MASTER_KEY;
        private int maxPixels = DEFAULT_MAX_PIXELS;
        private String watermarkTemplate = DEFAULT_WATERMARK_TEMPLATE;

        public Builder masterKey(String masterKey) {
            this.masterKey = masterKey;
            return this;
        }

        public Builder maxPixels(int maxPixels) {
            this.maxPixels = maxPixels;
            return this;
        }

        public Builder watermarkTemplate(String watermarkTemplate) {
            this.watermarkTemplate = watermarkTemplate;
            return this;
        }

        public FyeoVault build() {
            return new FyeoVault(masterKey, maxPixels, watermarkTemplate);
        }
    }
}
