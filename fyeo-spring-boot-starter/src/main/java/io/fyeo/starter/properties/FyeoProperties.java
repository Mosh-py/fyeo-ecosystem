package io.fyeo.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Enterprise Configuration Properties for F.Y.E.O Vault.
 * Provides complete enterprise policy sovereignty with secure defaults.
 *
 * Java for the Street. 🛣️☕️
 */
@ConfigurationProperties(prefix = "fyeo.vault")
public class FyeoProperties {

    /**
     * Whether the F.Y.E.O visual vault engine is enabled.
     */
    private boolean enabled = true;

    /**
     * Master encryption key / secret for local/dev environments.
     * In production, this can be hooked into an AWS KMS key ID or HSM ARN.
     */
    private String masterKey = "FyeoDefaultMasterKey#2026";

    /**
     * Maximum pixel count per document to prevent heap memory exhaustion.
     */
    private int maxPixels = 500_000;

    /**
     * KMS mode: "local" (AES-256 local derivation) or "aws-kms".
     */
    private String kmsMode = "local";

    /**
     * Duration in seconds for compliance reviewer ephemeral view grants.
     * Default: 15 seconds (configurable between 5s and 300s).
     */
    private int ephemeralViewDurationSeconds = 15;

    /**
     * Configurable Chameleon Stego-AEAD Decoy settings.
     */
    private ChameleonProperties chameleon = new ChameleonProperties();

    /**
     * Configurable forensic watermarking & anti-exfiltration settings.
     */
    private WatermarkProperties watermark = new WatermarkProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public int getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(int maxPixels) {
        this.maxPixels = maxPixels;
    }

    public String getKmsMode() {
        return kmsMode;
    }

    public void setKmsMode(String kmsMode) {
        this.kmsMode = kmsMode;
    }

    public int getEphemeralViewDurationSeconds() {
        return ephemeralViewDurationSeconds;
    }

    public void setEphemeralViewDurationSeconds(int ephemeralViewDurationSeconds) {
        this.ephemeralViewDurationSeconds = Math.max(5, Math.min(ephemeralViewDurationSeconds, 300));
    }

    public ChameleonProperties getChameleon() {
        return chameleon;
    }

    public void setChameleon(ChameleonProperties chameleon) {
        this.chameleon = chameleon;
    }

    public WatermarkProperties getWatermark() {
        return watermark;
    }

    public void setWatermark(WatermarkProperties watermark) {
        this.watermark = watermark;
    }

    public static class ChameleonProperties {
        private String defaultDescription = "Corporate Navigation Asset";
        private String carrierDirectory;
        private int defaultWidth = 800;
        private int defaultHeight = 600;

        public String getDefaultDescription() {
            return defaultDescription;
        }

        public void setDefaultDescription(String defaultDescription) {
            this.defaultDescription = defaultDescription;
        }

        public String getCarrierDirectory() {
            return carrierDirectory;
        }

        public void setCarrierDirectory(String carrierDirectory) {
            this.carrierDirectory = carrierDirectory;
        }

        public int getDefaultWidth() {
            return defaultWidth;
        }

        public void setDefaultWidth(int defaultWidth) {
            this.defaultWidth = defaultWidth;
        }

        public int getDefaultHeight() {
            return defaultHeight;
        }

        public void setDefaultHeight(int defaultHeight) {
            this.defaultHeight = defaultHeight;
        }
    }

    public static class WatermarkProperties {
        private String template = "CONFIDENTIAL • %username% • %timestamp% • Java for the Street 🛣️☕️";
        private String density = "CENTER";
        private String color = "rgba(255, 255, 255, 0.25)";
        private boolean allowPrint = false;

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public String getDensity() {
            return density;
        }

        public void setDensity(String density) {
            this.density = density;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public boolean isAllowPrint() {
            return allowPrint;
        }

        public void setAllowPrint(boolean allowPrint) {
            this.allowPrint = allowPrint;
        }
    }
}
