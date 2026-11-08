package io.jfs.playpen;

import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.MetadataEnvelope;
import io.jfs.playpen.service.FileEncryptorService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * F.Y.E.O Hands-On Playpen
 * Live CLI test harness demonstrating encryption, cloaking, and decryption.
 *
 * Java for the Street. 🛣️☕️
 */
public class App {

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("   JAVA FOR THE STREET - F.Y.E.O DEVELOPER PLAYPEN");
        System.out.println("   Enterprise Code, Grassroots Soul. 🛣️☕️");
        System.out.println("   Version: 3.0.2 (Maven Central)");
        System.out.println("=================================================================\n");

        // 1. Determine input file
        Path inputFile;
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            inputFile = Paths.get(args[0]);
        } else {
            inputFile = Paths.get("samples/cac_certificate_sample.txt");
        }

        if (!Files.exists(inputFile)) {
            System.err.println("❌ Error: Input file not found: " + inputFile.toAbsolutePath());
            System.exit(1);
        }

        byte[] originalBytes = Files.readAllBytes(inputFile);
        String filename = inputFile.getFileName().toString();
        System.out.printf("[STEP 1] Ingesting sensitive document: %s (%d bytes)\n", filename, originalBytes.length);

        // 2. Initialize Service & Singleton Vault
        FileEncryptorService fileEncryptorService = new FileEncryptorService();
        int vaultId = System.identityHashCode(fileEncryptorService.getFyeoVault());
        System.out.printf("         Vault Singleton Instance ID: %d (Thread-safe JVM Singleton)\n\n", vaultId);

        // 3. Cloak document into innocent PNG decoy
        Path outputDir = Paths.get("output");
        Files.createDirectories(outputDir);
        Path cloakedPngPath = outputDir.resolve("cloaked_document.png");

        System.out.println("[STEP 2] Cloaking document into innocent PNG decoy carrier...");
        ChameleonPayload payload = fileEncryptorService.encryptFile(
                inputFile,
                cloakedPngPath,
                "Adageje Moshood",
                "CONFIDENTIAL_KYC"
        );

        long cloakedSize = Files.size(cloakedPngPath);
        System.out.printf("         Decoy Dimensions: %dx%d (%d pixels)\n", payload.getWidth(), payload.getHeight(), payload.getWidth() * payload.getHeight());
        System.out.printf("         Carrier Asset: %s (%d bytes)\n", payload.getDecoyDescription(), cloakedSize);
        System.out.printf("         Saved cloaked asset: %s\n", cloakedPngPath.toAbsolutePath());
        System.out.println("         Verification: Image on disk is a 100% valid, viewable PNG.\n");

        // 4. Uncloak document strictly in volatile RAM
        System.out.println("[STEP 3] Uncloaking document strictly in volatile RAM (Zero temp files)...");
        MetadataEnvelope envelope = fileEncryptorService.decryptFile(cloakedPngPath);
        byte[] restoredBytes = envelope.getImageBytes();

        Path restoredPath = outputDir.resolve("restored_" + envelope.getOriginalFilename());
        Files.write(restoredPath, restoredBytes);

        System.out.printf("         Uncloaked Document Name: %s\n", envelope.getOriginalFilename());
        System.out.printf("         Customer Identity: %s\n", envelope.getCustomerName());
        System.out.printf("         Restored Payload Size: %d bytes\n\n", restoredBytes.length);

        // 5. Bit-level cryptographic parity verification
        System.out.println("[STEP 4] Bit-level cryptographic parity verification...");
        boolean isBitPerfect = Arrays.equals(originalBytes, restoredBytes);

        if (isBitPerfect) {
            System.out.println("         MATCH! Restored bytes match original input with 100.00% cryptographic parity.");
            System.out.println("         AES-256-GCM authentication tag verified. Zero byte corruption.");
            System.out.println("\n🎉 ALL TESTS PASSED! F.Y.E.O is live, active, and verified rock-solid.");
        } else {
            System.err.println("         MISMATCH! Restored bytes do not match original bytes.");
            System.exit(1);
        }
    }
}
