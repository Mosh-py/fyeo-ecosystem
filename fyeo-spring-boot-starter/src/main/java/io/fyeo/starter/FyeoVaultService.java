package io.fyeo.starter;

import io.fyeo.core.ChameleonDecoyEngine;
import io.fyeo.core.KeyCloaker;
import io.fyeo.core.VisualAeadEngine;
import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.EncryptedImagePayload;
import io.fyeo.core.model.MetadataEnvelope;
import io.fyeo.starter.kms.KmsKeyProvider;
import io.fyeo.starter.properties.FyeoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

/**
 * F.Y.E.O (For Your Eyes Only) — Zero-Yield Visual AEAD Vault
 * 
 * Part of the Java for the Street (JFS) ecosystem.
 * Enterprise Code. Grassroots Soul.
 * 
 * Enterprise Service Interface for F.Y.E.O Document Vault operations.
 * Provides drop-in methods to protect incoming documents (CAC certificates, NINs, Passports)
 * and restore them in volatile RAM during authorized compliance inspections.
 * 
 * @author Adageje Moshood (Java for the Street)
 */
public class FyeoVaultService {

    private static final Logger log = LoggerFactory.getLogger(FyeoVaultService.class);

    private static final String JFS_BANNER = "\n" +
            "  ______ __   __ _____ ____  \n" +
            " |  ____|\\ \\ / /|  ___/ __ \\ \n" +
            " | |__    \\ V / | |__| |  | |\n" +
            " |  __|    \\ /  |  __| |  | |\n" +
            " | |       | |  | |__| |__| |\n" +
            " |_|       |_|  |_____\\____/ \n" +
            " :: F.Y.E.O Visual AEAD Engine (v3.0.2) ::\n" +
            " :: Java for the Street. 🛣️☕️ ::\n";

    private final FyeoProperties properties;
    private final KmsKeyProvider kmsKeyProvider;

    public FyeoVaultService(FyeoProperties properties, KmsKeyProvider kmsKeyProvider) {
        this.properties = properties;
        this.kmsKeyProvider = kmsKeyProvider;
        log.info(JFS_BANNER);
        log.info("Initialized F.Y.E.O Enterprise Vault [Java for the Street. 🛣️☕️] | KMS: {}", kmsKeyProvider.getProviderName());
    }

    /**
     * Ingests a raw plaintext document, scrambles it into Visual AEAD noise,
     * and returns the protected payload ready to be saved to disk or S3.
     */
    public EncryptedImagePayload protectDocument(byte[] rawDocumentBytes) {
        Objects.requireNonNull(rawDocumentBytes, "rawDocumentBytes cannot be null");
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();

        EncryptedImagePayload payload = VisualAeadEngine.encrypt(rawDocumentBytes, activeKey, properties.getMaxPixels());
        log.debug("Protected document: dimensions={}x{}, HMAC tag length={}", payload.getWidth(), payload.getHeight(), payload.getHmacTag().length);
        return payload;
    }

    /**
     * Decrypts and restores an encrypted visual static document strictly in RAM.
     * Guaranteed side-channel immune and constant-time verified.
     */
    public byte[] restoreDocument(byte[] scrambledBytes, byte[] hmacTag, byte[] salt) {
        Objects.requireNonNull(scrambledBytes, "scrambledBytes cannot be null");
        Objects.requireNonNull(hmacTag, "hmacTag cannot be null");
        Objects.requireNonNull(salt, "salt cannot be null");

        String activeKey = kmsKeyProvider.getActiveEncryptionKey();
        byte[] restoredBytes = VisualAeadEngine.decrypt(scrambledBytes, activeKey, hmacTag, salt);
        log.debug("Restored document in volatile RAM: size={} bytes", restoredBytes.length);
        return restoredBytes;
    }

    /**
     * Embeds a sensitive document into an innocuous carrier image (decoy),
     * completely concealing its existence from S3 buckets, filesystem indexes, and attackers.
     *
     * @param rawDocumentBytes   Plaintext document bytes (NIN, CAC, Passport)
     * @param customCarrierBytes Optional carrier image bytes (if null, an enterprise asset is generated)
     * @return ChameleonPayload containing the innocent carrier image ready to be stored
     */
    public ChameleonPayload protectDocumentWithChameleon(byte[] rawDocumentBytes, byte[] customCarrierBytes) {
        Objects.requireNonNull(rawDocumentBytes, "rawDocumentBytes cannot be null");
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();

        ChameleonPayload payload = ChameleonDecoyEngine.embed(rawDocumentBytes, customCarrierBytes, activeKey);
        log.debug("Protected document inside Chameleon Decoy: dims={}x{}, totalSize={} bytes, desc='{}'",
                payload.getWidth(), payload.getHeight(), payload.getCarrierBytes().length, payload.getDecoyDescription());
        return payload;
    }

    /**
     * Extracts and restores a sensitive document from a chameleon decoy image strictly in RAM.
     */
    public byte[] restoreDocumentFromChameleon(byte[] carrierBytes) {
        Objects.requireNonNull(carrierBytes, "carrierBytes cannot be null");
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();

        byte[] restoredBytes = ChameleonDecoyEngine.extract(carrierBytes, activeKey);
        log.debug("Restored document from Chameleon Decoy in volatile RAM: size={} bytes", restoredBytes.length);
        return restoredBytes;
    }

    /**
     * Embeds a metadata envelope containing both filename/PII and document bytes into an innocuous carrier,
     * simultaneously concealing both metadata and document contents.
     */
    public ChameleonPayload protectDocumentWithEnvelope(MetadataEnvelope envelope, byte[] customCarrierBytes) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();

        ChameleonPayload payload = ChameleonDecoyEngine.embedEnvelope(envelope, customCarrierBytes, activeKey);
        log.debug("Protected document with envelope: file='{}', customer='{}', dims={}x{}",
                envelope.getOriginalFilename(), envelope.getCustomerName(), payload.getWidth(), payload.getHeight());
        return payload;
    }

    /**
     * Extracts and restores the full metadata envelope from a chameleon carrier strictly in RAM.
     */
    public MetadataEnvelope restoreDocumentEnvelope(byte[] carrierBytes) {
        Objects.requireNonNull(carrierBytes, "carrierBytes cannot be null");
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();

        MetadataEnvelope envelope = ChameleonDecoyEngine.extractEnvelope(carrierBytes, activeKey);
        log.debug("Restored metadata envelope in volatile RAM: file='{}', customer='{}', size={} bytes",
                envelope.getOriginalFilename(), envelope.getCustomerName(), envelope.getImageBytes().length);
        return envelope;
    }

    /**
     * Generates a zero-knowledge opaque object key for static storage.
     */
    public String generateOpaqueKey(String originalFilename) {
        String activeKey = kmsKeyProvider.getActiveEncryptionKey();
        return KeyCloaker.generateOpaqueKey(originalFilename, activeKey);
    }

    /**
     * Generates a benign chameleon decoy key.
     */
    public String generateChameleonDecoyKey(int sequence) {
        return KeyCloaker.generateChameleonDecoyKey(sequence);
    }

    /**
     * Helper to package HMAC tag and Salt into a compact storage token string.
     */
    public String encodeStorageToken(byte[] hmacTag, byte[] salt) {
        String tagB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacTag);
        String saltB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
        return "FYEO:v3:" + tagB64 + ":" + saltB64;
    }

    /**
     * Helper to parse a compact storage token string back into HMAC tag and Salt.
     */
    public byte[][] decodeStorageToken(String token) {
        if (token == null || !token.startsWith("FYEO:v3:")) {
            throw new IllegalArgumentException("Invalid F.Y.E.O storage token format: " + token);
        }
        String[] parts = token.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Malformed F.Y.E.O storage token parts: " + token);
        }
        byte[] hmacTag = Base64.getUrlDecoder().decode(parts[2]);
        byte[] salt = Base64.getUrlDecoder().decode(parts[3]);
        return new byte[][]{hmacTag, salt};
    }

    /**
     * Renders a forensic watermark string according to configured enterprise template.
     */
    public String renderWatermark(String username, String documentId) {
        String user = (username != null && !username.isBlank()) ? username : "ANONYMOUS_REVIEWER";
        String doc = (documentId != null && !documentId.isBlank()) ? documentId : "UNKNOWN_DOC";
        String timestamp = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());

        String template = properties.getWatermark().getTemplate();
        if (template == null || template.isBlank()) {
            template = "CONFIDENTIAL • %username% • %timestamp% • Java for the Street 🛣️☕️";
        }

        return template
                .replace("%username%", user)
                .replace("%documentId%", doc)
                .replace("%timestamp%", timestamp);
    }

    /**
     * Gets the configured ephemeral RAM view duration in seconds for compliance inspections.
     */
    public int getEphemeralViewDurationSeconds() {
        return properties.getEphemeralViewDurationSeconds();
    }

    /**
     * Exposes the underlying enterprise properties bean.
     */
    public FyeoProperties getProperties() {
        return properties;
    }
}
