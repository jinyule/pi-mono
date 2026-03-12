package dev.pi.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PiCliAnsi {
    private static final Map<String, Palette> BUILTIN_PALETTES = Map.of(
        "dark", Palette.DARK,
        "light", Palette.LIGHT
    );

    private static volatile Map<String, Palette> registeredPalettes = Map.of();
    private static volatile Palette palette = Palette.DARK;

    private PiCliAnsi() {
    }

    static void setTheme(String themeName) {
        palette = resolvePalette(themeName);
    }

    static void setRegisteredThemes(Map<String, Palette> palettes) {
        registeredPalettes = palettes == null ? Map.of() : Map.copyOf(palettes);
        palette = resolvePalette("dark");
    }

    static List<String> availableThemeNames() {
        var names = new ArrayList<String>();
        names.addAll(BUILTIN_PALETTES.keySet());
        for (var name : registeredPalettes.keySet()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    static void resetThemes() {
        registeredPalettes = Map.of();
        palette = Palette.DARK;
    }

    static String accent(String text) {
        return style(palette.accent(), text);
    }

    static String muted(String text) {
        return style(palette.muted(), text);
    }

    static String dim(String text) {
        return style(palette.dim(), text);
    }

    static String text(String text) {
        return style(palette.text(), text);
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

    static String border(String text) {
        return style(palette.border(), text);
    }

    static String borderMuted(String text) {
        return style(palette.borderMuted(), text);
    }

    private static String style(String code, String text) {
        return "\u001b[" + code + "m" + text + "\u001b[0m";
    }

    private static Palette resolvePalette(String themeName) {
        if (themeName != null) {
            for (var entry : registeredPalettes.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(themeName)) {
                    return entry.getValue();
                }
            }
            for (var entry : BUILTIN_PALETTES.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(themeName)) {
                    return entry.getValue();
                }
            }
        }
        return Palette.DARK;
    }

    static record Palette(
        String accent,
        String muted,
        String accentBold,
        String warning,
        String success,
        String error,
        String dim,
        String text,
        String border,
        String borderMuted
    ) {
        static final Palette DARK = new Palette("36", "90", "1;36", "33", "32", "31", "2;37", "39", "36", "90");
        static final Palette LIGHT = new Palette("34", "2;30", "1;34", "33", "32", "31", "2;30", "39", "34", "2;30");

        Palette(String accent, String muted, String accentBold, String warning, String success, String error) {
            this(accent, muted, accentBold, warning, success, error, muted, "39", accent, muted);
        }
    }
}
