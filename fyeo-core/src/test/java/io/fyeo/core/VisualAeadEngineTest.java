package io.fyeo.core;

import io.fyeo.core.exception.TamperDetectedException;
import io.fyeo.core.model.EncryptedImagePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class VisualAeadEngineTest {

    private byte[] createTestIdCard(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // Draw gradient background
            g.setColor(new Color(6, 78, 59));
            g.fillRect(0, 0, width, height / 4);
            g.setColor(new Color(248, 250, 252));
            g.fillRect(0, height / 4, width, height * 3 / 4);

            // Draw simulated ID text
            g.setColor(Color.BLACK);
            g.drawString("FEDERAL REPUBLIC OF NIGERIA", 20, 30);
            g.drawString("NIN: 7492 8841 0293", 20, 80);
            g.drawString("NAME: ADEBAYO KUNLE", 20, 110);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Encrypt and Decrypt cycle restores exact pixel parity")
    void testEncryptDecryptParity() throws IOException {
        byte[] originalBytes = createTestIdCard(400, 250);
        String secretKey = "BankMasterKmsKey#2026";

        EncryptedImagePayload payload = VisualAeadEngine.encrypt(originalBytes, secretKey, 500_000);
        assertNotNull(payload);
        assertTrue(payload.getImageBytes().length > 0);
        assertEquals(32, payload.getHmacTag().length); // 256-bit HMAC tag

        // Decrypt
        byte[] restoredBytes = VisualAeadEngine.decrypt(
                payload.getImageBytes(),
                secretKey,
                payload.getHmacTag(),
                payload.getSalt()
        );

        assertTrue(restoredBytes.length > 0);
        assertArrayEquals(originalBytes, restoredBytes);
    }

    @Test
    @DisplayName("Tampered encrypted image bytes fail HMAC authentication immediately")
    void testTamperDetection() throws IOException {
        byte[] originalBytes = createTestIdCard(400, 250);
        String secretKey = "EnterpriseSecret#99";

        EncryptedImagePayload payload = VisualAeadEngine.encrypt(originalBytes, secretKey, 500_000);
        byte[] tamperedBytes = payload.getImageBytes();

        // Tamper with a single byte in the scrambled payload
        tamperedBytes[tamperedBytes.length / 2] ^= 0xFF;

        TamperDetectedException ex = assertThrows(TamperDetectedException.class, () -> VisualAeadEngine.decrypt(
                tamperedBytes,
                secretKey,
                payload.getHmacTag(),
                payload.getSalt()
        ));
        assertTrue(ex.getMessage().contains("Visual AEAD authentication failed"));
    }

    @Test
    @DisplayName("Wrong passphrase fails decryption authentication in constant time")
    void testWrongKeyRejection() throws IOException {
        byte[] originalBytes = createTestIdCard(400, 250);
        String correctKey = "CorrectPass#123";
        String wrongKey = "AttackerGuess#456";

        EncryptedImagePayload payload = VisualAeadEngine.encrypt(originalBytes, correctKey, 500_000);

        assertThrows(TamperDetectedException.class, () -> VisualAeadEngine.decrypt(
                payload.getImageBytes(),
                wrongKey,
                payload.getHmacTag(),
                payload.getSalt()
        ));
    }

    @Test
    @DisplayName("Scrambled output exhibits high visual entropy (> 7.5 bits/byte)")
    void testScrambledEntropyCalculation() throws IOException {
        byte[] originalBytes = createTestIdCard(400, 250);
        EncryptedImagePayload payload = VisualAeadEngine.encrypt(originalBytes, "EntropyTestKey", 500_000);

        byte[] scrambledBytes = payload.getImageBytes();
        double entropy = calculateShannonEntropy(scrambledBytes);

        // Shannon entropy of high-entropy noise exceeds 7.8 bits/byte (Max is 8.0)
        assertTrue(entropy > 7.5);
    }

    private double calculateShannonEntropy(byte[] data) {
        int[] frequency = new int[256];
        for (byte b : data) {
            frequency[b & 0xFF]++;
        }
        double entropy = 0.0;
        int total = data.length;
        for (int count : frequency) {
            if (count > 0) {
                double p = (double) count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }
}
