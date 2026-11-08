package io.jfs.playpen.service;

import io.fyeo.core.FyeoVault;
import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.MetadataEnvelope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * FileEncryptorService
 * Demonstrates singleton FyeoVault usage for cloaking and restoring documents.
 *
 * Java for the Street. 🛣️☕️
 */
public class FileEncryptorService {

    // Lazy, thread-safe JVM ClassLoader singleton holder
    private static class FyeoVaultHolder {
        private static final FyeoVault INSTANCE = new FyeoVault();
    }

    private final FyeoVault fyeoVault;

    public FileEncryptorService() {
        this.fyeoVault = FyeoVaultHolder.INSTANCE;
    }

    public FileEncryptorService(FyeoVault fyeoVault) {
        this.fyeoVault = Objects.requireNonNull(fyeoVault, "fyeoVault cannot be null");
    }

    public FyeoVault getFyeoVault() {
        return fyeoVault;
    }

    /**
     * Cloaks a file from disk into an innocent PNG carrier asset.
     */
    public ChameleonPayload encryptFile(Path inputFilePath, Path outputPngPath, String customerName, String docType) throws IOException {
        byte[] rawBytes = Files.readAllBytes(inputFilePath);
        String filename = inputFilePath.getFileName().toString();

        MetadataEnvelope envelope = new MetadataEnvelope(
                filename,
                customerName != null ? customerName : "Chairman-Adageje",
                docType != null ? docType : "CONFIDENTIAL_DOC",
                System.currentTimeMillis(),
                rawBytes
        );

        ChameleonPayload payload = fyeoVault.cloakDocument(envelope, null);

        if (outputPngPath.getParent() != null) {
            Files.createDirectories(outputPngPath.getParent());
        }
        Files.write(outputPngPath, payload.getCarrierBytes());

        return payload;
    }

    /**
     * Uncloaks a file from an innocent PNG carrier strictly into volatile RAM.
     */
    public MetadataEnvelope decryptFile(Path cloakedPngPath) throws IOException {
        byte[] carrierBytes = Files.readAllBytes(cloakedPngPath);
        return fyeoVault.uncloakDocument(carrierBytes, null);
    }
}
