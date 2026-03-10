package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTruncatorTest {
    @Test
    void truncateHeadReturnsOriginalWhenWithinLimits() {
        var result = TextTruncator.truncateHead("alpha\nbeta", new TruncationOptions(10, 1024));

        assertThat(result.truncated()).isFalse();
        assertThat(result.content()).isEqualTo("alpha\nbeta");
        assertThat(result.totalLines()).isEqualTo(2);
        assertThat(result.outputLines()).isEqualTo(2);
        assertThat(result.truncatedBy()).isNull();
    }

    @Test
    void truncateHeadFlagsOversizedFirstLine() {
        var result = TextTruncator.truncateHead("abcdefghij", new TruncationOptions(10, 5));

        assertThat(result.truncated()).isTrue();
        assertThat(result.content()).isEmpty();
        assertThat(result.firstLineExceedsLimit()).isTrue();
        assertThat(result.truncatedBy()).isEqualTo(TruncationLimit.BYTES);
    }

    @Test
    void truncateTailKeepsPartialLineWhenSingleLineExceedsByteLimit() {
        var result = TextTruncator.truncateTail("prefix-abcdefghij", new TruncationOptions(10, 4));

        assertThat(result.truncated()).isTrue();
        assertThat(result.lastLinePartial()).isTrue();
        assertThat(result.content()).isEqualTo("ghij");
        assertThat(result.truncatedBy()).isEqualTo(TruncationLimit.BYTES);
    }

    @Test
    void truncateHeadStopsAtLineLimitBeforeByteLimit() {
        var result = TextTruncator.truncateHead("a\nb\nc\nd", new TruncationOptions(2, 1024));

        assertThat(result.truncated()).isTrue();
        assertThat(result.content()).isEqualTo("a\nb");
        assertThat(result.truncatedBy()).isEqualTo(TruncationLimit.LINES);
    }

    @Test
    void truncateLineAddsStableSuffix() {
        var result = TextTruncator.truncateLine("abcdefghij", 5);

        assertThat(result.wasTruncated()).isTrue();
        assertThat(result.text()).isEqualTo("abcde... [truncated]");
    }

    @Test
    void formatSizeUsesHumanReadableUnits() {
        assertThat(TextTruncator.formatSize(512)).isEqualTo("512B");
        assertThat(TextTruncator.formatSize(1536)).isEqualTo("1.5KB");
        assertThat(TextTruncator.formatSize(2 * 1024 * 1024)).isEqualTo("2.0MB");
    }
}
