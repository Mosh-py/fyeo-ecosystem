package io.fyeo.core.exception;

/**
 * Thrown when an image payload fails HMAC-SHA256 authentication or has been tampered with.
 */
public class TamperDetectedException extends RuntimeException {
    public TamperDetectedException(String message) {
        super(message);
    }

    public TamperDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
