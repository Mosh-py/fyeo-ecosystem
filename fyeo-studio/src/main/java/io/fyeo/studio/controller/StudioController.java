package io.fyeo.studio.controller;

import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.EncryptedImagePayload;
import io.fyeo.core.model.MetadataEnvelope;
import io.fyeo.starter.FyeoVaultService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
public class StudioController {

    private final FyeoVaultService fyeoVaultService;

    // Simulated In-Memory S3 Bucket
    private final Map<String, SimulatedS3Object> simulatedBucket = new ConcurrentHashMap<>();
    private final AtomicInteger sequenceCounter = new AtomicInteger(1);

    public StudioController(FyeoVaultService fyeoVaultService) {
        this.fyeoVaultService = fyeoVaultService;
        initializeSampleS3Bucket();
    }

    private void initializeSampleS3Bucket() {
        simulatedBucket.clear();
        try {
            // Seed a realistic bank S3 bucket with 3 simulated customer KYC documents (all initially naked)
            seedDocument("doc_001_NIN_Adeyemi.png", "Adeyemi Babatunde", "NIN: 7492 8841 0293", "NATIONAL IDENTITY SLIP");
            seedDocument("doc_002_CAC_KudaVentures.png", "Kuda Ventures Ltd", "RC: 1827491", "CAC REGISTRATION CERTIFICATE");
            seedDocument("doc_003_Passport_Chioma.png", "Chioma Okonjo", "PASSPORT: A09482711", "FEDERAL REPUBLIC OF NIGERIA");
        } catch (IOException e) {
            // Fallback for tests
        }
    }

    private void seedDocument(String originalKey, String name, String idText, String title) throws IOException {
        BufferedImage img = new BufferedImage(500, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, 500, 320);

        g.setColor(new Color(6, 78, 59));
        g.fillRect(0, 0, 500, 60);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(title, 20, 38);

        g.setColor(new Color(15, 23, 42));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("NAME: " + name, 30, 110);
        g.drawString(idText, 30, 150);
        g.drawString("DOB: 14 OCT 1996 · SEX: M", 30, 190);
        g.drawString("STATUS: ACTIVE CITIZEN RECORD", 30, 230);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        byte[] rawBytes = baos.toByteArray();

        simulatedBucket.put(originalKey, new SimulatedS3Object(
                originalKey,
                originalKey,
                rawBytes,
                "NAKED",
                null,
                name,
                idText,
                name,
                idText,
                "Naked Plaintext"
        ));
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("objects", simulatedBucket.values());
        return "index";
    }

    @PostMapping("/api/simulator/reset")
    @ResponseBody
    public Map<String, Object> resetSimulator() {
        initializeSampleS3Bucket();
        return Map.of("success", true, "message", "S3 Bucket reset to initial state");
    }

    @PostMapping("/api/simulator/protect")
    @ResponseBody
    public Map<String, Object> protectS3Document(
            @RequestParam("key") String key,
            @RequestParam(value = "mode", defaultValue = "STATIC") String mode) {
        
        SimulatedS3Object obj = simulatedBucket.get(key);
        if (obj == null) {
            return Map.of("success", false, "error", "Document not found: " + key);
        }
        if (!"NAKED".equals(obj.getMode())) {
            return Map.of("success", true, "message", "Already protected (" + obj.getMode() + ")");
        }

        if ("CHAMELEON".equalsIgnoreCase(mode)) {
            // 1. Encapsulate original filename and PII inside encrypted envelope
            MetadataEnvelope envelope = new MetadataEnvelope(
                    obj.getOriginalFilename(),
                    obj.getCustomerName(),
                    obj.getIdNumber(),
                    System.currentTimeMillis(),
                    obj.getBytes()
            );

            // 2. Embed envelope into Chameleon Decoy carrier
            ChameleonPayload chameleon = fyeoVaultService.protectDocumentWithEnvelope(envelope, null);

            // 3. Cloak S3 key into an innocent asset name
            int seq = sequenceCounter.getAndIncrement();
            String cloakedKey = fyeoVaultService.generateChameleonDecoyKey(seq);

            SimulatedS3Object protectedObj = new SimulatedS3Object(
                    cloakedKey,
                    obj.getOriginalFilename(),
                    chameleon.getCarrierBytes(),
                    "CHAMELEON",
                    "FYEO:CHAMELEON:AES-GCM",
                    obj.getCustomerName(),
                    obj.getIdNumber(),
                    "[CONCEALED: INNOCUOUS ASSET]",
                    "[SYSTEM ASSET]",
                    chameleon.getDecoyDescription()
            );

            // Remove naked key from S3 and insert cloaked key
            simulatedBucket.remove(key);
            simulatedBucket.put(cloakedKey, protectedObj);

            return Map.of(
                    "success", true,
                    "oldKey", key,
                    "newKey", cloakedKey,
                    "status", "PROTECTED_CHAMELEON_DECOY",
                    "desc", chameleon.getDecoyDescription()
            );
        } else {
            // 1. Standard Visual AEAD Scramble
            EncryptedImagePayload payload = fyeoVaultService.protectDocument(obj.getBytes());
            String token = fyeoVaultService.encodeStorageToken(payload.getHmacTag(), payload.getSalt());

            // 2. Cloak S3 key with zero-knowledge blind hash
            String cloakedKey = fyeoVaultService.generateOpaqueKey(key);

            SimulatedS3Object protectedObj = new SimulatedS3Object(
                    cloakedKey,
                    obj.getOriginalFilename(),
                    payload.getImageBytes(),
                    "STATIC",
                    token,
                    obj.getCustomerName(),
                    obj.getIdNumber(),
                    "[ENCRYPTED OPAQUE]",
                    "[BLIND HASH]",
                    "Visual AEAD Static Noise"
            );

            simulatedBucket.remove(key);
            simulatedBucket.put(cloakedKey, protectedObj);

            return Map.of(
                    "success", true,
                    "oldKey", key,
                    "newKey", cloakedKey,
                    "token", token,
                    "status", "PROTECTED_VISUAL_AEAD"
            );
        }
    }

    @GetMapping("/api/simulator/preview")
    public ResponseEntity<byte[]> previewObject(@RequestParam("key") String key) {
        SimulatedS3Object obj = simulatedBucket.get(key);
        if (obj == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(obj.getBytes());
    }

    @PostMapping("/api/simulator/ephemeral-inspect")
    @ResponseBody
    public Map<String, Object> ephemeralInspect(@RequestParam("key") String key) {
        SimulatedS3Object obj = simulatedBucket.get(key);
        if (obj == null || "NAKED".equals(obj.getMode())) {
            return Map.of("success", false, "error", "Invalid or unencrypted object");
        }

        byte[] restoredBytes;
        String restoredFilename = obj.getOriginalFilename();
        String restoredCustomer = obj.getCustomerName();
        String restoredId = obj.getIdNumber();

        if ("CHAMELEON".equals(obj.getMode())) {
            MetadataEnvelope restoredEnv = fyeoVaultService.restoreDocumentEnvelope(obj.getBytes());
            restoredBytes = restoredEnv.getImageBytes();
            restoredFilename = restoredEnv.getOriginalFilename();
            restoredCustomer = restoredEnv.getCustomerName();
            restoredId = restoredEnv.getDocumentType();
        } else {
            byte[][] parsed = fyeoVaultService.decodeStorageToken(obj.getStorageToken());
            restoredBytes = fyeoVaultService.restoreDocument(obj.getBytes(), parsed[0], parsed[1]);
        }

        String base64Restored = Base64.getEncoder().encodeToString(restoredBytes);
        return Map.of(
                "success", true,
                "s3Key", key,
                "originalFilename", restoredFilename,
                "customerName", restoredCustomer,
                "idNumber", restoredId,
                "mode", obj.getMode(),
                "imageData", "data:image/png;base64," + base64Restored,
                "viewTtlSeconds", 15,
                "message", "Metadata envelope and document decrypted in volatile RAM for 15s. Auto-burn active."
        );
    }

    public static class SimulatedS3Object {
        private final String key; // Cloaked physical key in S3
        private final String originalFilename; // Protected original name (held only in envelope)
        private final byte[] bytes;
        private final String mode; // NAKED, STATIC, CHAMELEON
        private final String storageToken;
        private final String customerName; // True customer
        private final String idNumber;     // True ID
        private final String s3CustomerView; // What attacker sees in S3
        private final String s3IdView;       // What attacker sees in S3
        private final String description;

        public SimulatedS3Object(String key, String originalFilename, byte[] bytes, String mode,
                                 String storageToken, String customerName, String idNumber,
                                 String s3CustomerView, String s3IdView, String description) {
            this.key = key;
            this.originalFilename = originalFilename;
            this.bytes = bytes;
            this.mode = mode;
            this.storageToken = storageToken;
            this.customerName = customerName;
            this.idNumber = idNumber;
            this.s3CustomerView = s3CustomerView;
            this.s3IdView = s3IdView;
            this.description = description;
        }

        public String getKey() { return key; }
        public String getOriginalFilename() { return originalFilename; }
        public byte[] getBytes() { return bytes; }
        public String getMode() { return mode; }
        public boolean isProtected() { return !"NAKED".equals(mode); }
        public String getStorageToken() { return storageToken; }
        public String getCustomerName() { return customerName; }
        public String getIdNumber() { return idNumber; }
        public String getS3CustomerView() { return s3CustomerView; }
        public String getS3IdView() { return s3IdView; }
        public String getDescription() { return description; }
        public int getSizeBytes() { return bytes.length; }
    }
}
