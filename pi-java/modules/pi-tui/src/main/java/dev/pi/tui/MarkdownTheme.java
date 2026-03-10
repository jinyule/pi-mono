package dev.pi.tui;

import java.util.List;

public interface MarkdownTheme {
    String heading(String text);

    String link(String text);

    String linkUrl(String text);

    String code(String text);

    String codeBlock(String text);

    String codeBlockBorder(String text);

    String quote(String text);

    String quoteBorder(String text);

    String hr(String text);

    String listBullet(String text);

    String bold(String text);

    String italic(String text);

    String strikethrough(String text);

    String underline(String text);

    default List<String> highlightCode(String code, String language) {
        return List.of();
    }

    default String codeBlockIndent() {
        return "  ";
    }
}
