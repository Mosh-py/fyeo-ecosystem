package io.fyeo.core.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value object holding the output of a Visual AEAD encryption operation.
 */
public final class EncryptedImagePayload {
    private final byte[] imageBytes;
    private final String mimeType;
    private final int width;
    private final int height;
    private final byte[] hmacTag;
    private final byte[] salt;
    private final byte[] envelopeCiphertext; // Optional for enterprise KMS

    public EncryptedImagePayload(byte[] imageBytes, String mimeType, int width, int height,
                                 byte[] hmacTag, byte[] salt, byte[] envelopeCiphertext) {
        this.imageBytes = Objects.requireNonNull(imageBytes, "imageBytes cannot be null").clone();
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType cannot be null");
        this.width = width;
        this.height = height;
        this.hmacTag = Objects.requireNonNull(hmacTag, "hmacTag cannot be null").clone();
        this.salt = Objects.requireNonNull(salt, "salt cannot be null").clone();
        this.envelopeCiphertext = envelopeCiphertext != null ? envelopeCiphertext.clone() : null;
    }

    public byte[] getImageBytes() {
        return imageBytes.clone();
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getHmacTag() {
        return hmacTag.clone();
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public byte[] getEnvelopeCiphertext() {
        return envelopeCiphertext != null ? envelopeCiphertext.clone() : null;
    }

    public boolean hasEnvelope() {
        return envelopeCiphertext != null && envelopeCiphertext.length > 0;
    }
}
