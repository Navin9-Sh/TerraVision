package ai.terravision.inference;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class ImagePreprocessor {

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
