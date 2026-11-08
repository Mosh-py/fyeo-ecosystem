package io.fyeo.core.model;

import io.fyeo.core.exception.CryptographicException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * F.Y.E.O (For Your Eyes Only) — Zero-Yield Visual AEAD Vault
 * 
 * Part of the Java for the Street (JFS) ecosystem.
 * Enterprise Code. Grassroots Soul.
 * 
 * Inner Cryptographic Envelope encapsulating document metadata (filename, customer PII,
 * document category, timestamps) together with raw image bytes.
 * Guarantees zero metadata leakage when serialized and passed to AEAD engines.
 * 
 * @author Adageje Moshood (Java for the Street)
 */
public final class MetadataEnvelope implements Serializable {

    private static final long serialVersionUID = 3100L;
    public static final int ENVELOPE_MAGIC = 0x4A465321; // "JFS!" in ASCII hex - Java for the Street
    public static final int ENVELOPE_MAGIC_LEGACY = 0x46454E56; // "FENV" for backward compatibility
    public static final String JFS_SIGNATURE = "Java for the Street. 🛣️☕️";

    private final String originalFilename;
    private final String customerName;
    private final String documentType;
    private final long uploadTimestamp;
    private final byte[] imageBytes;

    public MetadataEnvelope(String originalFilename, String customerName, String documentType, long uploadTimestamp, byte[] imageBytes) {
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        this.customerName = customerName != null ? customerName : "ANONYMOUS";
        this.documentType = documentType != null ? documentType : "UNCLASSIFIED";
        this.uploadTimestamp = uploadTimestamp > 0 ? uploadTimestamp : System.currentTimeMillis();
        this.imageBytes = Objects.requireNonNull(imageBytes, "imageBytes cannot be null").clone();
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public long getUploadTimestamp() {
        return uploadTimestamp;
    }

    public byte[] getImageBytes() {
        return imageBytes.clone();
    }

    /**
     * Serializes this envelope into a compact binary byte array.
     */
    public byte[] serialize() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            
            dos.writeInt(ENVELOPE_MAGIC);
            
            byte[] fileBytes = originalFilename.getBytes(StandardCharsets.UTF_8);
            dos.writeShort(fileBytes.length);
            dos.write(fileBytes);

            byte[] nameBytes = customerName.getBytes(StandardCharsets.UTF_8);
            dos.writeShort(nameBytes.length);
            dos.write(nameBytes);

            byte[] typeBytes = documentType.getBytes(StandardCharsets.UTF_8);
            dos.writeShort(typeBytes.length);
            dos.write(typeBytes);

            dos.writeLong(uploadTimestamp);

            dos.writeInt(imageBytes.length);
            dos.write(imageBytes);

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CryptographicException("Failed to serialize metadata envelope", e);
        }
    }

    /**
     * Deserializes a binary byte array back into a MetadataEnvelope.
     */
    public static MetadataEnvelope deserialize(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {

            int magic = dis.readInt();
            if (magic != ENVELOPE_MAGIC && magic != ENVELOPE_MAGIC_LEGACY) {
                // Backward-compatibility: If data does not have envelope header, treat as raw image bytes
                return new MetadataEnvelope("unknown_document.png", "UNKNOWN", "LEGACY", System.currentTimeMillis(), data);
            }

            int fileLen = dis.readUnsignedShort();
            byte[] fileBytes = new byte[fileLen];
            dis.readFully(fileBytes);
            String filename = new String(fileBytes, StandardCharsets.UTF_8);

            int nameLen = dis.readUnsignedShort();
            byte[] nameBytes = new byte[nameLen];
            dis.readFully(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);

            int typeLen = dis.readUnsignedShort();
            byte[] typeBytes = new byte[typeLen];
            dis.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.UTF_8);

            long timestamp = dis.readLong();

            int imgLen = dis.readInt();
            byte[] imgBytes = new byte[imgLen];
            dis.readFully(imgBytes);

            return new MetadataEnvelope(filename, name, type, timestamp, imgBytes);
        } catch (IOException e) {
            throw new CryptographicException("Failed to deserialize metadata envelope", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataEnvelope that = (MetadataEnvelope) o;
        return uploadTimestamp == that.uploadTimestamp &&
               originalFilename.equals(that.originalFilename) &&
               customerName.equals(that.customerName) &&
               documentType.equals(that.documentType) &&
               Arrays.equals(imageBytes, that.imageBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(originalFilename, customerName, documentType, uploadTimestamp);
        result = 31 * result + Arrays.hashCode(imageBytes);
        return result;
    }
}
