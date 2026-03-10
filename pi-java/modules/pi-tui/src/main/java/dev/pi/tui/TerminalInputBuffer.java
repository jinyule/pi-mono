package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TerminalInputBuffer {
    private static final String ESC = "\u001b";
    private static final String BRACKETED_PASTE_START = "\u001b[200~";
    private static final String BRACKETED_PASTE_END = "\u001b[201~";

    private String buffer = "";
    private boolean pasteMode;
    private String pasteBuffer = "";

    public List<String> feed(String data) {
        Objects.requireNonNull(data, "data");

        if (data.isEmpty()) {
            return List.of();
        }

        var emitted = new ArrayList<String>();
        buffer += data;
        processBuffer(emitted);
        return List.copyOf(emitted);
    }

    public List<String> flush() {
        if (buffer.isEmpty()) {
            return List.of();
        }
        var flushed = List.of(buffer);
        buffer = "";
        return flushed;
    }

    public void clear() {
        buffer = "";
        pasteMode = false;
        pasteBuffer = "";
    }

    String buffered() {
        return buffer;
    }

    private void processBuffer(List<String> emitted) {
        if (pasteMode) {
            processPasteMode(emitted);
            return;
        }

        var startIndex = buffer.indexOf(BRACKETED_PASTE_START);
        if (startIndex >= 0) {
            if (startIndex > 0) {
                var beforePaste = buffer.substring(0, startIndex);
                var extracted = extractCompleteSequences(beforePaste);
                emitted.addAll(extracted.sequences());
            }

            buffer = buffer.substring(startIndex + BRACKETED_PASTE_START.length());
            pasteMode = true;
            pasteBuffer = "";
            processPasteMode(emitted);
            return;
        }

        var extracted = extractCompleteSequences(buffer);
        emitted.addAll(extracted.sequences());
        buffer = extracted.remainder();
    }

    private void processPasteMode(List<String> emitted) {
        pasteBuffer += buffer;
        buffer = "";

        var endIndex = pasteBuffer.indexOf(BRACKETED_PASTE_END);
        if (endIndex < 0) {
            return;
        }

        var pastedContent = pasteBuffer.substring(0, endIndex);
        var remaining = pasteBuffer.substring(endIndex + BRACKETED_PASTE_END.length());

        pasteMode = false;
        pasteBuffer = "";
        emitted.add(BRACKETED_PASTE_START + pastedContent + BRACKETED_PASTE_END);

        if (!remaining.isEmpty()) {
            buffer = remaining;
            processBuffer(emitted);
        }
    }

    private static ExtractedSequences extractCompleteSequences(String content) {
        var sequences = new ArrayList<String>();
        var position = 0;

        while (position < content.length()) {
            var remaining = content.substring(position);
            if (remaining.startsWith(ESC)) {
                var sequenceEnd = 1;
                while (sequenceEnd <= remaining.length()) {
                    var candidate = remaining.substring(0, sequenceEnd);
                    var status = sequenceStatus(candidate);
                    if (status == SequenceStatus.COMPLETE) {
                        sequences.add(candidate);
                        position += sequenceEnd;
                        break;
                    }
                    if (status == SequenceStatus.INCOMPLETE) {
                        sequenceEnd += 1;
                        continue;
                    }

                    sequences.add(candidate);
                    position += sequenceEnd;
                    break;
                }

                if (sequenceEnd > remaining.length()) {
                    return new ExtractedSequences(sequences, remaining);
                }
            } else {
                var codePoint = remaining.codePointAt(0);
                var length = Character.charCount(codePoint);
                sequences.add(remaining.substring(0, length));
                position += length;
            }
        }

        return new ExtractedSequences(sequences, "");
    }

    private static SequenceStatus sequenceStatus(String data) {
        if (!data.startsWith(ESC)) {
            return SequenceStatus.NOT_ESCAPE;
        }
        if (data.length() == 1) {
            return SequenceStatus.INCOMPLETE;
        }

        var afterEsc = data.substring(1);
        if (afterEsc.startsWith("[")) {
            if (afterEsc.startsWith("[M")) {
                return data.length() >= 6 ? SequenceStatus.COMPLETE : SequenceStatus.INCOMPLETE;
            }
            return csiStatus(data);
        }
        if (afterEsc.startsWith("]")) {
            return oscStatus(data);
        }
        if (afterEsc.startsWith("P")) {
            return stTerminatedStatus(data, ESC + "P");
        }
        if (afterEsc.startsWith("_")) {
            return stTerminatedStatus(data, ESC + "_");
        }
        if (afterEsc.startsWith("O")) {
            return afterEsc.length() >= 2 ? SequenceStatus.COMPLETE : SequenceStatus.INCOMPLETE;
        }
        if (afterEsc.length() == 1) {
            return SequenceStatus.COMPLETE;
        }
        return SequenceStatus.COMPLETE;
    }

    private static SequenceStatus csiStatus(String data) {
        if (!data.startsWith(ESC + "[")) {
            return SequenceStatus.COMPLETE;
        }
        if (data.length() < 3) {
            return SequenceStatus.INCOMPLETE;
        }

        var payload = data.substring(2);
        var lastChar = payload.charAt(payload.length() - 1);
        if (lastChar >= 0x40 && lastChar <= 0x7e) {
            if (payload.startsWith("<")) {
                if (payload.matches("^<\\d+;\\d+;\\d+[Mm]$")) {
                    return SequenceStatus.COMPLETE;
                }
                if (lastChar == 'M' || lastChar == 'm') {
                    var body = payload.substring(1, payload.length() - 1);
                    var parts = body.split(";", -1);
                    if (parts.length == 3) {
                        var allDigits = true;
                        for (var part : parts) {
                            if (!part.matches("^\\d+$")) {
                                allDigits = false;
                                break;
                            }
                        }
                        if (allDigits) {
                            return SequenceStatus.COMPLETE;
                        }
                    }
                }
                return SequenceStatus.INCOMPLETE;
            }
            return SequenceStatus.COMPLETE;
        }

        return SequenceStatus.INCOMPLETE;
    }

    private static SequenceStatus oscStatus(String data) {
        if (!data.startsWith(ESC + "]")) {
            return SequenceStatus.COMPLETE;
        }
        return data.endsWith(ESC + "\\") || data.endsWith("\u0007")
            ? SequenceStatus.COMPLETE
            : SequenceStatus.INCOMPLETE;
    }

    private static SequenceStatus stTerminatedStatus(String data, String prefix) {
        if (!data.startsWith(prefix)) {
            return SequenceStatus.COMPLETE;
        }
        return data.endsWith(ESC + "\\") ? SequenceStatus.COMPLETE : SequenceStatus.INCOMPLETE;
    }

    private enum SequenceStatus {
        COMPLETE,
        INCOMPLETE,
        NOT_ESCAPE
    }

    private record ExtractedSequences(List<String> sequences, String remainder) {
    }
}
