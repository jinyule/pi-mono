package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Image implements Component {
    private final String base64Data;
    private final String mimeType;
    private final ImageTheme theme;
    private final ImageOptions options;
    private final ImageDimensions dimensions;

    private Integer imageId;
    private List<String> cachedLines;
    private Integer cachedWidth;

    public Image(String base64Data, String mimeType, ImageTheme theme) {
        this(base64Data, mimeType, theme, ImageOptions.defaults(), null);
    }

    public Image(String base64Data, String mimeType, ImageTheme theme, ImageOptions options, ImageDimensions dimensions) {
        this.base64Data = Objects.requireNonNull(base64Data, "base64Data");
        this.mimeType = mimeType == null ? "image/png" : mimeType;
        this.theme = Objects.requireNonNull(theme, "theme");
        this.options = options == null ? ImageOptions.defaults() : options;
        this.dimensions = dimensions != null
            ? dimensions
            : defaultDimensions(TerminalImages.getImageDimensions(base64Data, this.mimeType));
        this.imageId = this.options.imageId();
    }

    public Integer getImageId() {
        return imageId;
    }

    @Override
    public void invalidate() {
        cachedLines = null;
        cachedWidth = null;
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && Integer.valueOf(width).equals(cachedWidth)) {
            return cachedLines;
        }

        var maxWidth = Math.min(Math.max(1, width - 2), options.maxWidthCells() == null ? 60 : options.maxWidthCells());
        var rendered = TerminalImages.renderImage(
            base64Data,
            dimensions,
            new ImageRenderOptions(maxWidth, options.maxHeightCells(), true, imageId)
        );

        List<String> lines;
        if (rendered != null) {
            if (rendered.imageId() != null) {
                imageId = rendered.imageId();
            }
            lines = new ArrayList<>();
            for (var index = 0; index < rendered.rows() - 1; index += 1) {
                lines.add("");
            }
            var moveUp = rendered.rows() > 1 ? "\u001b[" + (rendered.rows() - 1) + "A" : "";
            lines.add(moveUp + rendered.sequence());
        } else {
            var fallback = TerminalImages.imageFallback(mimeType, dimensions, options.filename());
            lines = List.of(theme.fallbackColor(fallback));
        }

        cachedLines = List.copyOf(lines);
        cachedWidth = width;
        return cachedLines;
    }

    private static ImageDimensions defaultDimensions(ImageDimensions dimensions) {
        return dimensions == null ? new ImageDimensions(800, 600) : dimensions;
    }
}
