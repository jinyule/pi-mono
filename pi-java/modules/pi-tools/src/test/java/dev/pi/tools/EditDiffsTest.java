package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditDiffsTest {
    @TempDir
    Path tempDir;

    @Test
    void generateDiffStringIncludesStableContextAndLineNumbers() {
        var oldContent = String.join("\n", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine");
        var newContent = String.join("\n", "one", "two", "three", "four", "FIVE", "six", "seven", "eight", "nine");

        var result = EditDiffs.generateDiffString(oldContent, newContent, 1);

        assertThat(result.firstChangedLine()).isEqualTo(5);
        assertThat(result.diff()).isEqualTo(String.join("\n",
            "   ...",
            " 4 four",
            "-5 five",
            "+5 FIVE",
            " 6 six",
            "   ..."
        ));
    }

    @Test
    void computeEditDiffUsesFuzzyMatchingBeforeGeneratingDiff() throws IOException {
        var file = tempDir.resolve("sample.txt");
        Files.writeString(file, "alpha \nsmart \u201cquotes\u201d\nomega\n", StandardCharsets.UTF_8);

        var preview = EditDiffs.computeEditDiff("sample.txt", "alpha\nsmart \"quotes\"", "alpha\nsmart \"quotes\"\npatched", tempDir);

        assertThat(preview).isInstanceOf(EditDiffResult.class);
        var result = (EditDiffResult) preview;
        assertThat(result.firstChangedLine()).isEqualTo(3);
        assertThat(result.diff()).contains(" 1 alpha");
        assertThat(result.diff()).contains(" 2 smart \"quotes\"");
        assertThat(result.diff()).contains("+3 patched");
    }

    @Test
    void computeEditDiffRejectsDuplicateMatches() throws IOException {
        var file = tempDir.resolve("sample.txt");
        Files.writeString(file, "target\nmiddle\ntarget\n", StandardCharsets.UTF_8);

        var preview = EditDiffs.computeEditDiff("sample.txt", "target", "patched", tempDir);

        assertThat(preview).isInstanceOf(EditDiffError.class);
        assertThat(((EditDiffError) preview).error()).contains("must be unique");
    }

    @Test
    void stripBomRemovesLeadingUtf8Bom() {
        var stripped = EditDiffs.stripBom("\uFEFFhello");

        assertThat(stripped.bom()).isEqualTo("\uFEFF");
        assertThat(stripped.text()).isEqualTo("hello");
    }
}
