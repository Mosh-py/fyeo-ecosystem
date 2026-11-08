package io.fyeo.core.exception;

/**
 * General cryptographic failure exception for key derivation, formatting, or parsing errors.
 */
public class CryptographicException extends RuntimeException {
    public CryptographicException(String message) {
        super(message);
    }

    public CryptographicException(String message, Throwable cause) {
        super(message, cause);
    }
}
