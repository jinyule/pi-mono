package dev.pi.cli;

import dev.pi.ai.model.ImageContent;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

@FunctionalInterface
interface PiClipboardImage {
    ImageContent read();

    static PiClipboardImage system() {
        return () -> {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("System clipboard unavailable in headless environment");
            }
            try {
                var contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    return null;
                }
                var image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                if (image == null) {
                    return null;
                }

                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(toBufferedImage(image), "png", output)) {
                    throw new IllegalStateException("Failed to encode clipboard image");
                }
                return new ImageContent(
                    Base64.getEncoder().encodeToString(output.toByteArray()),
                    "image/png"
                );
            } catch (UnsupportedFlavorException exception) {
                return null;
            } catch (IllegalStateException exception) {
                throw new IllegalStateException("System clipboard unavailable", exception);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read clipboard image", exception);
            }
        };
    }

    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage bufferedImage) {
            return bufferedImage;
        }
        var bufferedImage = new BufferedImage(
            image.getWidth(null),
            image.getHeight(null),
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = bufferedImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return bufferedImage;
    }
}
