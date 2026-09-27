package ai.terravision.inference;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Letterbox resize: scales the source image to fit within targetSize x targetSize
 * while preserving its aspect ratio, then pads the leftover space with a fill color.
 *
 * <p>The original Flask prototype used a hard center-crop to square before resizing,
 * which discards whatever falls outside that square. This preserves the full frame
 * instead, at the cost of some padding pixels -- generally the better trade-off for
 * satellite tiles where content near the edges still matters.
 */
public final class ImagePreprocessor {

    /**
     * ImageNet mean (0.485, 0.456, 0.406) scaled to 0-255, used as the pad color so the
     * letterbox borders sit close to the average pixel the network saw during training,
     * rather than introducing a stark edge (e.g. pure black) it never learned to ignore.
     */
    public static final Color IMAGENET_MEAN_PAD = new Color(124, 116, 104);

    private ImagePreprocessor() {
    }

    public static BufferedImage resizeWithPadding(BufferedImage source, int targetSize) {
        return resizeWithPadding(source, targetSize, IMAGENET_MEAN_PAD);
    }

    public static BufferedImage resizeWithPadding(BufferedImage source, int targetSize, Color padColor) {
        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        double scale = Math.min((double) targetSize / srcWidth, (double) targetSize / srcHeight);
        int scaledWidth = Math.max(1, (int) Math.round(srcWidth * scale));
        int scaledHeight = Math.max(1, (int) Math.round(srcHeight * scale));

        BufferedImage canvas = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(padColor);
            g.fillRect(0, 0, targetSize, targetSize);

            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int offsetX = (targetSize - scaledWidth) / 2;
            int offsetY = (targetSize - scaledHeight) / 2;
            g.drawImage(source, offsetX, offsetY, scaledWidth, scaledHeight, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }
}
