package io.fyeo.core;

import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.EncryptedImagePayload;
import io.fyeo.core.model.MetadataEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FyeoVault Facade & Configurability Tests")
class FyeoVaultTest {

    @Test
    @DisplayName("Default constructor loads sensible defaults")
    void testDefaults() {
        FyeoVault vault = new FyeoVault();
        assertEquals(FyeoVault.DEFAULT_MASTER_KEY, vault.getMasterKey());
        assertEquals(FyeoVault.DEFAULT_MAX_PIXELS, vault.getMaxPixels());
        assertTrue(vault.getWatermarkTemplate().contains("Java for the Street"));
    }

    @Test
    @DisplayName("Custom builder configures properties cleanly")
    void testCustomBuilder() {
        FyeoVault vault = FyeoVault.builder()
                .masterKey("CustomBankKey#99")
                .maxPixels(750_000)
                .watermarkTemplate("AUDIT • %username% • %documentId%")
                .build();

        assertEquals("CustomBankKey#99", vault.getMasterKey());
        assertEquals(750_000, vault.getMaxPixels());
        assertEquals("AUDIT • %username% • %documentId%", vault.getWatermarkTemplate());

        String rendered = vault.renderWatermark("adebayo", "DOC-001");
        assertEquals("AUDIT • adebayo • DOC-001", rendered);
    }

    @Test
    @DisplayName("Full lifecycle cloak and uncloak with default vault")
    void testCloakAndUncloakLifecycle() {
        FyeoVault vault = new FyeoVault();

        byte[] secretPdf = "CONFIDENTIAL CAC CERTIFICATE FOR YOUR EYES ONLY".getBytes(StandardCharsets.UTF_8);
        MetadataEnvelope envelope = new MetadataEnvelope(
                "cac.pdf",
                "Mosh Technologies",
                "CAC_CERTIFICATE",
                System.currentTimeMillis(),
                secretPdf
        );

        // Cloak
        ChameleonPayload payload = vault.cloakDocument(envelope, null);
        assertNotNull(payload);
        assertNotNull(payload.getCarrierBytes());

        // Uncloak
        MetadataEnvelope uncloaked = vault.uncloakDocument(payload.getCarrierBytes(), null);
        assertNotNull(uncloaked);
        assertEquals("cac.pdf", uncloaked.getOriginalFilename());
        assertEquals("Mosh Technologies", uncloaked.getCustomerName());
        assertArrayEquals(secretPdf, uncloaked.getImageBytes());
    }

    @Test
    @DisplayName("Visual AEAD noise protect and restore with vault")
    void testProtectAndRestoreNoise() throws Exception {
        FyeoVault vault = new FyeoVault();
        
        // Generate a valid PNG image
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "PNG", baos);
        byte[] docBytes = baos.toByteArray();

        EncryptedImagePayload payload = vault.protectDocument(docBytes);
        assertNotNull(payload);

        byte[] restored = vault.restoreDocument(payload.getImageBytes(), payload.getHmacTag(), payload.getSalt());
        assertArrayEquals(docBytes, restored);
    }
}
