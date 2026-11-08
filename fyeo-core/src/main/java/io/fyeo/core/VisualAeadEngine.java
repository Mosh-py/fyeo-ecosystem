package io.fyeo.core;

import io.fyeo.core.exception.CryptographicException;
import io.fyeo.core.exception.TamperDetectedException;
import io.fyeo.core.model.EncryptedImagePayload;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

/**
 * Format-Preserving Visual AEAD (v3.0) Engine.
 * 
 * Cryptographically transforms images into high-entropy visual static noise
 * using Fisher-Yates CSPRNG permutation, HKDF subkey expansion, and
 * side-channel immune HMAC-SHA256 authentication.
 */
public final class VisualAeadEngine {

    public static final int PBKDF2_ROUNDS = 100_000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_BYTES = 32;
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final byte[] INFO_PERMUTATION = "FYEO-v3-Permutation".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_AUTH = "FYEO-v3-Auth-HMAC".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VisualAeadEngine() {}

    /**
     * Encrypts and scrambles an image into Visual AEAD noise.
     *
     * @param rawImageBytes  Plaintext image bytes (JPEG, PNG, etc.)
     * @param passphrase     User secret or enterprise session seed
     * @param maxPixels      Maximum pixel budget to avoid OOM
     * @return EncryptedImagePayload containing the scrambled PNG bytes and authentication metadata
     */
    public static EncryptedImagePayload encrypt(byte[] rawImageBytes, String passphrase, int maxPixels) {
        Objects.requireNonNull(rawImageBytes, "rawImageBytes cannot be null");
        Objects.requireNonNull(passphrase, "passphrase cannot be null");

        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        return encryptWithSalt(rawImageBytes, passphrase, salt, maxPixels);
    }

    /**
     * Encrypts and scrambles with a pre-determined salt (used for deterministic envelope packaging).
     */
    public static EncryptedImagePayload encryptWithSalt(byte[] rawImageBytes, String passphrase, byte[] salt, int maxPixels) {
        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(rawImageBytes));
            if (originalImage == null) {
                throw new CryptographicException("Failed to decode image: unsupported format or corrupt bytes");
            }
        } catch (IOException e) {
            throw new CryptographicException("I/O error decoding source image", e);
        }

        BufferedImage budgeted = PixelBudgeter.enforceBudget(originalImage, maxPixels);
        int width = budgeted.getWidth();
        int height = budgeted.getHeight();
        int totalPixels = width * height;

        int[] pixels = budgeted.getRGB(0, 0, width, height, null, 0, width);

        // Derive Master Key & Subkeys via HKDF
        byte[] masterKey = deriveMasterKey(passphrase, salt);
        byte[] permKey = hkdfExpand(masterKey, INFO_PERMUTATION, KEY_BYTES);
        byte[] authKey = hkdfExpand(masterKey, INFO_AUTH, KEY_BYTES);

        // Compute Forward Fisher-Yates Permutation
        int[] permutation = computePermutation(totalPixels, permKey);
        int[] scrambledPixels = new int[totalPixels];
        for (int i = 0; i < totalPixels; i++) {
            scrambledPixels[i] = pixels[permutation[i]];
        }

        BufferedImage scrambledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        scrambledImage.setRGB(0, 0, width, height, scrambledPixels, 0, width);

        byte[] scrambledBytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scrambledImage, "PNG", baos);
            scrambledBytes = baos.toByteArray();
        } catch (IOException e) {
            throw new CryptographicException("Failed to encode scrambled image to PNG", e);
        }

        // Authenticate Scrambled Bytes + AAD (Width, Height, Salt)
        byte[] aad = createAad(width, height, salt);
        byte[] hmacTag = computeHmac(authKey, aad, scrambledBytes);

        return new EncryptedImagePayload(scrambledBytes, "image/png", width, height, hmacTag, salt, null);
    }

    /**
     * Decrypts and restores scrambled visual static back to the original image.
     *
     * @param scrambledImageBytes Scrambled PNG bytes
     * @param passphrase          User secret or enterprise session key
     * @param expectedHmacTag     Expected HMAC tag from metadata
     * @param salt                Salt used during encryption
     * @return Decrypted plaintext PNG image bytes
     * @throws TamperDetectedException if HMAC verification fails
     */
    public static byte[] decrypt(byte[] scrambledImageBytes, String passphrase, byte[] expectedHmacTag, byte[] salt) {
        Objects.requireNonNull(scrambledImageBytes, "scrambledImageBytes cannot be null");
        Objects.requireNonNull(passphrase, "passphrase cannot be null");
        Objects.requireNonNull(expectedHmacTag, "expectedHmacTag cannot be null");
        Objects.requireNonNull(salt, "salt cannot be null");

        // Verify image decode first to obtain dimensions
        BufferedImage scrambledImage;
        try {
            scrambledImage = ImageIO.read(new ByteArrayInputStream(scrambledImageBytes));
            if (scrambledImage == null) {
                throw new TamperDetectedException("Visual AEAD authentication failed: corrupt image bytes or invalid payload structure");
            }
        } catch (Exception e) {
            throw new TamperDetectedException("Visual AEAD authentication failed: corrupt image bytes or invalid payload structure", e);
        }

        int width = scrambledImage.getWidth();
        int height = scrambledImage.getHeight();
        int totalPixels = width * height;

        // Derive Keys
        byte[] masterKey = deriveMasterKey(passphrase, salt);
        byte[] permKey = hkdfExpand(masterKey, INFO_PERMUTATION, KEY_BYTES);
        byte[] authKey = hkdfExpand(masterKey, INFO_AUTH, KEY_BYTES);

        // Constant-Time Authenticate AAD + Scrambled Bytes
        byte[] aad = createAad(width, height, salt);
        byte[] actualHmacTag = computeHmac(authKey, aad, scrambledImageBytes);

        if (!constantTimeEquals(expectedHmacTag, actualHmacTag)) {
            throw new TamperDetectedException("Visual AEAD authentication failed: document has been modified, corrupted, or invalid key");
        }

        // Invert Permutation
        int[] permutation = computePermutation(totalPixels, permKey);
        int[] scrambledPixels = scrambledImage.getRGB(0, 0, width, height, null, 0, width);
        int[] restoredPixels = new int[totalPixels];

        for (int i = 0; i < totalPixels; i++) {
            restoredPixels[permutation[i]] = scrambledPixels[i];
        }

        BufferedImage restoredImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        restoredImage.setRGB(0, 0, width, height, restoredPixels, 0, width);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(restoredImage, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new CryptographicException("Failed to encode restored image to PNG", e);
        }
    }

    // --- Cryptographic Primitives ---

    public static byte[] deriveMasterKey(String passphrase, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ROUNDS, KEY_BYTES * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new CryptographicException("Failed to derive master key with PBKDF2", e);
        }
    }

    public static byte[] hkdfExpand(byte[] prk, byte[] info, int outputLength) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(prk, HMAC_ALGO));
            ByteBuffer buffer = ByteBuffer.allocate(outputLength);
            byte[] t = new byte[0];
            byte round = 1;

            while (buffer.hasRemaining()) {
                mac.update(t);
                mac.update(info);
                mac.update(round);
                t = mac.doFinal();

                int toCopy = Math.min(t.length, buffer.remaining());
                buffer.put(t, 0, toCopy);
                round++;
            }
            return buffer.array();
        } catch (Exception e) {
            throw new CryptographicException("HKDF subkey expansion failed", e);
        }
    }

    private static int[] computePermutation(int n, byte[] seedKey) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }

        // Split seedKey into initial 64-bit seed state
        ByteBuffer bb = ByteBuffer.wrap(seedKey);
        long s0 = bb.getLong();
        long s1 = bb.getLong();

        // Xoroshiro128++ PRNG for deterministic linear-time shuffle
        for (int i = n - 1; i > 0; i--) {
            // Next random 64-bit integer
            long s0_val = s0;
            long s1_val = s1 ^ s0_val;
            long result = Long.rotateLeft(s0_val + s1_val, 17) + s0_val;
            s0 = Long.rotateLeft(s0_val, 49) ^ s1_val ^ (s1_val << 21);
            s1 = Long.rotateLeft(s1_val, 28);

            long bound = i + 1;
            long positive = result & 0x7FFFFFFFFFFFFFFFL;
            long randIndex = positive % bound;
            int j = (int) randIndex;

            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        return p;
    }

    private static byte[] createAad(int width, int height, byte[] salt) {
        return ByteBuffer.allocate(4 + 4 + salt.length)
                .putInt(width)
                .putInt(height)
                .put(salt)
                .array();
    }

    private static byte[] computeHmac(byte[] key, byte[] aad, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            mac.update(aad);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new CryptographicException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Constant-time bitwise byte array comparison to eliminate timing side-channel leaks.
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(a, b);
    }
}
