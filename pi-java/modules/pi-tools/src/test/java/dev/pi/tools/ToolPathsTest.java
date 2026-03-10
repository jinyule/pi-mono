package dev.pi.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void expandPathNormalizesUnicodeSpacesAtPrefixAndHome() {
        var homeDir = tempDir.resolve("home");
        var result = ToolPaths.expandPath("@~/file\u00A0name.txt", homeDir);

        assertThat(Path.of(result)).isEqualTo(homeDir.resolve("file name.txt"));
    }

    @Test
    void resolveToCwdKeepsAbsolutePathsAndResolvesRelativePaths() {
        var cwd = tempDir.resolve("project");
        var absolutePath = tempDir.resolve("absolute.txt").toAbsolutePath();

        assertThat(ToolPaths.resolveToCwd(absolutePath.toString(), cwd)).isEqualTo(absolutePath);
        assertThat(ToolPaths.resolveToCwd("src/Main.java", cwd)).isEqualTo(cwd.resolve("src/Main.java").normalize());
    }

    @Test
    void resolveReadPathFindsMacScreenshotAmPmVariant() throws IOException {
        var actual = tempDir.resolve("Screenshot 2024-01-01 at 10.00.00\u202FAM.png");
        Files.writeString(actual, "content");

        var resolved = ToolPaths.resolveReadPath("Screenshot 2024-01-01 at 10.00.00 AM.png", tempDir);

        assertThat(resolved).isEqualTo(actual);
    }

    @Test
    void resolveReadPathFindsCurlyQuoteVariant() throws IOException {
        var actual = tempDir.resolve("Capture d\u2019cran.txt");
        Files.writeString(actual, "content");

        var resolved = ToolPaths.resolveReadPath("Capture d'cran.txt", tempDir);

        assertThat(resolved).isEqualTo(actual);
    }

    @Test
    void resolveReadPathFindsNfdVariant() throws IOException {
        var actual = tempDir.resolve("file\u0065\u0301.txt");
        Files.writeString(actual, "content");

        var resolved = ToolPaths.resolveReadPath("file\u00e9.txt", tempDir);

        assertThat(resolved.getParent()).isEqualTo(tempDir);
        assertThat(resolved.getFileName().toString()).matches("file.+\\.txt");
    }
}
