package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageResizerTest {
    @Test
    void returnsOriginalImageWhenAlreadyWithinLimits() throws IOException {
        var png = pngBytes(32, 32, false);

        var result = new ImageResizer().resize(png, "image/png", new ImageResizeOptions(100, 100, 1024 * 1024, 80));

        assertThat(result.wasResized()).isFalse();
        assertThat(result.data()).isEqualTo(png);
        assertThat(result.originalWidth()).isEqualTo(32);
        assertThat(result.width()).isEqualTo(32);
    }

    @Test
    void resizesImageWhenDimensionsExceedLimit() throws IOException {
        var png = pngBytes(300, 150, false);

        var result = new ImageResizer().resize(png, "image/png", new ImageResizeOptions(100, 100, 1024 * 1024, 80));

        assertThat(result.wasResized()).isTrue();
        assertThat(result.originalWidth()).isEqualTo(300);
        assertThat(result.originalHeight()).isEqualTo(150);
        assertThat(result.width()).isLessThanOrEqualTo(100);
        assertThat(result.height()).isLessThanOrEqualTo(100);
    }

    @Test
    void reducesPayloadWhenByteLimitIsExceeded() throws IOException {
        var png = pngBytes(240, 240, true);

        var result = new ImageResizer().resize(png, "image/png", new ImageResizeOptions(2000, 2000, png.length / 3, 80));

        assertThat(result.wasResized()).isTrue();
        assertThat(result.data().length).isLessThan(png.length);
    }

    @Test
    void formatDimensionNoteExplainsCoordinateMapping() {
        var note = ImageResizer.formatDimensionNote(new ResizedImage(
            new byte[0],
            "image/png",
            2000,
            1000,
            1000,
            500,
            true
        ));

        assertThat(note).contains("original 2000x1000");
        assertThat(note).contains("displayed at 1000x500");
        assertThat(note).contains("2.00");
    }

    @Test
    void returnsOriginalBytesForUnreadableImages() {
        var result = new ImageResizer().resize("not-an-image".getBytes(), "image/png", new ImageResizeOptions(100, 100, 100, 80));

        assertThat(result.wasResized()).isFalse();
        assertThat(result.originalWidth()).isZero();
        assertThat(result.data()).isEqualTo("not-an-image".getBytes());
    }

    private static byte[] pngBytes(int width, int height, boolean noisy) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new Random(1234);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (noisy) {
                    image.setRGB(x, y, new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)).getRGB());
                } else {
                    image.setRGB(x, y, new Color((x * 255) / width, (y * 255) / height, 128).getRGB());
                }
            }
        }

        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
