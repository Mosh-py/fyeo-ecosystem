package io.fyeo.starter;

import io.fyeo.core.model.EncryptedImagePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FyeoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FyeoAutoConfiguration.class));

    private byte[] createDummyDoc() throws IOException {
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.BLUE);
        g.drawString("CAC REGISTRATION CERTIFICATE", 20, 50);
        g.drawString("RC: 1982741", 20, 80);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Auto-configuration activates FyeoVaultService by default")
    void testAutoConfigurationActive() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FyeoVaultService.class);

            FyeoVaultService vaultService = context.getBean(FyeoVaultService.class);
            byte[] rawDoc = createDummyDoc();

            // Protect
            EncryptedImagePayload payload = vaultService.protectDocument(rawDoc);
            assertThat(payload.getImageBytes()).isNotEmpty();

            // Encode/Decode storage token
            String token = vaultService.encodeStorageToken(payload.getHmacTag(), payload.getSalt());
            assertThat(token).startsWith("FYEO:v3:");

            byte[][] parsed = vaultService.decodeStorageToken(token);

            // Restore in RAM
            byte[] restored = vaultService.restoreDocument(payload.getImageBytes(), parsed[0], parsed[1]);
            assertThat(restored).isEqualTo(rawDoc);
        });
    }

    @Test
    @DisplayName("Auto-configuration can be disabled via property")
    void testAutoConfigurationDisabled() {
        contextRunner.withPropertyValues("fyeo.vault.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FyeoVaultService.class);
                });
    }

    @Test
    @DisplayName("Custom configuration properties are bound cleanly with sensible defaults")
    void testConfigurability() {
        contextRunner.withPropertyValues(
                "fyeo.vault.ephemeral-view-duration-seconds=45",
                "fyeo.vault.watermark.template=AUDIT • %username% • %documentId%",
                "fyeo.vault.chameleon.default-description=Custom Brand Badge"
        ).run(context -> {
            FyeoVaultService vaultService = context.getBean(FyeoVaultService.class);
            assertThat(vaultService.getEphemeralViewDurationSeconds()).isEqualTo(45);

            String watermark = vaultService.renderWatermark("reviewer-ayomide", "CERT-8819");
            assertThat(watermark).isEqualTo("AUDIT • reviewer-ayomide • CERT-8819");

            assertThat(vaultService.getProperties().getChameleon().getDefaultDescription())
                    .isEqualTo("Custom Brand Badge");
        });
    }
}
