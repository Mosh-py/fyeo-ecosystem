package io.fyeo.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Aspect-Ratio Preserving Memory Budgeter.
 * 
 * Prevents OutOfMemoryError (OOM) and PNG expansion blowouts by bounding
 * the total pixel count before visual cryptographic permutation.
 */
public final class PixelBudgeter {

    public static final int DEFAULT_MAX_PIXELS = 500_000; // ~840x595 pixels (~1.5 MB uncompressed bitmap)

    private PixelBudgeter() {}

    /**
     * Downscales an image strictly if its total pixel count exceeds {@code maxPixels},
     * preserving its exact geometric aspect ratio.
     *
     * @param original  The source BufferedImage
     * @param maxPixels Upper bound on total pixels (width * height)
     * @return Budgeted BufferedImage (original instance if already within budget)
     */
    public static BufferedImage enforceBudget(BufferedImage original, int maxPixels) {
        if (original == null) {
            throw new IllegalArgumentException("Original image cannot be null");
        }

        int origW = original.getWidth();
        int origH = original.getHeight();
        long totalPixels = (long) origW * origH;

        if (totalPixels <= maxPixels) {
            return original;
        }

        double ratio = (double) origW / origH;
        int targetW = (int) Math.round(Math.sqrt(maxPixels * ratio));
        int targetH = (int) Math.round(targetW / ratio);

        targetW = Math.max(1, targetW);
        targetH = Math.max(1, targetH);

        // Render using high-quality bicubic interpolation
        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, targetW, targetH, null);
        } finally {
            g2d.dispose();
        }

        return resized;
    }
}
