package dev.pi.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PiChangelog {
    private static final Pattern VERSION_HEADER = Pattern.compile("^##\\s+\\[?(\\d+)\\.(\\d+)\\.(\\d+)\\]?.*$");

    private PiChangelog() {
    }

    static String render(String cwd) {
        var changelogPath = resolve(cwd);
        var entries = changelogPath == null ? List.<Entry>of() : parse(changelogPath);
        if (entries.isEmpty()) {
            return "What's New\n\nNo changelog entries found.";
        }
        var content = entries.stream()
            .sorted(Comparator.comparingInt(Entry::major)
                .thenComparingInt(Entry::minor)
                .thenComparingInt(Entry::patch)
                .reversed())
            .map(Entry::content)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("No changelog entries found.");
        return "What's New\n\n" + content;
    }

    static Path resolve(String cwd) {
        Path start;
        try {
            start = cwd == null || cwd.isBlank()
                ? Path.of("").toAbsolutePath()
                : Path.of(cwd).toAbsolutePath();
        } catch (RuntimeException exception) {
            start = Path.of("").toAbsolutePath();
        }

        var current = start;
        while (current != null) {
            var packageChangelog = current.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md");
            if (Files.isRegularFile(packageChangelog)) {
                return packageChangelog;
            }
            var rootChangelog = current.resolve("CHANGELOG.md");
            if (Files.isRegularFile(rootChangelog)) {
                return rootChangelog;
            }
            current = current.getParent();
        }
        return null;
    }

    static List<Entry> parse(Path changelogPath) {
        if (changelogPath == null || !Files.isRegularFile(changelogPath)) {
            return List.of();
        }

        try {
            var lines = Files.readString(changelogPath, StandardCharsets.UTF_8).split("\\R", -1);
            var entries = new ArrayList<Entry>();
            var currentLines = new ArrayList<String>();
            EntryVersion currentVersion = null;

            for (var line : lines) {
                if (line.startsWith("## ")) {
                    if (currentVersion != null && !currentLines.isEmpty()) {
                        entries.add(new Entry(currentVersion.major(), currentVersion.minor(), currentVersion.patch(), String.join("\n", currentLines).trim()));
                    }
                    var matcher = VERSION_HEADER.matcher(line);
                    if (matcher.matches()) {
                        currentVersion = new EntryVersion(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                        );
                        currentLines.clear();
                        currentLines.add(line);
                    } else {
                        currentVersion = null;
                        currentLines.clear();
                    }
                    continue;
                }

                if (currentVersion != null) {
                    currentLines.add(line);
                }
            }

            if (currentVersion != null && !currentLines.isEmpty()) {
                entries.add(new Entry(currentVersion.major(), currentVersion.minor(), currentVersion.patch(), String.join("\n", currentLines).trim()));
            }
            return List.copyOf(entries);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read changelog", exception);
        }
    }

    record Entry(
        int major,
        int minor,
        int patch,
        String content
    ) {
    }

    private record EntryVersion(
        int major,
        int minor,
        int patch
    ) {
    }
}
