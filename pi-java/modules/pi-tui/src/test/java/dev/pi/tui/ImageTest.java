package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImageTest {
    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+n+uoAAAAASUVORK5CYII=";

    private static final ImageTheme THEME = text -> "[[" + text + "]]";

    @AfterEach
    void resetCapabilities() {
        TerminalImages.resetCapabilitiesCache();
        TerminalImages.setCellDimensions(new CellDimensions(9, 18));
    }

    @Test
    void fallsBackToTextWhenImageProtocolUnavailable() {
        TerminalImages.setCapabilitiesOverride(new TerminalCapabilities(null, true, true));
        var image = new Image(
            PNG_1X1,
            "image/png",
            THEME,
            new ImageOptions(null, null, "pixel.png", null),
            new ImageDimensions(1, 1)
        );

        var lines = image.render(40);

        assertThat(lines).containsExactly("[[[Image: pixel.png image/png 1x1]]]");
    }

    @Test
    void rendersKittySequenceAcrossReservedRows() {
        TerminalImages.setCapabilitiesOverride(new TerminalCapabilities(ImageProtocol.KITTY, true, true));
        TerminalImages.setCellDimensions(new CellDimensions(10, 10));
        var image = new Image(
            PNG_1X1,
            "image/png",
            THEME,
            new ImageOptions(4, null, null, 99),
            new ImageDimensions(20, 20)
        );

        var lines = image.render(20);

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEmpty();
        assertThat(lines.getLast()).contains("\u001b[3A");
        assertThat(lines.getLast()).contains("\u001b_G");
    }

    @Test
    void detectsPngDimensionsFromBase64() {
        var dimensions = TerminalImages.getImageDimensions(PNG_1X1, "image/png");

        assertThat(dimensions).isEqualTo(new ImageDimensions(1, 1));
    }
}
