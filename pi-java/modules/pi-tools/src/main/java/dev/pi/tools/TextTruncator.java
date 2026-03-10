package dev.pi.tools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public final class TextTruncator {
    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_BYTES = 50 * 1024;
    public static final int GREP_MAX_LINE_LENGTH = 500;

    private TextTruncator() {
    }

    public static String formatSize(int bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return "%.1fKB".formatted(bytes / 1024.0);
        }
        return "%.1fMB".formatted(bytes / (1024.0 * 1024.0));
    }

    public static TruncationResult truncateHead(String content) {
        return truncateHead(content, TruncationOptions.defaults());
    }

    public static TruncationResult truncateHead(String content, TruncationOptions options) {
        Objects.requireNonNull(content, "content");
        var appliedOptions = options == null ? TruncationOptions.defaults() : options;
        var totalBytes = utf8Length(content);
        var lines = splitLines(content);
        var totalLines = lines.length;

        if (totalLines <= appliedOptions.maxLines() && totalBytes <= appliedOptions.maxBytes()) {
            return new TruncationResult(
                content,
                false,
                null,
                totalLines,
                totalBytes,
                totalLines,
                totalBytes,
                false,
                false,
                appliedOptions.maxLines(),
                appliedOptions.maxBytes()
            );
        }

        if (utf8Length(lines[0]) > appliedOptions.maxBytes()) {
            return new TruncationResult(
                "",
                true,
                TruncationLimit.BYTES,
                totalLines,
                totalBytes,
                0,
                0,
                false,
                true,
                appliedOptions.maxLines(),
                appliedOptions.maxBytes()
            );
        }

        var outputLines = new ArrayList<String>();
        var outputBytes = 0;
        var truncatedBy = TruncationLimit.LINES;

        for (var index = 0; index < lines.length && index < appliedOptions.maxLines(); index++) {
            var line = lines[index];
            var lineBytes = utf8Length(line) + (index > 0 ? 1 : 0);
            if (outputBytes + lineBytes > appliedOptions.maxBytes()) {
                truncatedBy = TruncationLimit.BYTES;
                break;
            }
            outputLines.add(line);
            outputBytes += lineBytes;
        }

        if (outputLines.size() >= appliedOptions.maxLines() && outputBytes <= appliedOptions.maxBytes()) {
            truncatedBy = TruncationLimit.LINES;
        }

        var outputContent = String.join("\n", outputLines);
        return new TruncationResult(
            outputContent,
            true,
            truncatedBy,
            totalLines,
            totalBytes,
            outputLines.size(),
            utf8Length(outputContent),
            false,
            false,
            appliedOptions.maxLines(),
            appliedOptions.maxBytes()
        );
    }

    public static TruncationResult truncateTail(String content) {
        return truncateTail(content, TruncationOptions.defaults());
    }

    public static TruncationResult truncateTail(String content, TruncationOptions options) {
        Objects.requireNonNull(content, "content");
        var appliedOptions = options == null ? TruncationOptions.defaults() : options;
        var totalBytes = utf8Length(content);
        var lines = splitLines(content);
        var totalLines = lines.length;

        if (totalLines <= appliedOptions.maxLines() && totalBytes <= appliedOptions.maxBytes()) {
            return new TruncationResult(
                content,
                false,
                null,
                totalLines,
                totalBytes,
                totalLines,
                totalBytes,
                false,
                false,
                appliedOptions.maxLines(),
                appliedOptions.maxBytes()
            );
        }

        var outputLines = new ArrayList<String>();
        var outputBytes = 0;
        var truncatedBy = TruncationLimit.LINES;
        var lastLinePartial = false;

        for (var index = lines.length - 1; index >= 0 && outputLines.size() < appliedOptions.maxLines(); index--) {
            var line = lines[index];
            var lineBytes = utf8Length(line) + (!outputLines.isEmpty() ? 1 : 0);
            if (outputBytes + lineBytes > appliedOptions.maxBytes()) {
                truncatedBy = TruncationLimit.BYTES;
                if (outputLines.isEmpty()) {
                    outputLines.add(0, truncateStringToBytesFromEnd(line, appliedOptions.maxBytes()));
                    outputBytes = utf8Length(outputLines.getFirst());
                    lastLinePartial = true;
                }
                break;
            }
            outputLines.add(0, line);
            outputBytes += lineBytes;
        }

        if (outputLines.size() >= appliedOptions.maxLines() && outputBytes <= appliedOptions.maxBytes()) {
            truncatedBy = TruncationLimit.LINES;
        }

        var outputContent = String.join("\n", outputLines);
        return new TruncationResult(
            outputContent,
            true,
            truncatedBy,
            totalLines,
            totalBytes,
            outputLines.size(),
            utf8Length(outputContent),
            lastLinePartial,
            false,
            appliedOptions.maxLines(),
            appliedOptions.maxBytes()
        );
    }

    public static LineTruncationResult truncateLine(String line) {
        return truncateLine(line, GREP_MAX_LINE_LENGTH);
    }

    public static LineTruncationResult truncateLine(String line, int maxChars) {
        Objects.requireNonNull(line, "line");
        if (line.length() <= maxChars) {
            return new LineTruncationResult(line, false);
        }
        return new LineTruncationResult(line.substring(0, maxChars) + "... [truncated]", true);
    }

    private static String[] splitLines(String content) {
        return content.split("\n", -1);
    }

    private static int utf8Length(String content) {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String truncateStringToBytesFromEnd(String value, int maxBytes) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        var start = bytes.length - maxBytes;
        while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) {
            start++;
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }
}
