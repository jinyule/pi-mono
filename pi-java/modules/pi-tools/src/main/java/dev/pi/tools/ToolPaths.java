package dev.pi.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ToolPaths {
    private static final Pattern UNICODE_SPACES = Pattern.compile("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]");
    private static final String NARROW_NO_BREAK_SPACE = "\u202F";

    private ToolPaths() {
    }

    public static String expandPath(String filePath) {
        return expandPath(filePath, Path.of(System.getProperty("user.home")));
    }

    public static String expandPath(String filePath, Path homeDir) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(homeDir, "homeDir");
        var normalized = normalizeUnicodeSpaces(normalizeAtPrefix(filePath));
        if ("~".equals(normalized)) {
            return homeDir.toString();
        }
        if (normalized.startsWith("~/") || normalized.startsWith("~\\")) {
            return homeDir.resolve(normalized.substring(2)).normalize().toString();
        }
        return normalized;
    }

    public static Path resolveToCwd(String filePath, Path cwd) {
        Objects.requireNonNull(cwd, "cwd");
        var expanded = Path.of(expandPath(filePath));
        if (expanded.isAbsolute()) {
            return expanded.normalize();
        }
        return cwd.resolve(expanded).normalize();
    }

    public static Path resolveReadPath(String filePath, Path cwd) {
        var resolved = resolveToCwd(filePath, cwd);
        if (Files.exists(resolved)) {
            return resolved;
        }

        var amPmVariant = Path.of(tryMacOsScreenshotPath(resolved.toString()));
        if (!amPmVariant.equals(resolved) && Files.exists(amPmVariant)) {
            return amPmVariant;
        }

        var nfdVariant = Path.of(tryNfdVariant(resolved.toString()));
        if (!nfdVariant.equals(resolved) && Files.exists(nfdVariant)) {
            return nfdVariant;
        }

        var curlyVariant = Path.of(tryCurlyQuoteVariant(resolved.toString()));
        if (!curlyVariant.equals(resolved) && Files.exists(curlyVariant)) {
            return curlyVariant;
        }

        var nfdCurlyVariant = Path.of(tryCurlyQuoteVariant(nfdVariant.toString()));
        if (!nfdCurlyVariant.equals(resolved) && Files.exists(nfdCurlyVariant)) {
            return nfdCurlyVariant;
        }

        return resolved;
    }

    private static String normalizeUnicodeSpaces(String value) {
        return UNICODE_SPACES.matcher(value).replaceAll(" ");
    }

    private static String normalizeAtPrefix(String value) {
        return value.startsWith("@") ? value.substring(1) : value;
    }

    private static String tryMacOsScreenshotPath(String filePath) {
        return filePath.replaceAll(" (AM|PM)\\.", NARROW_NO_BREAK_SPACE + "$1.");
    }

    private static String tryNfdVariant(String filePath) {
        return Normalizer.normalize(filePath, Normalizer.Form.NFD);
    }

    private static String tryCurlyQuoteVariant(String filePath) {
        return filePath.replace("'", "\u2019");
    }
}
