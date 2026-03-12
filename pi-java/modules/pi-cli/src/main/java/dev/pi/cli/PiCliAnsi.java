package dev.pi.cli;

final class PiCliAnsi {
    private static volatile Palette palette = Palette.DARK;

    private PiCliAnsi() {
    }

    static void setTheme(String themeName) {
        palette = "light".equalsIgnoreCase(themeName) ? Palette.LIGHT : Palette.DARK;
    }

    static String accent(String text) {
        return style(palette.accent(), text);
    }

    static String muted(String text) {
        return style(palette.muted(), text);
    }

    static String bold(String text) {
        return style("1", text);
    }

    static String accentBold(String text) {
        return style(palette.accentBold(), text);
    }

    static String warning(String text) {
        return style(palette.warning(), text);
    }

    static String success(String text) {
        return style(palette.success(), text);
    }

    static String error(String text) {
        return style(palette.error(), text);
    }

    private static String style(String code, String text) {
        return "\u001b[" + code + "m" + text + "\u001b[0m";
    }

    private record Palette(
        String accent,
        String muted,
        String accentBold,
        String warning,
        String success,
        String error
    ) {
        private static final Palette DARK = new Palette("36", "90", "1;36", "33", "32", "31");
        private static final Palette LIGHT = new Palette("34", "2;30", "1;34", "33", "32", "31");
    }
}
