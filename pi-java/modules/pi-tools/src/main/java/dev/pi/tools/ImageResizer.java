package dev.pi.tools;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class ImageResizer {
    public ResizedImage resize(byte[] data, String mimeType, ImageResizeOptions options) {
        Objects.requireNonNull(data, "data");
        var appliedOptions = options == null ? ImageResizeOptions.defaults() : options;

        try {
            var image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                return unreadable(data, mimeType);
            }

            var originalWidth = image.getWidth();
            var originalHeight = image.getHeight();
            if (originalWidth <= appliedOptions.maxWidth()
                && originalHeight <= appliedOptions.maxHeight()
                && data.length <= appliedOptions.maxBytes()) {
                return new ResizedImage(
                    data,
                    defaultMimeType(mimeType),
                    originalWidth,
                    originalHeight,
                    originalWidth,
                    originalHeight,
                    false
                );
            }

            var targetWidth = originalWidth;
            var targetHeight = originalHeight;
            if (targetWidth > appliedOptions.maxWidth()) {
                targetHeight = Math.round((float) targetHeight * appliedOptions.maxWidth() / targetWidth);
                targetWidth = appliedOptions.maxWidth();
            }
            if (targetHeight > appliedOptions.maxHeight()) {
                targetWidth = Math.round((float) targetWidth * appliedOptions.maxHeight() / targetHeight);
                targetHeight = appliedOptions.maxHeight();
            }

            Candidate best = tryBothFormats(image, targetWidth, targetHeight, appliedOptions.jpegQuality());
            var finalWidth = targetWidth;
            var finalHeight = targetHeight;
            if (best.data.length <= appliedOptions.maxBytes()) {
                return resized(best, originalWidth, originalHeight, finalWidth, finalHeight);
            }

            for (var quality : List.of(85, 70, 55, 40)) {
                best = tryBothFormats(image, targetWidth, targetHeight, quality);
                if (best.data.length <= appliedOptions.maxBytes()) {
                    return resized(best, originalWidth, originalHeight, finalWidth, finalHeight);
                }
            }

            for (var scale : List.of(1.0, 0.75, 0.5, 0.35, 0.25)) {
                finalWidth = Math.max(1, (int) Math.round(targetWidth * scale));
                finalHeight = Math.max(1, (int) Math.round(targetHeight * scale));
                if (finalWidth < 100 || finalHeight < 100) {
                    break;
                }

                for (var quality : List.of(85, 70, 55, 40)) {
                    best = tryBothFormats(image, finalWidth, finalHeight, quality);
                    if (best.data.length <= appliedOptions.maxBytes()) {
                        return resized(best, originalWidth, originalHeight, finalWidth, finalHeight);
                    }
                }
            }

            return resized(best, originalWidth, originalHeight, finalWidth, finalHeight);
        } catch (IOException exception) {
            return unreadable(data, mimeType);
        }
    }

    public static String formatDimensionNote(ResizedImage result) {
        Objects.requireNonNull(result, "result");
        if (!result.wasResized()) {
            return null;
        }
        var scale = (double) result.originalWidth() / result.width();
        return "[Image: original %dx%d, displayed at %dx%d. Multiply coordinates by %.2f to map to original image.]"
            .formatted(result.originalWidth(), result.originalHeight(), result.width(), result.height(), scale);
    }

    private static ResizedImage resized(Candidate candidate, int originalWidth, int originalHeight, int width, int height) {
        return new ResizedImage(candidate.data, candidate.mimeType, originalWidth, originalHeight, width, height, true);
    }

    private static Candidate tryBothFormats(BufferedImage image, int width, int height, int jpegQuality) throws IOException {
        var resized = resize(image, width, height);
        var png = new Candidate(writePng(resized), "image/png");
        var jpeg = new Candidate(writeJpeg(resized, jpegQuality), "image/jpeg");
        return png.data.length <= jpeg.data.length ? png : jpeg;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        var hasAlpha = source.getColorModel().hasAlpha() || source.getTransparency() != Transparency.OPAQUE;
        var imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var resized = new BufferedImage(width, height, imageType);
        var graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] writeJpeg(BufferedImage image, int jpegQuality) throws IOException {
        var rgbImage = image.getType() == BufferedImage.TYPE_INT_RGB ? image : toRgb(image);
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return writePng(image);
        }

        ImageWriter writer = writers.next();
        try (var output = new ByteArrayOutputStream(); var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            var params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(jpegQuality / 100.0f);
            }
            writer.write(null, new IIOImage(rgbImage, null, null), params);
            writer.dispose();
            return output.toByteArray();
        }
    }

    private static BufferedImage toRgb(BufferedImage source) {
        var rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static ResizedImage unreadable(byte[] data, String mimeType) {
        return new ResizedImage(data, defaultMimeType(mimeType), 0, 0, 0, 0, false);
    }

    private static String defaultMimeType(String mimeType) {
        return mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
    }

    private record Candidate(
        byte[] data,
        String mimeType
    ) {
    }
}
