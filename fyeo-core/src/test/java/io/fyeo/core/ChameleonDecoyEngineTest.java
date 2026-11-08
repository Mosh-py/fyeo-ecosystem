package io.fyeo.core;

import io.fyeo.core.exception.TamperDetectedException;
import io.fyeo.core.model.ChameleonPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chameleon Stego-AEAD Decoy Engine Tests")
class ChameleonDecoyEngineTest {

    private static final String MASTER_KEY = "enterprise-kms-root-key-super-secret-99182";

    private byte[] createSampleKycDocument(String idText) throws IOException {
        BufferedImage img = new BufferedImage(300, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(241, 245, 249));
        g.fillRect(0, 0, 300, 180);

        g.setColor(new Color(15, 23, 42));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("FEDERAL REPUBLIC OF NIGERIA", 20, 30);
        g.drawString("NATIONAL IDENTITY MANAGEMENT", 20, 55);
        g.drawString(idText, 20, 95);
        g.drawString("NAME: ADEKUNLE FATIMA", 20, 125);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should successfully embed KYC document into generated decoy and restore exact byte parity")
    void testEmbedAndExtractWithGeneratedDecoy() throws IOException {
        byte[] originalKyc = createSampleKycDocument("NIN: 9021-4829-1092");

        // 1. Embed inside Chameleon Decoy (Auto-Generated Corporate Card)
        ChameleonPayload payload = ChameleonDecoyEngine.embed(originalKyc, null, MASTER_KEY);
        assertNotNull(payload.getCarrierBytes());
        assertTrue(payload.getCarrierBytes().length > 1000);
        assertTrue(payload.getWidth() >= 640);
        assertTrue(payload.getHeight() >= 400);

        // 2. Extract and assert exact byte parity
        byte[] restoredKyc = ChameleonDecoyEngine.extract(payload.getCarrierBytes(), MASTER_KEY);
        assertArrayEquals(originalKyc, restoredKyc, "Restored KYC document must have 100% exact byte parity");
    }

    @Test
    @DisplayName("Should embed inside a custom corporate logo carrier and recover cleanly")
    void testEmbedWithCustomCarrier() throws IOException {
        byte[] originalKyc = createSampleKycDocument("PASSPORT: A10293847");

        // Create a custom 650x450 carrier image (e.g. Corporate brand asset)
        BufferedImage customLogo = new BufferedImage(650, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = customLogo.createGraphics();
        g.setColor(new Color(4, 120, 87));
        g.fillRect(0, 0, 650, 450);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        g.drawString("ENTERPRISE PAYMENT PLATFORM ASSET", 80, 200);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(customLogo, "PNG", baos);
        byte[] carrierBytes = baos.toByteArray();

        ChameleonPayload payload = ChameleonDecoyEngine.embed(originalKyc, carrierBytes, MASTER_KEY);
        assertEquals(650, payload.getWidth());
        assertEquals(450, payload.getHeight());

        byte[] restoredKyc = ChameleonDecoyEngine.extract(payload.getCarrierBytes(), MASTER_KEY);
        assertArrayEquals(originalKyc, restoredKyc);
    }

    @Test
    @DisplayName("Should reject extraction with TamperDetectedException when wrong password is supplied")
    void testWrongKeyRejection() throws IOException {
        byte[] originalKyc = createSampleKycDocument("NIN: 1111-2222-3333");
        ChameleonPayload payload = ChameleonDecoyEngine.embed(originalKyc, null, MASTER_KEY);

        assertThrows(TamperDetectedException.class, () -> {
            ChameleonDecoyEngine.extract(payload.getCarrierBytes(), "incorrect-wrong-password-999");
        });
    }

    @Test
    @DisplayName("Should detect file-level corruption and throw TamperDetectedException")
    void testCarrierFileCorruptionTamperDetection() throws IOException {
        byte[] originalKyc = createSampleKycDocument("CAC: RC-893019");
        ChameleonPayload payload = ChameleonDecoyEngine.embed(originalKyc, null, MASTER_KEY);

        byte[] tamperedCarrier = payload.getCarrierBytes().clone();
        int tamperIdx = tamperedCarrier.length / 2;
        tamperedCarrier[tamperIdx] ^= 0x55;

        assertThrows(TamperDetectedException.class, () -> {
            ChameleonDecoyEngine.extract(tamperedCarrier, MASTER_KEY);
        });
    }

    @Test
    @DisplayName("Should detect pixel-level payload tampering in valid carrier and throw TamperDetectedException")
    void testCarrierPixelTamperDetection() throws IOException {
        byte[] originalKyc = createSampleKycDocument("UTILITY: NEPA-29482");
        ChameleonPayload payload = ChameleonDecoyEngine.embed(originalKyc, null, MASTER_KEY);

        // Read carrier as valid BufferedImage
        BufferedImage carrier = ImageIO.read(new ByteArrayInputStream(payload.getCarrierBytes()));
        // Invert top pixels containing the embedded header pool
        for (int x = 0; x < 50; x++) {
            int originalRgb = carrier.getRGB(x, 0);
            carrier.setRGB(x, 0, originalRgb ^ 0x00FFFFFF);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(carrier, "PNG", baos);
        byte[] pixelTamperedBytes = baos.toByteArray();

        assertThrows(TamperDetectedException.class, () -> {
            ChameleonDecoyEngine.extract(pixelTamperedBytes, MASTER_KEY);
        });
    }

    @Test
    @DisplayName("Should encapsulate filename and PII in envelope and cloak external object key")
    void testMetadataEnvelopeCloakingAndRestoration() throws IOException {
        byte[] originalKyc = createSampleKycDocument("NIN: 5544-3322-1100");
        String originalFilename = "doc_001_NIN_Adeyemi.png";
        String customer = "Adeyemi Babatunde";
        String docType = "NATIONAL_IDENTITY";

        // 1. Cloak external S3 key
        String opaqueKey = KeyCloaker.generateOpaqueKey(originalFilename, MASTER_KEY);
        assertTrue(opaqueKey.startsWith("obj_"));
        assertFalse(opaqueKey.contains("Adeyemi"));
        assertFalse(opaqueKey.contains("NIN"));

        String chameleonKey = KeyCloaker.generateChameleonDecoyKey(1);
        assertTrue(chameleonKey.endsWith(".png"));
        assertFalse(chameleonKey.contains("Adeyemi"));

        // 2. Encapsulate into inner envelope
        io.fyeo.core.model.MetadataEnvelope envelope = new io.fyeo.core.model.MetadataEnvelope(
                originalFilename, customer, docType, System.currentTimeMillis(), originalKyc);

        // 3. Embed envelope into Chameleon Decoy
        ChameleonPayload payload = ChameleonDecoyEngine.embedEnvelope(envelope, null, MASTER_KEY);

        // 4. Extract envelope and assert complete metadata and image restoration
        io.fyeo.core.model.MetadataEnvelope restored = ChameleonDecoyEngine.extractEnvelope(payload.getCarrierBytes(), MASTER_KEY);

        assertEquals(originalFilename, restored.getOriginalFilename());
        assertEquals(customer, restored.getCustomerName());
        assertEquals(docType, restored.getDocumentType());
        assertArrayEquals(originalKyc, restored.getImageBytes());
    }
}
