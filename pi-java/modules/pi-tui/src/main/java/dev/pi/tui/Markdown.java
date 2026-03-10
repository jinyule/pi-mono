package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

public final class Markdown implements Component {
    private static final List<Extension> EXTENSIONS = List.of(
        AutolinkExtension.create(),
        StrikethroughExtension.create(),
        TablesExtension.create()
    );

    private static final Parser PARSER = Parser.builder()
        .extensions(EXTENSIONS)
        .build();

    private String text;
    private final int paddingX;
    private final int paddingY;
    private final MarkdownTheme theme;
    private final DefaultTextStyle defaultTextStyle;

    private String cachedText;
    private Integer cachedWidth;
    private List<String> cachedLines;
    private String defaultStylePrefix;

    public Markdown(String text, int paddingX, int paddingY, MarkdownTheme theme) {
        this(text, paddingX, paddingY, theme, null);
    }

    public Markdown(String text, int paddingX, int paddingY, MarkdownTheme theme, DefaultTextStyle defaultTextStyle) {
        this.text = text == null ? "" : text;
        this.paddingX = Math.max(0, paddingX);
        this.paddingY = Math.max(0, paddingY);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.defaultTextStyle = defaultTextStyle;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        invalidate();
    }

    @Override
    public void invalidate() {
        cachedText = null;
        cachedWidth = null;
        cachedLines = null;
        defaultStylePrefix = null;
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && text.equals(cachedText) && Integer.valueOf(width).equals(cachedWidth)) {
            return cachedLines;
        }

        if (text.isBlank()) {
            cachedText = text;
            cachedWidth = width;
            cachedLines = List.of();
            return cachedLines;
        }

        var normalized = text.replace("\t", "   ");
        var contentWidth = Math.max(1, width - paddingX * 2);
        var document = PARSER.parse(normalized);
        var rendered = renderBlocks(document, contentWidth, defaultInlineStyleContext(), true);
        var wrapped = wrapLines(rendered, contentWidth);

        var leftPadding = " ".repeat(paddingX);
        var rightPadding = " ".repeat(paddingX);
        var background = defaultTextStyle == null ? null : defaultTextStyle.background();
        var contentLines = new ArrayList<String>();
        for (var line : wrapped) {
            var withMargins = leftPadding + line + rightPadding;
            contentLines.add(background == null
                ? TerminalText.padRightVisible(withMargins, width)
                : TerminalText.applyBackgroundToLine(withMargins, width, background));
        }

        var emptyLine = " ".repeat(Math.max(0, width));
        var result = new ArrayList<String>();
        for (var index = 0; index < paddingY; index += 1) {
            result.add(background == null ? emptyLine : TerminalText.applyBackgroundToLine(emptyLine, width, background));
        }
        result.addAll(contentLines);
        for (var index = 0; index < paddingY; index += 1) {
            result.add(background == null ? emptyLine : TerminalText.applyBackgroundToLine(emptyLine, width, background));
        }

        cachedText = text;
        cachedWidth = width;
        cachedLines = List.copyOf(result);
        return cachedLines.isEmpty() ? List.of("") : cachedLines;
    }

    private List<String> wrapLines(List<String> lines, int width) {
        var wrapped = new ArrayList<String>();
        for (var line : lines) {
            if (line.isEmpty()) {
                wrapped.add("");
                continue;
            }
            var leadingSpaces = countLeadingSpaces(line);
            if (leadingSpaces > 0 && leadingSpaces < line.length()) {
                var prefix = line.substring(0, leadingSpaces);
                var remainder = line.substring(leadingSpaces);
                for (var wrappedLine : TerminalText.wrapText(remainder, Math.max(1, width - leadingSpaces))) {
                    wrapped.add(prefix + wrappedLine);
                }
                continue;
            }
            wrapped.addAll(TerminalText.wrapText(line, width));
        }
        return wrapped;
    }

    private List<String> renderBlocks(Node parent, int width, InlineStyleContext styleContext, boolean separateBlocks) {
        var lines = new ArrayList<String>();
        for (var block = parent.getFirstChild(); block != null; block = block.getNext()) {
            var rendered = renderBlock(block, width, styleContext);
            if (rendered.isEmpty()) {
                continue;
            }
            lines.addAll(rendered);
            if (separateBlocks && block.getNext() != null && !endsWithBlank(lines)) {
                lines.add("");
            }
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private List<String> renderBlock(Node block, int width, InlineStyleContext styleContext) {
        if (block instanceof Heading heading) {
            return renderHeading(heading, styleContext);
        }
        if (block instanceof Paragraph paragraph) {
            return List.of(renderInlineChildren(paragraph, styleContext));
        }
        if (block instanceof FencedCodeBlock fencedCodeBlock) {
            return renderCodeBlock(fencedCodeBlock.getInfo(), fencedCodeBlock.getLiteral());
        }
        if (block instanceof IndentedCodeBlock indentedCodeBlock) {
            return renderCodeBlock("", indentedCodeBlock.getLiteral());
        }
        if (block instanceof BulletList bulletList) {
            return renderList(bulletList, width, styleContext);
        }
        if (block instanceof OrderedList orderedList) {
            return renderList(orderedList, width, styleContext);
        }
        if (block instanceof TableBlock tableBlock) {
            return renderTable(tableBlock, width);
        }
        if (block instanceof BlockQuote blockQuote) {
            return renderBlockQuote(blockQuote, width);
        }
        if (block instanceof ThematicBreak) {
            return List.of(theme.hr("─".repeat(Math.min(width, 80))));
        }
        if (block instanceof HtmlBlock htmlBlock) {
            var literal = htmlBlock.getLiteral();
            return literal == null || literal.isBlank()
                ? List.of()
                : List.of(applyTextWithNewlines(literal.trim(), styleContext));
        }
        if (block instanceof Document document) {
            return renderBlocks(document, width, styleContext, true);
        }
        return List.of();
    }

    private List<String> renderHeading(Heading heading, InlineStyleContext styleContext) {
        var rendered = renderInlineChildren(heading, styleContext);
        var level = heading.getLevel();
        if (level == 1) {
            return List.of(theme.heading(theme.bold(theme.underline(rendered))));
        }
        if (level == 2) {
            return List.of(theme.heading(theme.bold(rendered)));
        }
        return List.of(theme.heading(theme.bold("#".repeat(level) + " " + rendered)));
    }

    private List<String> renderCodeBlock(String language, String code) {
        var lines = new ArrayList<String>();
        var info = language == null ? "" : language;
        lines.add(theme.codeBlockBorder("```" + info));
        var highlighted = theme.highlightCode(code == null ? "" : code, info);
        if (highlighted != null && !highlighted.isEmpty()) {
            for (var line : highlighted) {
                lines.add(theme.codeBlockIndent() + line);
            }
        } else {
            for (var line : (code == null ? "" : code).split("\n", -1)) {
                lines.add(theme.codeBlockIndent() + theme.codeBlock(line));
            }
        }
        lines.add(theme.codeBlockBorder("```"));
        return lines;
    }

    private List<String> renderList(ListBlock listBlock, int width, InlineStyleContext styleContext) {
        return renderList(listBlock, width, styleContext, listDepth(listBlock));
    }

    private List<String> renderList(ListBlock listBlock, int width, InlineStyleContext styleContext, int depth) {
        var lines = new ArrayList<String>();
        var ordered = listBlock instanceof OrderedList;
        var start = ordered ? ((OrderedList) listBlock).getStartNumber() : 1;
        var index = 0;
        for (var child = listBlock.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem item)) {
                continue;
            }
            var bullet = ordered ? (start + index) + ". " : "- ";
            lines.addAll(renderListItem(item, width, styleContext, bullet, depth));
            index += 1;
        }
        return lines;
    }

    private List<String> renderListItem(
        ListItem item,
        int width,
        InlineStyleContext styleContext,
        String bullet,
        int depth
    ) {
        var itemLines = new ArrayList<String>();
        for (var child = item.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListBlock listBlock) {
                itemLines.addAll(renderList(listBlock, width, styleContext, depth + 1));
                continue;
            }
            itemLines.addAll(renderBlock(child, width, styleContext));
        }

        var result = new ArrayList<String>();
        var indent = "  ".repeat(depth);
        if (itemLines.isEmpty()) {
            result.add(indent + theme.listBullet(bullet));
            return result;
        }

        var first = true;
        var nestedPrefix = "  ".repeat(depth + 1);
        for (var line : itemLines) {
            if (line.isEmpty()) {
                result.add("");
                first = false;
                continue;
            }
            if (line.startsWith(nestedPrefix)) {
                result.add(line);
                first = false;
                continue;
            }
            if (first) {
                result.add(indent + theme.listBullet(bullet) + line);
                first = false;
            } else {
                result.add(indent + "  " + line);
            }
        }
        return result;
    }

    private int listDepth(ListBlock listBlock) {
        var depth = 0;
        for (var parent = listBlock.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ListItem) {
                depth += 1;
            }
        }
        return depth;
    }

    private List<String> renderBlockQuote(BlockQuote blockQuote, int width) {
        UnaryOperator<String> quoteStyle = value -> theme.quote(theme.italic(value));
        var quoteContext = new InlineStyleContext(quoteStyle, getStylePrefix(quoteStyle));
        var innerLines = renderBlocks(blockQuote, Math.max(1, width - 2), quoteContext, true);
        var quoted = new ArrayList<String>();
        for (var line : innerLines) {
            if (line.isEmpty()) {
                quoted.add(theme.quoteBorder("│ "));
                continue;
            }
            for (var wrapped : TerminalText.wrapText(line, Math.max(1, width - 2))) {
                quoted.add(theme.quoteBorder("│ ") + wrapped);
            }
        }
        return quoted;
    }

    private List<String> renderTable(TableBlock tableBlock, int availableWidth) {
        var rows = new ArrayList<List<String>>();
        List<String> header = List.of();
        for (var child = tableBlock.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableHead head) {
                var row = firstRow(head);
                if (row != null) {
                    header = tableCells(row);
                }
            } else if (child instanceof TableBody body) {
                for (var rowNode = body.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                    if (rowNode instanceof TableRow row) {
                        rows.add(tableCells(row));
                    }
                }
            } else if (child instanceof TableRow row) {
                header = tableCells(row);
            }
        }

        if (header.isEmpty()) {
            return List.of();
        }

        var columnCount = header.size();
        var borderOverhead = 3 * columnCount + 1;
        var availableForCells = availableWidth - borderOverhead;
        if (availableForCells < columnCount) {
            return wrapLines(List.of(String.join(" | ", header)), availableWidth);
        }

        var naturalWidths = new int[columnCount];
        var minWordWidths = new int[columnCount];
        for (var column = 0; column < columnCount; column += 1) {
            naturalWidths[column] = TerminalText.visibleWidth(header.get(column));
            minWordWidths[column] = Math.max(1, longestWordWidth(header.get(column), 30));
        }
        for (var row : rows) {
            for (var column = 0; column < Math.min(columnCount, row.size()); column += 1) {
                naturalWidths[column] = Math.max(naturalWidths[column], TerminalText.visibleWidth(row.get(column)));
                minWordWidths[column] = Math.max(minWordWidths[column], longestWordWidth(row.get(column), 30));
            }
        }

        var minColumnWidths = minWordWidths.clone();
        var minCellsWidth = sum(minColumnWidths);
        if (minCellsWidth > availableForCells) {
            minColumnWidths = new int[columnCount];
            for (var column = 0; column < columnCount; column += 1) {
                minColumnWidths[column] = 1;
            }
            var remaining = availableForCells - columnCount;
            if (remaining > 0) {
                var totalWeight = 0;
                for (var minWordWidth : minWordWidths) {
                    totalWeight += Math.max(0, minWordWidth - 1);
                }
                var allocated = 0;
                for (var column = 0; column < columnCount; column += 1) {
                    var weight = Math.max(0, minWordWidths[column] - 1);
                    var growth = totalWeight == 0 ? 0 : (weight * remaining) / totalWeight;
                    minColumnWidths[column] += growth;
                    allocated += growth;
                }
                for (var column = 0; allocated < remaining; column = (column + 1) % columnCount) {
                    minColumnWidths[column] += 1;
                    allocated += 1;
                }
            }
            minCellsWidth = sum(minColumnWidths);
        }

        var columnWidths = minColumnWidths.clone();
        var totalNaturalWidth = sum(naturalWidths) + borderOverhead;
        if (totalNaturalWidth <= availableWidth) {
            for (var column = 0; column < columnCount; column += 1) {
                columnWidths[column] = Math.max(columnWidths[column], naturalWidths[column]);
            }
        } else {
            var extraWidth = Math.max(0, availableForCells - minCellsWidth);
            var totalGrowPotential = 0;
            for (var column = 0; column < columnCount; column += 1) {
                totalGrowPotential += Math.max(0, naturalWidths[column] - minColumnWidths[column]);
            }
            var allocated = 0;
            for (var column = 0; column < columnCount; column += 1) {
                var potential = Math.max(0, naturalWidths[column] - minColumnWidths[column]);
                var growth = totalGrowPotential == 0 ? 0 : (potential * extraWidth) / totalGrowPotential;
                columnWidths[column] += growth;
                allocated += growth;
            }
            for (var column = 0; allocated < extraWidth; column = (column + 1) % columnCount) {
                if (columnWidths[column] < naturalWidths[column]) {
                    columnWidths[column] += 1;
                    allocated += 1;
                } else if (column == columnCount - 1) {
                    break;
                }
            }
        }

        var lines = new ArrayList<String>();
        lines.add("┌─" + joinBorder(columnWidths, "─┬─") + "─┐");
        renderTableRow(lines, header, columnWidths, true);
        var separator = "├─" + joinBorder(columnWidths, "─┼─") + "─┤";
        lines.add(separator);
        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            renderTableRow(lines, rows.get(rowIndex), columnWidths, false);
            if (rowIndex < rows.size() - 1) {
                lines.add(separator);
            }
        }
        lines.add("└─" + joinBorder(columnWidths, "─┴─") + "─┘");
        return lines;
    }

    private TableRow firstRow(Node parent) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableRow row) {
                return row;
            }
        }
        return null;
    }

    private List<String> tableCells(TableRow row) {
        var cells = new ArrayList<String>();
        for (var child = row.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableCell cell) {
                cells.add(renderInlineChildren(cell, defaultInlineStyleContext()));
            }
        }
        return cells;
    }

    private void renderTableRow(List<String> lines, List<String> row, int[] columnWidths, boolean header) {
        var wrappedCells = new ArrayList<List<String>>();
        var height = 1;
        for (var column = 0; column < columnWidths.length; column += 1) {
            var cell = column < row.size() ? row.get(column) : "";
            var wrapped = TerminalText.wrapText(cell, Math.max(1, columnWidths[column]));
            wrappedCells.add(wrapped);
            height = Math.max(height, wrapped.size());
        }

        for (var lineIndex = 0; lineIndex < height; lineIndex += 1) {
            var parts = new ArrayList<String>();
            for (var column = 0; column < columnWidths.length; column += 1) {
                var text = lineIndex < wrappedCells.get(column).size() ? wrappedCells.get(column).get(lineIndex) : "";
                var padded = text + " ".repeat(Math.max(0, columnWidths[column] - TerminalText.visibleWidth(text)));
                parts.add(header ? theme.bold(padded) : padded);
            }
            lines.add("│ " + String.join(" │ ", parts) + " │");
        }
    }

    private int longestWordWidth(String text, int maxWidth) {
        var longest = 0;
        for (var word : text.split("\\s+")) {
            if (!word.isEmpty()) {
                longest = Math.max(longest, TerminalText.visibleWidth(word));
            }
        }
        return Math.min(longest, maxWidth);
    }

    private int sum(int[] values) {
        var total = 0;
        for (var value : values) {
            total += value;
        }
        return total;
    }

    private String joinBorder(int[] widths, String separator) {
        var parts = new ArrayList<String>();
        for (var width : widths) {
            parts.add("─".repeat(width));
        }
        return String.join(separator, parts);
    }

    private String renderInlineChildren(Node parent, InlineStyleContext styleContext) {
        var builder = new StringBuilder();
        for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
            builder.append(renderInline(child, styleContext));
        }
        return builder.toString();
    }

    private String renderInline(Node node, InlineStyleContext styleContext) {
        if (node instanceof Text textNode) {
            return applyTextWithNewlines(textNode.getLiteral(), styleContext);
        }
        if (node instanceof Paragraph paragraph) {
            return renderInlineChildren(paragraph, styleContext);
        }
        if (node instanceof Emphasis emphasis) {
            return theme.italic(renderInlineChildren(emphasis, styleContext)) + styleContext.stylePrefix();
        }
        if (node instanceof StrongEmphasis strongEmphasis) {
            return theme.bold(renderInlineChildren(strongEmphasis, styleContext)) + styleContext.stylePrefix();
        }
        if (node instanceof Code code) {
            return theme.code(code.getLiteral()) + styleContext.stylePrefix();
        }
        if (node instanceof Link link) {
            return renderLink(link, styleContext);
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            return "\n";
        }
        if (node instanceof Strikethrough strikethrough) {
            return theme.strikethrough(renderInlineChildren(strikethrough, styleContext)) + styleContext.stylePrefix();
        }
        if (node instanceof HtmlInline htmlInline) {
            return applyTextWithNewlines(htmlInline.getLiteral(), styleContext);
        }
        return renderInlineChildren(node, styleContext);
    }

    private String renderLink(Link link, InlineStyleContext styleContext) {
        var linkText = renderInlineChildren(link, styleContext);
        var rawText = plainText(link);
        var href = link.getDestination() == null ? "" : link.getDestination();
        var comparableHref = href.startsWith("mailto:") ? href.substring("mailto:".length()) : href;
        var styled = theme.link(theme.underline(linkText));
        if (rawText.equals(href) || rawText.equals(comparableHref)) {
            return styled + styleContext.stylePrefix();
        }
        return styled + theme.linkUrl(" (" + href + ")") + styleContext.stylePrefix();
    }

    private String plainText(Node node) {
        var builder = new StringBuilder();
        appendPlainText(node, builder);
        return builder.toString();
    }

    private void appendPlainText(Node node, StringBuilder builder) {
        if (node instanceof Text textNode) {
            builder.append(textNode.getLiteral());
            return;
        }
        if (node instanceof Code code) {
            builder.append(code.getLiteral());
            return;
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            builder.append('\n');
            return;
        }
        if (node instanceof HtmlInline htmlInline) {
            builder.append(htmlInline.getLiteral());
            return;
        }
        if (node instanceof HtmlBlock htmlBlock) {
            builder.append(htmlBlock.getLiteral());
            return;
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendPlainText(child, builder);
        }
    }

    private String applyTextWithNewlines(String text, InlineStyleContext styleContext) {
        var lines = text.split("\n", -1);
        var result = new ArrayList<String>(lines.length);
        for (var line : lines) {
            result.add(styleContext.applyText().apply(line));
        }
        return String.join("\n", result);
    }

    private String applyDefaultStyle(String value) {
        if (defaultTextStyle == null) {
            return value;
        }
        var styled = defaultTextStyle.color().apply(value);
        if (defaultTextStyle.bold()) {
            styled = theme.bold(styled);
        }
        if (defaultTextStyle.italic()) {
            styled = theme.italic(styled);
        }
        if (defaultTextStyle.strikethrough()) {
            styled = theme.strikethrough(styled);
        }
        if (defaultTextStyle.underline()) {
            styled = theme.underline(styled);
        }
        return styled;
    }

    private InlineStyleContext defaultInlineStyleContext() {
        return new InlineStyleContext(this::applyDefaultStyle, getDefaultStylePrefix());
    }

    private String getDefaultStylePrefix() {
        if (defaultTextStyle == null) {
            return "";
        }
        if (defaultStylePrefix != null) {
            return defaultStylePrefix;
        }
        defaultStylePrefix = getStylePrefix(this::applyDefaultStyle);
        return defaultStylePrefix;
    }

    private String getStylePrefix(UnaryOperator<String> styleFn) {
        var sentinel = "\u0000";
        var styled = styleFn.apply(sentinel);
        var index = styled.indexOf(sentinel);
        return index >= 0 ? styled.substring(0, index) : "";
    }

    private boolean endsWithBlank(List<String> lines) {
        return !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty();
    }

    private int countLeadingSpaces(String line) {
        var count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count += 1;
        }
        return count;
    }

    private record InlineStyleContext(
        UnaryOperator<String> applyText,
        String stylePrefix
    ) {
    }
}
