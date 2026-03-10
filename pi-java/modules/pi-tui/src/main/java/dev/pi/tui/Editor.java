package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class Editor implements Component, Focusable {
    private static final String BRACKETED_PASTE_START = "\u001b[200~";
    private static final String BRACKETED_PASTE_END = "\u001b[201~";

    private final List<String> lines = new ArrayList<>(List.of(""));
    private final KillRing killRing = new KillRing();
    private final UndoStack<EditorState> undoStack = new UndoStack<>();

    private EditorKeybindings keybindings = EditorKeybindings.global();
    private int cursorLine;
    private int cursorColumn;
    private boolean focused;
    private boolean disableSubmit;
    private int paddingX;
    private String pasteBuffer = "";
    private boolean inPaste;
    private String lastAction;
    private UnaryOperator<String> borderColor = UnaryOperator.identity();

    private TextHandler onSubmit;
    private TextHandler onChange;

    public String getText() {
        return String.join("\n", lines);
    }

    public void setText(String text) {
        lines.clear();
        var normalized = text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n");
        lines.addAll(List.of(normalized.split("\n", -1)));
        if (lines.isEmpty()) {
            lines.add("");
        }
        cursorLine = lines.size() - 1;
        cursorColumn = currentLine().length();
    }

    public void setOnSubmit(TextHandler onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setOnChange(TextHandler onChange) {
        this.onChange = onChange;
    }

    public void setDisableSubmit(boolean disableSubmit) {
        this.disableSubmit = disableSubmit;
    }

    public void setPaddingX(int paddingX) {
        this.paddingX = Math.max(0, paddingX);
    }

    public int getPaddingX() {
        return paddingX;
    }

    public void setBorderColor(UnaryOperator<String> borderColor) {
        this.borderColor = borderColor == null ? UnaryOperator.identity() : borderColor;
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
                insertText(pasteBuffer.substring(0, endIndex).replace("\r\n", "\n").replace("\r", "\n"));
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
            return;
        }
        if (keybindings.matches(data, EditorAction.UNDO)) {
            undo();
            return;
        }
        if (keybindings.matches(data, EditorAction.NEW_LINE)) {
            insertNewLine();
            return;
        }
        if (keybindings.matches(data, EditorAction.SUBMIT)) {
            if (!disableSubmit && onSubmit != null) {
                onSubmit.handle(getText());
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
            moveLeft();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_RIGHT)) {
            moveRight();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_UP)) {
            moveUp();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_DOWN)) {
            moveDown();
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_LINE_START)) {
            lastAction = null;
            cursorColumn = 0;
            return;
        }
        if (keybindings.matches(data, EditorAction.CURSOR_LINE_END)) {
            lastAction = null;
            cursorColumn = currentLine().length();
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

        if (InputSupport.containsControlCharacters(data)) {
            return;
        }
        insertText(data);
    }

    @Override
    public List<String> render(int width) {
        var result = new ArrayList<String>();
        result.add(borderColor.apply("─".repeat(Math.max(1, width))));

        var contentWidth = Math.max(1, width - paddingX * 2);
        for (var index = 0; index < lines.size(); index += 1) {
            var line = renderLine(index, contentWidth);
            var padded = " ".repeat(paddingX) + line + " ".repeat(paddingX);
            result.add(TerminalText.padRightVisible(padded, width));
        }

        result.add(borderColor.apply("─".repeat(Math.max(1, width))));
        return List.copyOf(result);
    }

    private String renderLine(int lineIndex, int contentWidth) {
        var line = lines.get(lineIndex);
        if (lineIndex != cursorLine) {
            return TerminalText.takeVisibleColumns(line, contentWidth);
        }

        var beforeCursor = line.substring(0, Math.min(cursorColumn, line.length()));
        var atCursor = cursorColumn < line.length()
            ? line.substring(cursorColumn, nextCodePointEnd(line, cursorColumn))
            : " ";
        var afterCursor = cursorColumn < line.length()
            ? line.substring(Math.min(line.length(), cursorColumn + atCursor.length()))
            : "";
        var marker = focused ? Tui.CURSOR_MARKER : "";
        var rendered = beforeCursor + marker + "\u001b[7m" + atCursor + "\u001b[27m" + afterCursor;
        return TerminalText.takeVisibleColumns(rendered, contentWidth);
    }

    private void insertText(String text) {
        pushUndo();
        var current = currentLine();
        var before = current.substring(0, cursorColumn);
        var after = current.substring(cursorColumn);
        var normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        if (!normalized.contains("\n")) {
            lines.set(cursorLine, before + normalized + after);
            cursorColumn += normalized.length();
        } else {
            var split = normalized.split("\n", -1);
            lines.set(cursorLine, before + split[0]);
            var insertAt = cursorLine + 1;
            for (var index = 1; index < split.length - 1; index += 1) {
                lines.add(insertAt++, split[index]);
            }
            lines.add(insertAt, split[split.length - 1] + after);
            cursorLine = insertAt;
            cursorColumn = split[split.length - 1].length();
        }
        lastAction = "type";
        notifyChange();
    }

    private void insertNewLine() {
        pushUndo();
        var current = currentLine();
        var before = current.substring(0, cursorColumn);
        var after = current.substring(cursorColumn);
        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, after);
        cursorLine += 1;
        cursorColumn = 0;
        lastAction = null;
        notifyChange();
    }

    private void deleteBackward() {
        pushUndo();
        var current = currentLine();
        if (cursorColumn > 0) {
            var start = previousCodePointStart(current, cursorColumn);
            lines.set(cursorLine, current.substring(0, start) + current.substring(cursorColumn));
            cursorColumn = start;
        } else if (cursorLine > 0) {
            var previous = lines.get(cursorLine - 1);
            cursorColumn = previous.length();
            lines.set(cursorLine - 1, previous + current);
            lines.remove(cursorLine);
            cursorLine -= 1;
        }
        lastAction = null;
        notifyChange();
    }

    private void deleteForward() {
        pushUndo();
        var current = currentLine();
        if (cursorColumn < current.length()) {
            var end = nextCodePointEnd(current, cursorColumn);
            lines.set(cursorLine, current.substring(0, cursorColumn) + current.substring(end));
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, current + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        }
        lastAction = null;
        notifyChange();
    }

    private void deleteToLineStart() {
        pushUndo();
        if (cursorColumn == 0) {
            return;
        }
        var current = currentLine();
        var deleted = current.substring(0, cursorColumn);
        killRing.push(deleted, true, "kill".equals(lastAction));
        lines.set(cursorLine, current.substring(cursorColumn));
        cursorColumn = 0;
        lastAction = "kill";
        notifyChange();
    }

    private void deleteToLineEnd() {
        pushUndo();
        var current = currentLine();
        if (cursorColumn >= current.length()) {
            return;
        }
        var deleted = current.substring(cursorColumn);
        killRing.push(deleted, false, "kill".equals(lastAction));
        lines.set(cursorLine, current.substring(0, cursorColumn));
        lastAction = "kill";
        notifyChange();
    }

    private void deleteWordBackward() {
        pushUndo();
        var oldLine = cursorLine;
        var oldColumn = cursorColumn;
        moveWordBackward();
        var deleted = deleteRange(cursorLine, cursorColumn, oldLine, oldColumn);
        killRing.push(deleted, true, "kill".equals(lastAction));
        lastAction = "kill";
        notifyChange();
    }

    private void deleteWordForward() {
        pushUndo();
        var startLine = cursorLine;
        var startColumn = cursorColumn;
        moveWordForward();
        var deleted = deleteRange(startLine, startColumn, cursorLine, cursorColumn);
        cursorLine = startLine;
        cursorColumn = startColumn;
        killRing.push(deleted, false, "kill".equals(lastAction));
        lastAction = "kill";
        notifyChange();
    }

    private String deleteRange(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine == endLine) {
            var line = lines.get(startLine);
            var deleted = line.substring(startColumn, endColumn);
            lines.set(startLine, line.substring(0, startColumn) + line.substring(endColumn));
            return deleted;
        }

        var builder = new StringBuilder();
        builder.append(lines.get(startLine), startColumn, lines.get(startLine).length()).append('\n');
        for (var line = startLine + 1; line < endLine; line += 1) {
            builder.append(lines.get(line)).append('\n');
        }
        builder.append(lines.get(endLine), 0, endColumn);

        var merged = lines.get(startLine).substring(0, startColumn) + lines.get(endLine).substring(endColumn);
        for (var line = endLine; line > startLine; line -= 1) {
            lines.remove(line);
        }
        lines.set(startLine, merged);
        return builder.toString();
    }

    private void yank() {
        var text = killRing.peek();
        if (text == null || text.isEmpty()) {
            return;
        }
        insertText(text);
        lastAction = "yank";
    }

    private void yankPop() {
        if (!"yank".equals(lastAction) || killRing.size() <= 1) {
            return;
        }
        pushUndo();
        var previous = killRing.peek();
        deleteRange(cursorLine, cursorColumn - previous.length(), cursorLine, cursorColumn);
        cursorColumn -= previous.length();
        killRing.rotate();
        var next = killRing.peek();
        insertText(next);
        lastAction = "yank";
    }

    private void undo() {
        var snapshot = undoStack.pop();
        if (snapshot == null) {
            return;
        }
        lines.clear();
        lines.addAll(snapshot.lines());
        cursorLine = snapshot.cursorLine();
        cursorColumn = snapshot.cursorColumn();
        lastAction = null;
        notifyChange();
    }

    private void moveLeft() {
        lastAction = null;
        if (cursorColumn > 0) {
            cursorColumn = previousCodePointStart(currentLine(), cursorColumn);
        } else if (cursorLine > 0) {
            cursorLine -= 1;
            cursorColumn = currentLine().length();
        }
    }

    private void moveRight() {
        lastAction = null;
        var current = currentLine();
        if (cursorColumn < current.length()) {
            cursorColumn = nextCodePointEnd(current, cursorColumn);
        } else if (cursorLine < lines.size() - 1) {
            cursorLine += 1;
            cursorColumn = 0;
        }
    }

    private void moveUp() {
        lastAction = null;
        if (cursorLine > 0) {
            cursorLine -= 1;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
        }
    }

    private void moveDown() {
        lastAction = null;
        if (cursorLine < lines.size() - 1) {
            cursorLine += 1;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
        }
    }

    private void moveWordBackward() {
        lastAction = null;
        if (cursorColumn == 0 && cursorLine > 0) {
            cursorLine -= 1;
            cursorColumn = currentLine().length();
        }
        while (cursorColumn > 0) {
            var start = previousCodePointStart(currentLine(), cursorColumn);
            var token = currentLine().substring(start, cursorColumn);
            if (!InputSupport.isWhitespace(token)) {
                break;
            }
            cursorColumn = start;
        }
        while (cursorColumn > 0) {
            var start = previousCodePointStart(currentLine(), cursorColumn);
            var token = currentLine().substring(start, cursorColumn);
            if (InputSupport.isPunctuation(token)) {
                cursorColumn = start;
                continue;
            }
            break;
        }
        while (cursorColumn > 0) {
            var start = previousCodePointStart(currentLine(), cursorColumn);
            var token = currentLine().substring(start, cursorColumn);
            if (InputSupport.isWhitespace(token) || InputSupport.isPunctuation(token)) {
                break;
            }
            cursorColumn = start;
        }
    }

    private void moveWordForward() {
        lastAction = null;
        if (cursorColumn >= currentLine().length() && cursorLine < lines.size() - 1) {
            cursorLine += 1;
            cursorColumn = 0;
        }
        while (cursorColumn < currentLine().length()) {
            var end = nextCodePointEnd(currentLine(), cursorColumn);
            var token = currentLine().substring(cursorColumn, end);
            if (!InputSupport.isWhitespace(token)) {
                break;
            }
            cursorColumn = end;
        }
        while (cursorColumn < currentLine().length()) {
            var end = nextCodePointEnd(currentLine(), cursorColumn);
            var token = currentLine().substring(cursorColumn, end);
            if (InputSupport.isPunctuation(token)) {
                cursorColumn = end;
                continue;
            }
            break;
        }
        while (cursorColumn < currentLine().length()) {
            var end = nextCodePointEnd(currentLine(), cursorColumn);
            var token = currentLine().substring(cursorColumn, end);
            if (InputSupport.isWhitespace(token) || InputSupport.isPunctuation(token)) {
                break;
            }
            cursorColumn = end;
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.handle(getText());
        }
    }

    private void pushUndo() {
        undoStack.push(new EditorState(List.copyOf(lines), cursorLine, cursorColumn));
    }

    private String currentLine() {
        return lines.get(cursorLine);
    }

    private static int previousCodePointStart(String text, int cursor) {
        return text.offsetByCodePoints(cursor, -1);
    }

    private static int nextCodePointEnd(String text, int cursor) {
        return text.offsetByCodePoints(cursor, 1);
    }

    public interface TextHandler {
        void handle(String text);
    }

    private record EditorState(
        List<String> lines,
        int cursorLine,
        int cursorColumn
    ) {
    }
}
