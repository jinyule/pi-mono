package dev.pi.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

final class PiCliThemeLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> REQUIRED_COLOR_KEYS = Set.of("accent", "muted", "warning", "success", "error");

    private final Path cwd;
    private final Path agentDir;

    PiCliThemeLoader(Path cwd) {
        this(cwd, Path.of(System.getProperty("user.home"), ".pi", "agent"));
    }

    PiCliThemeLoader(Path cwd, Path agentDir) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.agentDir = Objects.requireNonNull(agentDir, "agentDir").toAbsolutePath().normalize();
    }

    LoadResult load(List<Path> explicitThemePaths, boolean noDiscovery) {
        var palettes = new LinkedHashMap<String, PiCliAnsi.Palette>();
        var warnings = new ArrayList<String>();

        if (!noDiscovery) {
            loadDirectory(agentDir.resolve("themes"), palettes, warnings);
            loadDirectory(cwd.resolve(".pi").resolve("themes"), palettes, warnings);
        }

        for (var themePath : explicitThemePaths == null ? List.<Path>of() : explicitThemePaths) {
            loadPath(resolvePath(themePath), palettes, warnings);
        }

        var availableThemes = new ArrayList<String>(palettes.keySet());
        availableThemes.sort(String.CASE_INSENSITIVE_ORDER);
        return new LoadResult(List.copyOf(availableThemes), Map.copyOf(palettes), List.copyOf(warnings));
    }

    private void loadDirectory(
        Path directory,
        Map<String, PiCliAnsi.Palette> palettes,
        List<String> warnings
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> loadPath(path, palettes, warnings));
        } catch (IOException exception) {
            warnings.add("Failed to read theme directory " + directory + ": " + exception.getMessage());
        }
    }

    private void loadPath(
        Path path,
        Map<String, PiCliAnsi.Palette> palettes,
        List<String> warnings
    ) {
        if (!Files.exists(path)) {
            warnings.add("Theme path not found: " + path);
            return;
        }
        if (Files.isDirectory(path)) {
            loadDirectory(path, palettes, warnings);
            return;
        }
        if (!Files.isRegularFile(path)) {
            warnings.add("Theme path is not a regular file: " + path);
            return;
        }

        try {
            var theme = parseTheme(path);
            palettes.put(theme.name(), theme.palette());
        } catch (RuntimeException | IOException exception) {
            warnings.add("Failed to load theme " + path + ": " + rootMessage(exception));
        }
    }

    private ParsedTheme parseTheme(Path path) throws IOException {
        var root = OBJECT_MAPPER.readTree(Files.readString(path));
        var themeName = root.path("name").asText("").trim();
        if (themeName.isBlank()) {
            throw new IllegalArgumentException("theme name is required");
        }

        var colors = requireObject(root, "colors");
        var vars = objectFields(root.path("vars"));
        var resolved = new LinkedHashMap<String, String>();
        for (var key : REQUIRED_COLOR_KEYS) {
            resolved.put(key, resolveColorCode(key, colors.path(key), vars, new LinkedHashSet<>()));
        }

        return new ParsedTheme(
            themeName,
            new PiCliAnsi.Palette(
                resolved.get("accent"),
                resolved.get("muted"),
                "1;" + resolved.get("accent"),
                resolved.get("warning"),
                resolved.get("success"),
                resolved.get("error")
            )
        );
    }

    private static JsonNode requireObject(JsonNode root, String field) {
        var node = root.path(field);
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return node;
    }

    private static Map<String, JsonNode> objectFields(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, JsonNode>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue()));
        return values;
    }

    private static String resolveColorCode(
        String token,
        JsonNode node,
        Map<String, JsonNode> vars,
        Set<String> resolving
    ) {
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("missing required color token: " + token);
        }
        if (node.canConvertToInt()) {
            var value = node.asInt();
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("color index out of range for " + token + ": " + value);
            }
            return "38;5;" + value;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("unsupported color value for " + token);
        }

        var value = node.asText();
        if (value.isEmpty()) {
            return "39";
        }
        if (isHexColor(value)) {
            return hexToAnsi(value);
        }
        var varName = value.trim();
        if (!resolving.add(varName)) {
            throw new IllegalArgumentException("cyclic color reference: " + varName);
        }
        try {
            var referenced = vars.get(varName);
            if (referenced == null) {
                throw new IllegalArgumentException("unknown color reference for " + token + ": " + value);
            }
            return resolveColorCode(token, referenced, vars, resolving);
        } finally {
            resolving.remove(varName);
        }
    }

    private static boolean isHexColor(String value) {
        if (value.length() != 7 || value.charAt(0) != '#') {
            return false;
        }
        for (var index = 1; index < value.length(); index += 1) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String hexToAnsi(String value) {
        var red = Integer.parseInt(value.substring(1, 3), 16);
        var green = Integer.parseInt(value.substring(3, 5), 16);
        var blue = Integer.parseInt(value.substring(5, 7), 16);
        return "38;2;" + red + ";" + green + ";" + blue;
    }

    private Path resolvePath(Path path) {
        Objects.requireNonNull(path, "path");
        return path.isAbsolute() ? path.normalize() : cwd.resolve(path).normalize();
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    record LoadResult(
        List<String> availableThemes,
        Map<String, PiCliAnsi.Palette> palettes,
        List<String> warnings
    ) {
        LoadResult {
            availableThemes = List.copyOf(Objects.requireNonNull(availableThemes, "availableThemes"));
            palettes = Map.copyOf(Objects.requireNonNull(palettes, "palettes"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }

    private record ParsedTheme(String name, PiCliAnsi.Palette palette) {
    }
}
