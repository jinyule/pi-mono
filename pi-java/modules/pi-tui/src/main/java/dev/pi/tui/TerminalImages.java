package dev.pi.tui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

public final class TerminalImages {
    private static final String KITTY_PREFIX = "\u001b_G";
    private static final String ITERM2_PREFIX = "\u001b]1337;File=";

    private static volatile TerminalCapabilities cachedCapabilities;
    private static volatile TerminalCapabilities capabilitiesOverride;
    private static volatile CellDimensions cellDimensions = new CellDimensions(9, 18);

    private TerminalImages() {
    }

    public static CellDimensions getCellDimensions() {
        return cellDimensions;
    }

    public static void setCellDimensions(CellDimensions dimensions) {
        cellDimensions = Objects.requireNonNull(dimensions, "dimensions");
    }

    public static TerminalCapabilities detectCapabilities() {
        Map<String, String> env = System.getenv();
        var termProgram = env.getOrDefault("TERM_PROGRAM", "").toLowerCase();
        var term = env.getOrDefault("TERM", "").toLowerCase();
        var colorTerm = env.getOrDefault("COLORTERM", "").toLowerCase();

        if (env.containsKey("KITTY_WINDOW_ID") || "kitty".equals(termProgram)) {
            return new TerminalCapabilities(ImageProtocol.KITTY, true, true);
        }
        if ("ghostty".equals(termProgram) || term.contains("ghostty") || env.containsKey("GHOSTTY_RESOURCES_DIR")) {
            return new TerminalCapabilities(ImageProtocol.KITTY, true, true);
        }
        if (env.containsKey("WEZTERM_PANE") || "wezterm".equals(termProgram)) {
            return new TerminalCapabilities(ImageProtocol.KITTY, true, true);
        }
        if (env.containsKey("ITERM_SESSION_ID") || "iterm.app".equals(termProgram)) {
            return new TerminalCapabilities(ImageProtocol.ITERM2, true, true);
        }
        if ("vscode".equals(termProgram) || "alacritty".equals(termProgram)) {
            return new TerminalCapabilities(null, true, true);
        }
        var trueColor = "truecolor".equals(colorTerm) || "24bit".equals(colorTerm);
        return new TerminalCapabilities(null, trueColor, true);
    }

    public static TerminalCapabilities getCapabilities() {
        if (capabilitiesOverride != null) {
            return capabilitiesOverride;
        }
        if (cachedCapabilities == null) {
            cachedCapabilities = detectCapabilities();
        }
        return cachedCapabilities;
    }

    public static void resetCapabilitiesCache() {
        cachedCapabilities = null;
        capabilitiesOverride = null;
    }

    static void setCapabilitiesOverride(TerminalCapabilities capabilities) {
        capabilitiesOverride = capabilities;
    }

    public static boolean isImageLine(String line) {
        return line.startsWith(KITTY_PREFIX)
            || line.startsWith(ITERM2_PREFIX)
            || line.contains(KITTY_PREFIX)
            || line.contains(ITERM2_PREFIX);
    }

    public static int allocateImageId() {
        return ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    }

    public static String encodeKitty(String base64Data, Integer columns, Integer rows, Integer imageId) {
        final int chunkSize = 4096;
        var params = new StringBuilder("a=T,f=100,q=2");
        if (columns != null) {
            params.append(",c=").append(columns);
        }
        if (rows != null) {
            params.append(",r=").append(rows);
        }
        if (imageId != null) {
            params.append(",i=").append(imageId);
        }
        if (base64Data.length() <= chunkSize) {
            return "\u001b_G" + params + ";" + base64Data + "\u001b\\";
        }

        var result = new StringBuilder();
        var offset = 0;
        var first = true;
        while (offset < base64Data.length()) {
            var end = Math.min(base64Data.length(), offset + chunkSize);
            var chunk = base64Data.substring(offset, end);
            var last = end >= base64Data.length();
            if (first) {
                result.append("\u001b_G").append(params).append(",m=1;").append(chunk).append("\u001b\\");
                first = false;
            } else if (last) {
                result.append("\u001b_Gm=0;").append(chunk).append("\u001b\\");
            } else {
                result.append("\u001b_Gm=1;").append(chunk).append("\u001b\\");
            }
            offset = end;
        }
        return result.toString();
    }

    public static String deleteKittyImage(int imageId) {
        return "\u001b_Ga=d,d=I,i=" + imageId + "\u001b\\";
    }

    public static String deleteAllKittyImages() {
        return "\u001b_Ga=d,d=A\u001b\\";
    }

    public static String encodeITerm2(String base64Data, Integer width, String height, String name, boolean preserveAspectRatio, boolean inline) {
        var params = new StringBuilder("inline=").append(inline ? 1 : 0);
        if (width != null) {
            params.append(";width=").append(width);
        }
        if (height != null) {
            params.append(";height=").append(height);
        }
        if (name != null && !name.isBlank()) {
            params.append(";name=").append(Base64.getEncoder().encodeToString(name.getBytes()));
        }
        if (!preserveAspectRatio) {
            params.append(";preserveAspectRatio=0");
        }
        return "\u001b]1337;File=" + params + ":" + base64Data + "\u0007";
    }

    public static int calculateImageRows(ImageDimensions imageDimensions, int targetWidthCells) {
        var targetWidthPx = targetWidthCells * cellDimensions.widthPx();
        var scale = (double) targetWidthPx / imageDimensions.widthPx();
        var scaledHeightPx = imageDimensions.heightPx() * scale;
        return Math.max(1, (int) Math.ceil(scaledHeightPx / cellDimensions.heightPx()));
    }

    public static ImageDimensions getImageDimensions(String base64Data, String mimeType) {
        try {
            var bytes = Base64.getDecoder().decode(base64Data);
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    public static RenderedImage renderImage(String base64Data, ImageDimensions imageDimensions, ImageRenderOptions options) {
        var capabilities = getCapabilities();
        if (capabilities.images() == null) {
            return null;
        }

        var appliedOptions = options == null ? ImageRenderOptions.defaults() : options;
        var maxWidth = appliedOptions.maxWidthCells() == null ? 80 : appliedOptions.maxWidthCells();
        var rows = calculateImageRows(imageDimensions, maxWidth);
        if (appliedOptions.maxHeightCells() != null) {
            rows = Math.min(rows, appliedOptions.maxHeightCells());
        }

        if (capabilities.images() == ImageProtocol.KITTY) {
            var sequence = encodeKitty(base64Data, maxWidth, rows, appliedOptions.imageId());
            return new RenderedImage(sequence, rows, appliedOptions.imageId());
        }
        if (capabilities.images() == ImageProtocol.ITERM2) {
            var sequence = encodeITerm2(base64Data, maxWidth, "auto", null, appliedOptions.preserveAspectRatio(), true);
            return new RenderedImage(sequence, rows, null);
        }
        return null;
    }

    public static String imageFallback(String mimeType, ImageDimensions dimensions, String filename) {
        var builder = new StringBuilder("[Image: ");
        if (filename != null && !filename.isBlank()) {
            builder.append(filename).append(' ');
        }
        builder.append(mimeType);
        if (dimensions != null) {
            builder.append(' ').append(dimensions.widthPx()).append('x').append(dimensions.heightPx());
        }
        builder.append(']');
        return builder.toString();
    }

    public record RenderedImage(
        String sequence,
        int rows,
        Integer imageId
    ) {
    }
}
