package io.fyeo.core.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable carrier container holding an innocuous visual decoy with
 * cryptographically embedded Authenticated Steganographic data.
 */
public final class ChameleonPayload implements Serializable {

    private static final long serialVersionUID = 3000L;

    private final byte[] carrierBytes;
    private final int width;
    private final int height;
    private final int payloadBytesCount;
    private final String decoyDescription;

    public ChameleonPayload(byte[] carrierBytes, int width, int height, int payloadBytesCount, String decoyDescription) {
        this.carrierBytes = Objects.requireNonNull(carrierBytes, "carrierBytes cannot be null").clone();
        this.width = width;
        this.height = height;
        this.payloadBytesCount = payloadBytesCount;
        this.decoyDescription = decoyDescription != null ? decoyDescription : "Generic Brand Decoy";
    }

    public byte[] getCarrierBytes() {
        return carrierBytes.clone();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPayloadBytesCount() {
        return payloadBytesCount;
    }

    public String getDecoyDescription() {
        return decoyDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChameleonPayload that = (ChameleonPayload) o;
        return width == that.width &&
               height == that.height &&
               payloadBytesCount == that.payloadBytesCount &&
               Arrays.equals(carrierBytes, that.carrierBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(width, height, payloadBytesCount);
        result = 31 * result + Arrays.hashCode(carrierBytes);
        return result;
    }

    @Override
    public String toString() {
        return String.format("ChameleonPayload[size=%d bytes, dims=%dx%d, payload=%d bytes, desc='%s']",
                carrierBytes.length, width, height, payloadBytesCount, decoyDescription);
    }
}
