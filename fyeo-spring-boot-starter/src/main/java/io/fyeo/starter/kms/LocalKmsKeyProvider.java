package io.fyeo.starter.kms;

import io.fyeo.starter.properties.FyeoProperties;

/**
 * Standard local development KMS provider.
 */
public class LocalKmsKeyProvider implements KmsKeyProvider {

    private final FyeoProperties properties;

    public LocalKmsKeyProvider(FyeoProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getActiveEncryptionKey() {
        return properties.getMasterKey();
    }

    @Override
    public String getProviderName() {
        return "LOCAL-PBKDF2-KMS";
    }
}
