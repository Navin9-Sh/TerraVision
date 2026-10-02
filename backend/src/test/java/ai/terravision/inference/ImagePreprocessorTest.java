package ai.terravision.inference;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePreprocessorTest {

    @Test
    void outputIsAlwaysTargetSizeSquareRegardlessOfInputShape() {
        BufferedImage wide = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImagePreprocessor.resizeWithPadding(wide, 224);

        assertThat(result.getWidth()).isEqualTo(224);
        assertThat(result.getHeight()).isEqualTo(224);
    }

    @Test
    void wideImageIsLetterboxedWithPaddingAboveAndBelow() {
        BufferedImage wide = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        fill(wide, Color.WHITE);

        BufferedImage result = ImagePreprocessor.resizeWithPadding(wide, 224);

        assertThat(result.getRGB(112, 0)).isEqualTo(ImagePreprocessor.IMAGENET_MEAN_PAD.getRGB());
    }

    @Test
    void tallImageIsLetterboxedWithPaddingOnEitherSide() {
        BufferedImage tall = new BufferedImage(100, 400, BufferedImage.TYPE_INT_RGB);
        fill(tall, Color.WHITE);

        BufferedImage result = ImagePreprocessor.resizeWithPadding(tall, 224);

        assertThat(result.getRGB(0, 112)).isEqualTo(ImagePreprocessor.IMAGENET_MEAN_PAD.getRGB());
    }

    @Test
    void squareImageContentReachesTheCornersWithNoPadding() {
        BufferedImage square = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        fill(square, Color.WHITE);

        BufferedImage result = ImagePreprocessor.resizeWithPadding(square, 224);

        assertThat(result.getRGB(0, 0)).isNotEqualTo(ImagePreprocessor.IMAGENET_MEAN_PAD.getRGB());
    }

    @Test
    void customPadColorIsRespected() {
        BufferedImage wide = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        fill(wide, Color.WHITE);

        BufferedImage result = ImagePreprocessor.resizeWithPadding(wide, 224, Color.BLACK);

        assertThat(result.getRGB(112, 0)).isEqualTo(Color.BLACK.getRGB());
    }

    private static void fill(BufferedImage image, Color color) {
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
    }
}
