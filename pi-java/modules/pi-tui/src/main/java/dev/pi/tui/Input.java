package dev.pi.tui;

import java.util.List;

public final class Input implements Component, Focusable {
    private static final String BRACKETED_PASTE_START = "\u001b[200~";
    private static final String BRACKETED_PASTE_END = "\u001b[201~";

    private String value = "";
    private int cursor;
    private boolean focused;
    private String pasteBuffer = "";
    private boolean inPaste;
    private final KillRing killRing = new KillRing();
    private final UndoStack<InputState> undoStack = new UndoStack<>();

    private EditorKeybindings keybindings = EditorKeybindings.global();
    private String lastAction;

    private ValueHandler onSubmit;
    private Runnable onEscape;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        cursor = Math.min(cursor, this.value.length());
    }

    public void setOnSubmit(ValueHandler onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setOnEscape(Runnable onEscape) {
        this.onEscape = onEscape;
    }

    public void setKeybindings(EditorKeybindings keybindings) {
        this.keybindings = keybindings == null ? EditorKeybindings.global() : keybindings;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public void handleInput(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        if (data.contains(BRACKETED_PASTE_START)) {
            inPaste = true;
            pasteBuffer = "";
            data = data.replace(BRACKETED_PASTE_START, "");
        }

        if (inPaste) {
            pasteBuffer += data;
            var endIndex = pasteBuffer.indexOf(BRACKETED_PASTE_END);
            if (endIndex >= 0) {
                var pasted = pasteBuffer.substring(0, endIndex);
                handlePaste(pasted);
                inPaste = false;
                var remaining = pasteBuffer.substring(endIndex + BRACKETED_PASTE_END.length());
                pasteBuffer = "";
                if (!remaining.isEmpty()) {
                    handleInput(remaining);
                }
            }
            return;
        }

        if (keybindings.matches(data, EditorAction.SELECT_CANCEL)) {
            if (onEscape != null) {
                onEscape.run();
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.UNDO)) {
            undo();
            return;
        }
        if (keybindings.matches(data, EditorAction.SUBMIT)) {
            if (onSubmit != null) {
                onSubmit.handle(value);
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_CHAR_BACKWARD)) {
            deleteBackward();
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_CHAR_FORWARD)) {
            deleteForward();
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_WORD_BACKWARD)) {
            deleteWordBackward();
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_WORD_FORWARD)) {
            deleteWordForward();
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_TO_LINE_START)) {
            deleteToLineStart();
            return;
        }
        if (keybindings.matches(data, EditorAction.DELETE_TO_LINE_END)) {
            deleteToLineEnd();
            return;
        }
        if (keybindings.matches(data, EditorAction.YANK)) {
            yank();
            return;
        }
        if (keybindings.matches(data, EditorAction.YANK_POP)) {
            yankPop();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_LEFT)) {
            lastAction = null;
            if (cursor > 0) {
                cursor = previousCodePointStart(value, cursor);
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_RIGHT)) {
            lastAction = null;
            if (cursor < value.length()) {
                cursor = nextCodePointEnd(value, cursor);
            }
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_LINE_START)) {
            lastAction = null;
            cursor = 0;
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_LINE_END)) {
            lastAction = null;
            cursor = value.length();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_WORD_LEFT)) {
            moveWordBackward();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_WORD_RIGHT)) {
            moveWordForward();
            return;
        }

        if (containsControlCharacters(data)) {
            return;
        }
        insertText(data);
    }

    @Override
    public List<String> render(int width) {
        var prompt = "> ";
        var availableWidth = Math.max(1, width - prompt.length());

        String visibleText;
        var cursorDisplay = cursor;
        if (value.length() < availableWidth) {
            visibleText = value;
        } else {
            var scrollWidth = cursor == value.length() ? Math.max(1, availableWidth - 1) : availableWidth;
            var halfWidth = Math.max(1, scrollWidth / 2);

            if (cursor < halfWidth) {
                visibleText = value.substring(0, Math.min(value.length(), scrollWidth));
                cursorDisplay = cursor;
            } else if (cursor > value.length() - halfWidth) {
                var start = Math.max(0, value.length() - scrollWidth);
                visibleText = value.substring(start);
                cursorDisplay = cursor - start;
            } else {
                var start = Math.max(0, cursor - halfWidth);
                var end = Math.min(value.length(), start + scrollWidth);
                visibleText = value.substring(start, end);
                cursorDisplay = cursor - start;
            }
        }

        var beforeCursor = visibleText.substring(0, Math.min(cursorDisplay, visibleText.length()));
        var atCursor = cursorDisplay < visibleText.length()
            ? visibleText.substring(cursorDisplay, nextCodePointEnd(visibleText, cursorDisplay))
            : " ";
        var afterCursor = cursorDisplay < visibleText.length()
            ? visibleText.substring(Math.min(visibleText.length(), cursorDisplay + atCursor.length()))
            : "";
        var marker = focused ? Tui.CURSOR_MARKER : "";
        var cursorChar = "\u001b[7m" + atCursor + "\u001b[27m";
        var textWithCursor = beforeCursor + marker + cursorChar + afterCursor;
        var padding = " ".repeat(Math.max(0, availableWidth - TerminalText.visibleWidth(textWithCursor)));
        return List.of(prompt + textWithCursor + padding);
    }

    private void insertText(String text) {
        if (isWhitespace(text) || !"type-word".equals(lastAction)) {
            pushUndo();
        }
        lastAction = "type-word";
        value = value.substring(0, cursor) + text + value.substring(cursor);
        cursor += text.length();
    }

    private void deleteBackward() {
        lastAction = null;
        if (cursor <= 0) {
            return;
        }
        pushUndo();
        var start = previousCodePointStart(value, cursor);
        value = value.substring(0, start) + value.substring(cursor);
        cursor = start;
    }

    private void deleteForward() {
        lastAction = null;
        if (cursor >= value.length()) {
            return;
        }
        pushUndo();
        var end = nextCodePointEnd(value, cursor);
        value = value.substring(0, cursor) + value.substring(end);
    }

    private void deleteToLineStart() {
        if (cursor == 0) {
            return;
        }
        pushUndo();
        var deleted = value.substring(0, cursor);
        killRing.push(deleted, true, "kill".equals(lastAction));
        lastAction = "kill";
        value = value.substring(cursor);
        cursor = 0;
    }

    private void deleteToLineEnd() {
        if (cursor >= value.length()) {
            return;
        }
        pushUndo();
        var deleted = value.substring(cursor);
        killRing.push(deleted, false, "kill".equals(lastAction));
        lastAction = "kill";
        value = value.substring(0, cursor);
    }

    private void deleteWordBackward() {
        if (cursor == 0) {
            return;
        }
        var wasKill = "kill".equals(lastAction);
        pushUndo();
        var oldCursor = cursor;
        moveWordBackward();
        var deleteFrom = cursor;
        cursor = oldCursor;
        var deleted = value.substring(deleteFrom, cursor);
        killRing.push(deleted, true, wasKill);
        lastAction = "kill";
        value = value.substring(0, deleteFrom) + value.substring(cursor);
        cursor = deleteFrom;
    }

    private void deleteWordForward() {
        if (cursor >= value.length()) {
            return;
        }
        var wasKill = "kill".equals(lastAction);
        pushUndo();
        var oldCursor = cursor;
        moveWordForward();
        var deleteTo = cursor;
        cursor = oldCursor;
        var deleted = value.substring(cursor, deleteTo);
        killRing.push(deleted, false, wasKill);
        lastAction = "kill";
        value = value.substring(0, cursor) + value.substring(deleteTo);
    }

    private void yank() {
        var text = killRing.peek();
        if (text == null || text.isEmpty()) {
            return;
        }
        pushUndo();
        value = value.substring(0, cursor) + text + value.substring(cursor);
        cursor += text.length();
        lastAction = "yank";
    }

    private void yankPop() {
        if (!"yank".equals(lastAction) || killRing.size() <= 1) {
            return;
        }
        pushUndo();
        var previousText = killRing.peek();
        value = value.substring(0, cursor - previousText.length()) + value.substring(cursor);
        cursor -= previousText.length();
        killRing.rotate();
        var next = killRing.peek();
        value = value.substring(0, cursor) + next + value.substring(cursor);
        cursor += next.length();
        lastAction = "yank";
    }

    private void undo() {
        var snapshot = undoStack.pop();
        if (snapshot == null) {
            return;
        }
        value = snapshot.value();
        cursor = snapshot.cursor();
        lastAction = null;
    }

    private void handlePaste(String pastedText) {
        lastAction = null;
        pushUndo();
        var clean = pastedText.replace("\r\n", "").replace("\r", "").replace("\n", "");
        value = value.substring(0, cursor) + clean + value.substring(cursor);
        cursor += clean.length();
    }

    private void pushUndo() {
        undoStack.push(new InputState(value, cursor));
    }

    private void moveWordBackward() {
        if (cursor == 0) {
            return;
        }
        lastAction = null;
        while (cursor > 0) {
            var start = previousCodePointStart(value, cursor);
            var token = value.substring(start, cursor);
            if (!isWhitespace(token)) {
                break;
            }
            cursor = start;
        }
        while (cursor > 0) {
            var start = previousCodePointStart(value, cursor);
            var token = value.substring(start, cursor);
            if (isPunctuation(token)) {
                cursor = start;
                continue;
            }
            break;
        }
        while (cursor > 0) {
            var start = previousCodePointStart(value, cursor);
            var token = value.substring(start, cursor);
            if (isWhitespace(token) || isPunctuation(token)) {
                break;
            }
            cursor = start;
        }
    }

    private void moveWordForward() {
        if (cursor >= value.length()) {
            return;
        }
        lastAction = null;
        while (cursor < value.length()) {
            var end = nextCodePointEnd(value, cursor);
            var token = value.substring(cursor, end);
            if (!isWhitespace(token)) {
                break;
            }
            cursor = end;
        }
        while (cursor < value.length()) {
            var end = nextCodePointEnd(value, cursor);
            var token = value.substring(cursor, end);
            if (isPunctuation(token)) {
                cursor = end;
                continue;
            }
            break;
        }
        while (cursor < value.length()) {
            var end = nextCodePointEnd(value, cursor);
            var token = value.substring(cursor, end);
            if (isWhitespace(token) || isPunctuation(token)) {
                break;
            }
            cursor = end;
        }
    }

    private static boolean containsControlCharacters(String text) {
        for (var index = 0; index < text.length(); ) {
            var codePoint = text.codePointAt(index);
            if (codePoint < 32 || codePoint == 0x7f || (codePoint >= 0x80 && codePoint <= 0x9f)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isWhitespace(String text) {
        var codePoint = text.codePointAt(0);
        return Character.isWhitespace(codePoint);
    }

    private static boolean isPunctuation(String text) {
        var type = Character.getType(text.codePointAt(0));
        return switch (type) {
            case Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static int previousCodePointStart(String text, int cursor) {
        return text.offsetByCodePoints(cursor, -1);
    }

    private static int nextCodePointEnd(String text, int cursor) {
        return text.offsetByCodePoints(cursor, 1);
    }

    public interface ValueHandler {
        void handle(String value);
    }

    private record InputState(
        String value,
        int cursor
    ) {
    }
}
