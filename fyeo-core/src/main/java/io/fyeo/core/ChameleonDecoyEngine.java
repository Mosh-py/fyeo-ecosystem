package io.fyeo.core;

import io.fyeo.core.exception.CryptographicException;
import io.fyeo.core.exception.TamperDetectedException;
import io.fyeo.core.model.ChameleonPayload;
import io.fyeo.core.model.MetadataEnvelope;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Chameleon Stego-AEAD Engine (v3.0).
 * 
 * Conceals the very existence of sensitive KYC documents by embedding authenticated,
 * high-entropy ciphertext into visually ordinary carrier images (corporate badges,
 * brand logos, receipt templates) using PRNG-scattered Fisher-Yates LSB modulation.
 * 
 * Guarantees:
 * 1. Plausible Deniability: Carrier appears 100% normal in preview and standard decoders.
 * 2. Authenticated Encryption: AES-256-GCM prevents bit manipulation or forgery.
 * 3. PRNG Scattering: Resistant to sequential chi-square steganalysis.
 */
public final class ChameleonDecoyEngine {

    public static final int PBKDF2_ROUNDS = 100_000;
    public static final int SALT_BYTES = 16;
    public static final int IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int MAGIC = 0x4659454F; // "FYEO"
    public static final int HEADER_BYTES = 4 + SALT_BYTES + IV_BYTES + 4; // Magic(4) + Salt(16) + IV(12) + Len(4) = 36 bytes
    public static final int HEADER_CHANNEL_POOL = 4096; // First 4096 channels reserved for scattered header

    private static final byte[] FIXED_HEADER_SALT = "FYEO-Decoy-Hdr-Salt-v3".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_AES = "FYEO-v3-Stego-AES-GCM".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_SCATTER = "FYEO-v3-Stego-Scatter".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ChameleonDecoyEngine() {}

    /**
     * Embeds a metadata envelope containing both filename/PII and document bytes into an innocuous carrier.
     */
    public static ChameleonPayload embedEnvelope(MetadataEnvelope envelope, byte[] carrierImageBytes, String passphrase) {
        Objects.requireNonNull(envelope, "envelope cannot be null");
        return embed(envelope.serialize(), carrierImageBytes, passphrase);
    }

    /**
     * Extracts and restores both metadata and document bytes from a chameleon decoy image.
     */
    public static MetadataEnvelope extractEnvelope(byte[] carrierImageBytes, String passphrase) {
        byte[] payloadBytes = extract(carrierImageBytes, passphrase);
        return MetadataEnvelope.deserialize(payloadBytes);
    }

    /**
     * Embeds a sensitive document into an innocuous carrier image.
     *
     * @param secretImageBytes  Plaintext sensitive KYC document bytes (NIN, CAC, passport)
     * @param carrierImageBytes Plaintext carrier image bytes (if null, a default corporate carrier is generated)
     * @param passphrase        Secret passkey or KMS envelope token
     * @return ChameleonPayload containing the innocent-looking carrier image
     */
    public static ChameleonPayload embed(byte[] secretImageBytes, byte[] carrierImageBytes, String passphrase) {
        Objects.requireNonNull(secretImageBytes, "secretImageBytes cannot be null");
        Objects.requireNonNull(passphrase, "passphrase cannot be null");

        // 1. Deflate compress secret document
        byte[] compressedSecret = compress(secretImageBytes);

        // 2. Encrypt with AES-256-GCM
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        byte[] masterKey = deriveKey(passphrase, salt);
        byte[] aesKey = hkdfExpand(masterKey, INFO_AES, 32);

        byte[] ciphertext = encryptAesGcm(compressedSecret, aesKey, iv);

        // 3. Assemble binary packet: [MAGIC(4) | SALT(16) | IV(12) | CIPHERTEXT_LEN(4) | CIPHERTEXT]
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + ciphertext.length);
        buffer.putInt(MAGIC);
        buffer.put(salt);
        buffer.put(iv);
        buffer.putInt(ciphertext.length);
        buffer.put(ciphertext);
        byte[] packet = buffer.array();

        int payloadBits = ciphertext.length * 8;
        int headerBits = HEADER_BYTES * 8;

        // 4. Resolve Carrier Image
        BufferedImage carrier;
        String description;
        if (carrierImageBytes != null && carrierImageBytes.length > 0) {
            carrier = readImage(carrierImageBytes);
            description = "Custom Organization Carrier";
        } else {
            // Calculate necessary dimensions (3 bits per pixel across RGB)
            int minChannels = HEADER_CHANNEL_POOL + payloadBits + 5000;
            int minPixels = (minChannels / 3) + 1000;
            int width = Math.max(640, (int) Math.sqrt(minPixels * 1.5));
            int height = Math.max(400, minPixels / width + 60);
            carrier = generateCorporateDecoy(width, height, "ENTERPRISE IDENTITY SYSTEM", "VERIFIED COMPLIANCE ARCHIVE");
            description = "Institutional Compliance Brand Asset";
        }

        int carrierWidth = carrier.getWidth();
        int carrierHeight = carrier.getHeight();
        int totalChannels = carrierWidth * carrierHeight * 3;

        if (totalChannels < HEADER_CHANNEL_POOL + payloadBits) {
            throw new CryptographicException(String.format(
                    "Carrier capacity insufficient: needs %d channels, carrier only has %d. Enlarge carrier image.",
                    HEADER_CHANNEL_POOL + payloadBits, totalChannels));
        }

        // 5. Embed Header in PRNG locations derived from Header Key inside HEADER_CHANNEL_POOL
        byte[] headerKey = deriveKey(passphrase, FIXED_HEADER_SALT);
        byte[] headerScatterKey = hkdfExpand(headerKey, INFO_SCATTER, 32);
        int[] headerIndices = selectFisherYatesChannels(0, HEADER_CHANNEL_POOL, headerBits, headerScatterKey);

        // Extract raw RGB byte array from carrier
        byte[] channels = extractRgbChannels(carrier);

        embedBitsIntoChannels(channels, packet, 0, HEADER_BYTES, headerIndices);

        // 6. Embed Ciphertext in PRNG locations in the remaining pool [HEADER_CHANNEL_POOL ... totalChannels - 1]
        byte[] payloadScatterKey = hkdfExpand(masterKey, INFO_SCATTER, 32);
        int[] payloadIndices = selectFisherYatesChannels(HEADER_CHANNEL_POOL, totalChannels, payloadBits, payloadScatterKey);

        embedBitsIntoChannels(channels, packet, HEADER_BYTES, ciphertext.length, payloadIndices);

        // 7. Write modified channels back into buffered image
        applyRgbChannels(carrier, channels);

        byte[] outputBytes = writeImagePng(carrier);
        return new ChameleonPayload(outputBytes, carrierWidth, carrierHeight, packet.length, description);
    }

    /**
     * Extracts and restores the hidden document from a chameleon carrier image.
     *
     * @param carrierImageBytes The carrier image bytes
     * @param passphrase        Secret passkey or KMS envelope token
     * @return Exact byte array of the original sensitive document
     */
    public static byte[] extract(byte[] carrierImageBytes, String passphrase) {
        Objects.requireNonNull(carrierImageBytes, "carrierImageBytes cannot be null");
        Objects.requireNonNull(passphrase, "passphrase cannot be null");

        BufferedImage carrier = readImage(carrierImageBytes);
        int totalChannels = carrier.getWidth() * carrier.getHeight() * 3;

        if (totalChannels < HEADER_CHANNEL_POOL) {
            throw new TamperDetectedException("Invalid carrier image: insufficient dimensions for steganographic pool");
        }

        byte[] channels = extractRgbChannels(carrier);

        // 1. Locate and extract Header
        byte[] headerKey = deriveKey(passphrase, FIXED_HEADER_SALT);
        byte[] headerScatterKey = hkdfExpand(headerKey, INFO_SCATTER, 32);
        int headerBits = HEADER_BYTES * 8;
        int[] headerIndices = selectFisherYatesChannels(0, HEADER_CHANNEL_POOL, headerBits, headerScatterKey);

        byte[] headerBytes = extractBitsFromChannels(channels, HEADER_BYTES, headerIndices);
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);

        int magic = headerBuffer.getInt();
        if (magic != MAGIC) {
            throw new TamperDetectedException("Decoy authentication failed: invalid magic header or incorrect key");
        }

        byte[] salt = new byte[SALT_BYTES];
        headerBuffer.get(salt);
        byte[] iv = new byte[IV_BYTES];
        headerBuffer.get(iv);
        int ciphertextLen = headerBuffer.getInt();

        if (ciphertextLen <= 0 || ciphertextLen > 50_000_000 || (HEADER_CHANNEL_POOL + ciphertextLen * 8) > totalChannels) {
            throw new TamperDetectedException("Corrupt steganographic payload length: " + ciphertextLen);
        }

        // 2. Derive Payload Keys
        byte[] masterKey = deriveKey(passphrase, salt);
        byte[] payloadScatterKey = hkdfExpand(masterKey, INFO_SCATTER, 32);
        int payloadBits = ciphertextLen * 8;
        int[] payloadIndices = selectFisherYatesChannels(HEADER_CHANNEL_POOL, totalChannels, payloadBits, payloadScatterKey);

        byte[] ciphertext = extractBitsFromChannels(channels, ciphertextLen, payloadIndices);

        // 3. Decrypt with AES-256-GCM (Automatic Authentication Tag Verification)
        byte[] aesKey = hkdfExpand(masterKey, INFO_AES, 32);
        byte[] compressedSecret;
        try {
            compressedSecret = decryptAesGcm(ciphertext, aesKey, iv);
        } catch (Exception e) {
            throw new TamperDetectedException("Steganographic payload authentication failed: image tampered or key mismatch", e);
        }

        // 4. Decompress back to original bytes
        return decompress(compressedSecret);
    }

    /**
     * Generates a sleek, institutional enterprise decoy carrier image.
     */
    public static BufferedImage generateCorporateDecoy(int width, int height, String headerTitle, String subTitle) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Sleek enterprise background
        g.setColor(new Color(15, 23, 42)); // slate-900
        g.fillRect(0, 0, width, height);

        // Decorative background geometric grid
        g.setColor(new Color(30, 41, 59, 140)); // slate-800
        int gridSize = 32;
        for (int x = 0; x < width; x += gridSize) {
            g.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += gridSize) {
            g.drawLine(0, y, width, y);
        }

        // Header brand bar
        g.setColor(new Color(16, 185, 129)); // emerald-500
        g.fillRect(0, 0, width, 6);

        // Inner Card Container
        int cardMargin = 28;
        g.setColor(new Color(30, 41, 59));
        g.fillRoundRect(cardMargin, cardMargin + 10, width - (cardMargin * 2), height - (cardMargin * 2) - 10, 16, 16);

        // Card Border
        g.setColor(new Color(51, 65, 85));
        g.drawRoundRect(cardMargin, cardMargin + 10, width - (cardMargin * 2), height - (cardMargin * 2) - 10, 16, 16);

        // Organization Brand Badge
        g.setColor(new Color(16, 185, 129, 40));
        g.fillRoundRect(cardMargin + 24, cardMargin + 32, 48, 48, 12, 12);
        g.setColor(new Color(16, 185, 129));
        g.drawRoundRect(cardMargin + 24, cardMargin + 32, 48, 48, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(new Color(16, 185, 129));
        g.drawString("◈", cardMargin + 39, cardMargin + 64);

        // Title and Metadata
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(headerTitle, cardMargin + 88, cardMargin + 52);

        g.setColor(new Color(148, 163, 184)); // slate-400
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString(subTitle, cardMargin + 88, cardMargin + 72);

        // Decorative horizontal rule
        g.setColor(new Color(51, 65, 85));
        g.drawLine(cardMargin + 24, cardMargin + 100, width - cardMargin - 24, cardMargin + 100);

        // Innocent compliance stamp
        g.setColor(new Color(100, 116, 139));
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.drawString("SYSTEM ASSET ID: SYS-ARCH-2026-X9", cardMargin + 28, height - cardMargin - 40);
        g.drawString("STATUS: SECURE ENTERPRISE REPOSITORY", cardMargin + 28, height - cardMargin - 22);

        g.dispose();
        return img;
    }

    private static byte[] extractRgbChannels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);

        byte[] channels = new byte[rgb.length * 3];
        int idx = 0;
        for (int pixel : rgb) {
            channels[idx++] = (byte) ((pixel >> 16) & 0xFF); // Red
            channels[idx++] = (byte) ((pixel >> 8) & 0xFF);  // Green
            channels[idx++] = (byte) (pixel & 0xFF);         // Blue
        }
        return channels;
    }

    private static void applyRgbChannels(BufferedImage image, byte[] channels) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = new int[width * height];
        int idx = 0;
        for (int i = 0; i < rgb.length; i++) {
            int r = channels[idx++] & 0xFF;
            int g = channels[idx++] & 0xFF;
            int b = channels[idx++] & 0xFF;
            rgb[i] = (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, rgb, 0, width);
    }

    /**
     * Selects exactly `count` unique channel indices from range [poolStart, poolEnd)
     * using partial Fisher-Yates shuffle seeded with PRNG.
     */
    private static int[] selectFisherYatesChannels(int poolStart, int poolEnd, int count, byte[] seedKey) {
        int poolSize = poolEnd - poolStart;
        if (count > poolSize) {
            throw new CryptographicException("Requested channel count exceeds pool size: " + count + " > " + poolSize);
        }

        // Initialize pool indices
        int[] pool = new int[poolSize];
        for (int i = 0; i < poolSize; i++) {
            pool[i] = poolStart + i;
        }

        ByteBuffer buf = ByteBuffer.wrap(seedKey);
        long s0 = buf.getLong();
        long s1 = buf.getLong() ^ 0x9E3779B97F4A7C15L;
        if (s0 == 0 && s1 == 0) s0 = 1L;

        long state0 = s0;
        long state1 = s1;

        int[] selected = new int[count];
        for (int i = 0; i < count; i++) {
            // Xoroshiro128++ step
            long result = Long.rotateLeft(state0 + state1, 17) + state0;
            state1 ^= state0;
            state0 = Long.rotateLeft(state0, 49) ^ state1 ^ (state1 << 21);
            state1 = Long.rotateLeft(state1, 28);

            long positive = result & 0x7FFFFFFFFFFFFFFFL;
            int remaining = poolSize - i;
            int j = i + (int) (positive % remaining);

            // Swap pool[i] and pool[j]
            int temp = pool[i];
            pool[i] = pool[j];
            pool[j] = temp;

            selected[i] = pool[i];
        }
        return selected;
    }

    private static void embedBitsIntoChannels(byte[] channels, byte[] data, int dataOffset, int dataLength, int[] indices) {
        int bitIdx = 0;
        for (int i = 0; i < dataLength; i++) {
            int b = data[dataOffset + i] & 0xFF;
            for (int bit = 7; bit >= 0; bit--) {
                int bitVal = (b >> bit) & 0x01;
                int targetChannel = indices[bitIdx++];
                channels[targetChannel] = (byte) ((channels[targetChannel] & 0xFE) | bitVal);
            }
        }
    }

    private static byte[] extractBitsFromChannels(byte[] channels, int dataLength, int[] indices) {
        byte[] result = new byte[dataLength];
        int bitIdx = 0;
        for (int i = 0; i < dataLength; i++) {
            int b = 0;
            for (int bit = 7; bit >= 0; bit--) {
                int bitVal = (channels[indices[bitIdx++]]) & 0x01;
                b = (b << 1) | bitVal;
            }
            result[i] = (byte) b;
        }
        return result;
    }

    private static byte[] encryptAesGcm(byte[] plaintext, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptographicException("AES-GCM encryption failed", e);
        }
    }

    private static byte[] decryptAesGcm(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private static byte[] compress(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
            dos.write(data);
            dos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CryptographicException("Deflate compression failed", e);
        }
    }

    private static byte[] decompress(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             InflaterInputStream iis = new InflaterInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = iis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new TamperDetectedException("Decompression failed: corrupted payload bytes", e);
        }
    }

    private static byte[] deriveKey(String passphrase, byte[] salt) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ROUNDS, 256);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new CryptographicException("PBKDF2 key derivation failed", e);
        }
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int outputLength) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 0x01);
            byte[] okm = mac.doFinal();
            return Arrays.copyOf(okm, outputLength);
        } catch (Exception e) {
            throw new CryptographicException("HKDF subkey expansion failed", e);
        }
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new TamperDetectedException("Unsupported or corrupt carrier image: invalid format or tampered bytes");
            }
            return img;
        } catch (IOException e) {
            throw new TamperDetectedException("I/O error reading carrier image: tampered or corrupt byte stream", e);
        }
    }

    private static byte[] writeImagePng(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CryptographicException("I/O error encoding output image", e);
        }
    }
}
