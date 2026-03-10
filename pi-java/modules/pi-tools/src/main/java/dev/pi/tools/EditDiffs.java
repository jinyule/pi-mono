package dev.pi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EditDiffs {
    private EditDiffs() {
    }

    public static String detectLineEnding(String content) {
        Objects.requireNonNull(content, "content");
        var crlfIndex = content.indexOf("\r\n");
        var lfIndex = content.indexOf('\n');
        if (lfIndex == -1 || crlfIndex == -1) {
            return "\n";
        }
        return crlfIndex < lfIndex ? "\r\n" : "\n";
    }

    public static String normalizeToLf(String text) {
        Objects.requireNonNull(text, "text");
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String restoreLineEndings(String text, String ending) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(ending, "ending");
        return "\r\n".equals(ending) ? text.replace("\n", "\r\n") : text;
    }

    public static String normalizeForFuzzyMatch(String text) {
        Objects.requireNonNull(text, "text");
        var normalized = String.join("\n", stripTrailingPerLine(text));
        return normalized
            .replaceAll("[\\u2018\\u2019\\u201A\\u201B]", "'")
            .replaceAll("[\\u201C\\u201D\\u201E\\u201F]", "\"")
            .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-")
            .replaceAll("[\\u00A0\\u2002-\\u200A\\u202F\\u205F\\u3000]", " ");
    }

    public static FuzzyMatchResult fuzzyFindText(String content, String oldText) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(oldText, "oldText");
        var exactIndex = content.indexOf(oldText);
        if (exactIndex >= 0) {
            return new FuzzyMatchResult(true, exactIndex, oldText.length(), false, content);
        }

        var fuzzyContent = normalizeForFuzzyMatch(content);
        var fuzzyOldText = normalizeForFuzzyMatch(oldText);
        var fuzzyIndex = fuzzyContent.indexOf(fuzzyOldText);
        if (fuzzyIndex < 0) {
            return new FuzzyMatchResult(false, -1, 0, false, content);
        }

        return new FuzzyMatchResult(true, fuzzyIndex, fuzzyOldText.length(), true, fuzzyContent);
    }

    public static BomStrippedText stripBom(String content) {
        Objects.requireNonNull(content, "content");
        if (content.startsWith("\uFEFF")) {
            return new BomStrippedText("\uFEFF", content.substring(1));
        }
        return new BomStrippedText("", content);
    }

    public static EditDiffResult generateDiffString(String oldContent, String newContent) {
        return generateDiffString(oldContent, newContent, 4);
    }

    public static EditDiffResult generateDiffString(String oldContent, String newContent, int contextLines) {
        Objects.requireNonNull(oldContent, "oldContent");
        Objects.requireNonNull(newContent, "newContent");
        var parts = diffLines(oldContent, newContent);
        var output = new ArrayList<String>();

        var oldLines = splitDisplayLines(oldContent);
        var newLines = splitDisplayLines(newContent);
        var maxLineNum = Math.max(oldLines.size(), newLines.size());
        var lineNumWidth = Integer.toString(Math.max(1, maxLineNum)).length();

        var oldLineNum = 1;
        var newLineNum = 1;
        var lastWasChange = false;
        Integer firstChangedLine = null;

        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (part.kind() == DiffKind.ADDED || part.kind() == DiffKind.REMOVED) {
                if (firstChangedLine == null) {
                    firstChangedLine = newLineNum;
                }

                for (var line : part.lines()) {
                    if (part.kind() == DiffKind.ADDED) {
                        output.add("+" + pad(newLineNum, lineNumWidth) + " " + line);
                        newLineNum++;
                    } else {
                        output.add("-" + pad(oldLineNum, lineNumWidth) + " " + line);
                        oldLineNum++;
                    }
                }

                lastWasChange = true;
                continue;
            }

            var nextPartIsChange = index < parts.size() - 1 && parts.get(index + 1).kind() != DiffKind.CONTEXT;
            if (lastWasChange || nextPartIsChange) {
                var linesToShow = new ArrayList<>(part.lines());
                var skipStart = 0;
                var skipEnd = 0;

                if (!lastWasChange) {
                    skipStart = Math.max(0, linesToShow.size() - contextLines);
                    linesToShow = new ArrayList<>(linesToShow.subList(skipStart, linesToShow.size()));
                }

                if (!nextPartIsChange && linesToShow.size() > contextLines) {
                    skipEnd = linesToShow.size() - contextLines;
                    linesToShow = new ArrayList<>(linesToShow.subList(0, contextLines));
                }

                if (skipStart > 0) {
                    output.add(" " + " ".repeat(lineNumWidth) + " ...");
                    oldLineNum += skipStart;
                    newLineNum += skipStart;
                }

                for (var line : linesToShow) {
                    output.add(" " + pad(oldLineNum, lineNumWidth) + " " + line);
                    oldLineNum++;
                    newLineNum++;
                }

                if (skipEnd > 0) {
                    output.add(" " + " ".repeat(lineNumWidth) + " ...");
                    oldLineNum += skipEnd;
                    newLineNum += skipEnd;
                }
            } else {
                oldLineNum += part.lines().size();
                newLineNum += part.lines().size();
            }

            lastWasChange = false;
        }

        return new EditDiffResult(String.join("\n", output), firstChangedLine);
    }

    public static EditDiffPreview computeEditDiff(String path, String oldText, String newText, Path cwd) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(oldText, "oldText");
        Objects.requireNonNull(newText, "newText");
        Objects.requireNonNull(cwd, "cwd");

        var absolutePath = ToolPaths.resolveToCwd(path, cwd);
        try {
            if (!Files.isReadable(absolutePath)) {
                return new EditDiffError("File not found: " + path);
            }

            var rawContent = Files.readString(absolutePath, StandardCharsets.UTF_8);
            var content = stripBom(rawContent).text();
            var normalizedContent = normalizeToLf(content);
            var normalizedOldText = normalizeToLf(oldText);
            var normalizedNewText = normalizeToLf(newText);
            var matchResult = fuzzyFindText(normalizedContent, normalizedOldText);

            if (!matchResult.found()) {
                return new EditDiffError(
                    "Could not find the exact text in %s. The old text must match exactly including all whitespace and newlines."
                        .formatted(path)
                );
            }

            var occurrences = countOccurrences(normalizeForFuzzyMatch(normalizedContent), normalizeForFuzzyMatch(normalizedOldText));
            if (occurrences > 1) {
                return new EditDiffError(
                    "Found %d occurrences of the text in %s. The text must be unique. Please provide more context to make it unique."
                        .formatted(occurrences, path)
                );
            }

            var baseContent = matchResult.contentForReplacement();
            var newContent = baseContent.substring(0, matchResult.index())
                + normalizedNewText
                + baseContent.substring(matchResult.index() + matchResult.matchLength());

            if (baseContent.equals(newContent)) {
                return new EditDiffError(
                    "No changes would be made to %s. The replacement produces identical content.".formatted(path)
                );
            }

            return generateDiffString(baseContent, newContent);
        } catch (IOException | RuntimeException exception) {
            return new EditDiffError(exception.getMessage() == null ? exception.toString() : exception.getMessage());
        }
    }

    private static int countOccurrences(String content, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        var count = 0;
        var index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static List<String> stripTrailingPerLine(String text) {
        var lines = text.split("\n", -1);
        var result = new ArrayList<String>(lines.length);
        for (var line : lines) {
            result.add(line.stripTrailing());
        }
        return result;
    }

    private static String pad(int lineNumber, int width) {
        return String.format("%" + width + "d", lineNumber);
    }

    private static List<String> splitDisplayLines(String content) {
        var lines = new ArrayList<>(List.of(content.split("\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    private static List<DiffPart> diffLines(String oldContent, String newContent) {
        var oldLines = splitDisplayLines(oldContent);
        var newLines = splitDisplayLines(newContent);
        var lcs = new int[oldLines.size() + 1][newLines.size() + 1];

        for (var oldIndex = oldLines.size() - 1; oldIndex >= 0; oldIndex--) {
            for (var newIndex = newLines.size() - 1; newIndex >= 0; newIndex--) {
                if (oldLines.get(oldIndex).equals(newLines.get(newIndex))) {
                    lcs[oldIndex][newIndex] = lcs[oldIndex + 1][newIndex + 1] + 1;
                } else {
                    lcs[oldIndex][newIndex] = Math.max(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1]);
                }
            }
        }

        var parts = new ArrayList<DiffPart>();
        var oldIndex = 0;
        var newIndex = 0;

        while (oldIndex < oldLines.size() && newIndex < newLines.size()) {
            if (oldLines.get(oldIndex).equals(newLines.get(newIndex))) {
                appendPart(parts, DiffKind.CONTEXT, oldLines.get(oldIndex));
                oldIndex++;
                newIndex++;
            } else if (lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1]) {
                appendPart(parts, DiffKind.REMOVED, oldLines.get(oldIndex++));
            } else {
                appendPart(parts, DiffKind.ADDED, newLines.get(newIndex++));
            }
        }

        while (oldIndex < oldLines.size()) {
            appendPart(parts, DiffKind.REMOVED, oldLines.get(oldIndex++));
        }
        while (newIndex < newLines.size()) {
            appendPart(parts, DiffKind.ADDED, newLines.get(newIndex++));
        }

        return parts;
    }

    private static void appendPart(List<DiffPart> parts, DiffKind kind, String line) {
        if (!parts.isEmpty() && parts.getLast().kind() == kind) {
            parts.getLast().lines().add(line);
            return;
        }
        var lines = new ArrayList<String>();
        lines.add(line);
        parts.add(new DiffPart(kind, lines));
    }

    private enum DiffKind {
        CONTEXT,
        ADDED,
        REMOVED
    }

    private record DiffPart(
        DiffKind kind,
        ArrayList<String> lines
    ) {
    }
}
