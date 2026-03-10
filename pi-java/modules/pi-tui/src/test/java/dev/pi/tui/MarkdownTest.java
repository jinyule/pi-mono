package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownTest {
    private static final MarkdownTheme THEME = new MarkdownTheme() {
        @Override
        public String heading(String text) {
            return style("36", text);
        }

        @Override
        public String link(String text) {
            return style("34", text);
        }

        @Override
        public String linkUrl(String text) {
            return style("35", text);
        }

        @Override
        public String code(String text) {
            return style("33", text);
        }

        @Override
        public String codeBlock(String text) {
            return style("32", text);
        }

        @Override
        public String codeBlockBorder(String text) {
            return style("90", text);
        }

        @Override
        public String quote(String text) {
            return style("90", text);
        }

        @Override
        public String quoteBorder(String text) {
            return style("90", text);
        }

        @Override
        public String hr(String text) {
            return style("90", text);
        }

        @Override
        public String listBullet(String text) {
            return style("36", text);
        }

        @Override
        public String bold(String text) {
            return style("1", text);
        }

        @Override
        public String italic(String text) {
            return style("3", text);
        }

        @Override
        public String strikethrough(String text) {
            return style("9", text);
        }

        @Override
        public String underline(String text) {
            return style("4", text);
        }
    };

    @Test
    void rendersNestedLists() {
        var markdown = new Markdown("""
            - Item 1
                - Nested 1.1
                - Nested 1.2
            - Item 2
            """, 0, 0, THEME);

        var lines = plain(markdown.render(80));

        assertThat(lines).anyMatch(line -> line.contains("- Item 1"));
        assertThat(lines).anyMatch(line -> line.contains("  - Nested 1.1"));
        assertThat(lines).anyMatch(line -> line.contains("  - Nested 1.2"));
        assertThat(lines).anyMatch(line -> line.contains("- Item 2"));
    }

    @Test
    void rendersTablesWithinWidth() {
        var markdown = new Markdown("""
            | Command | Description |
            | --- | --- |
            | npm install | Install all dependencies |
            | npm run build | Build the project |
            """, 0, 0, THEME);

        var lines = plain(markdown.render(40));

        assertThat(lines).allMatch(line -> line.length() <= 40);
        assertThat(lines).anyMatch(line -> line.contains("│"));
        assertThat(lines).anyMatch(line -> line.contains("Command"));
        assertThat(lines).anyMatch(line -> line.contains("Install"));
    }

    @Test
    void rendersExplicitLinksWithSeparateUrlStyle() {
        var markdown = new Markdown("[click here](https://example.com)", 0, 0, THEME);

        var rendered = markdown.render(80).get(0);
        var plain = stripAnsi(rendered);

        assertThat(plain).contains("click here");
        assertThat(plain).contains("(https://example.com)");
        assertThat(rendered).contains("\u001b[34m");
        assertThat(rendered).contains("\u001b[35m");
    }

    @Test
    void doesNotDuplicateBareUrlsOrEmails() {
        var bareUrl = new Markdown("Visit https://example.com for more", 0, 0, THEME);
        var bareEmail = new Markdown("Contact user@example.com for help", 0, 0, THEME);

        var bareUrlText = String.join(" ", plain(bareUrl.render(80)));
        var bareEmailText = String.join(" ", plain(bareEmail.render(80)));

        assertThat(countOccurrences(bareUrlText, "https://example.com")).isEqualTo(1);
        assertThat(countOccurrences(bareEmailText, "user@example.com")).isEqualTo(1);
        assertThat(bareEmailText).doesNotContain("mailto:");
    }

    @Test
    void wrapsBlockquotesAndPreservesQuoteBorder() {
        var markdown = new Markdown("> This is a very long blockquote line that should wrap to multiple lines", 0, 0, THEME);

        var lines = plain(markdown.render(24)).stream()
            .filter(line -> !line.isEmpty())
            .toList();

        assertThat(lines.size()).isGreaterThan(1);
        assertThat(lines).allMatch(line -> line.startsWith("│ "));
    }

    @Test
    void preservesDefaultStyleAroundInlineFormatting() {
        var markdown = new Markdown(
            "Thinking with `inline code` and **bold** text",
            1,
            0,
            THEME,
            new DefaultTextStyle(text -> style("90", text), null, false, true, false, false)
        );

        var rendered = String.join("\n", markdown.render(80));

        assertThat(rendered).contains("\u001b[90m");
        assertThat(rendered).contains("\u001b[3m");
        assertThat(rendered).contains("\u001b[33m");
        assertThat(rendered).contains("\u001b[1m");
    }

    @Test
    void respectsHorizontalPaddingForRenderedContent() {
        var markdown = new Markdown("""
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """, 2, 0, THEME);

        var lines = plain(markdown.render(24));
        var tableLine = lines.stream().filter(line -> line.contains("│")).findFirst().orElseThrow();

        assertThat(tableLine).startsWith("  ");
        assertThat(lines).allMatch(line -> line.length() <= 24);
    }

    private static List<String> plain(List<String> lines) {
        return lines.stream().map(MarkdownTest::stripAnsi).toList();
    }

    private static String stripAnsi(String text) {
        return text.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private static int countOccurrences(String text, String token) {
        var count = 0;
        var index = text.indexOf(token);
        while (index >= 0) {
            count += 1;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String style(String code, String text) {
        return "\u001b[" + code + "m" + text + "\u001b[0m";
    }
}
