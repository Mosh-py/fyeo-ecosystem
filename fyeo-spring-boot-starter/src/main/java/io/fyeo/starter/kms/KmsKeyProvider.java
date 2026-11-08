package io.fyeo.starter.kms;

/**
 * Key Management Service (KMS) provider abstraction for enterprise banks.
 */
public interface KmsKeyProvider {

    /**
     * Resolves the active master key or decrypts an envelope session key.
     */
    String getActiveEncryptionKey();

    /**
     * Identifies the key provider type (e.g. "LOCAL", "AWS-KMS", "HASHICORP-VAULT").
     */
    String getProviderName();
}
