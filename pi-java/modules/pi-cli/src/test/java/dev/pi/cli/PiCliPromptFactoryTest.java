package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PiCliPromptFactoryTest {
    private static final String TINY_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+XxK0AAAAASUVORK5CYII=";

    @Test
    void buildsPromptFromTextAndImageFiles(@TempDir Path tempDir) throws Exception {
        var textFile = tempDir.resolve("notes.txt");
        var imageFile = tempDir.resolve("image.png");
        Files.writeString(textFile, "Context line", StandardCharsets.UTF_8);
        Files.write(imageFile, Base64.getDecoder().decode(TINY_PNG_BASE64));

        var args = new PiCliParser().parse(
            "@" + textFile,
            "@" + imageFile,
            "Explain the issue"
        );

        var prompt = PiCliPromptFactory.createInitialPrompt(args, tempDir);

        assertThat(prompt).isNotNull();
        assertThat(prompt.content().getFirst()).isInstanceOf(TextContent.class);
        var text = ((TextContent) prompt.content().getFirst()).text();
        assertThat(text)
            .contains("<file name=\"" + textFile + "\">\nContext line\n</file>")
            .contains("<file name=\"" + imageFile + "\">")
            .contains("Explain the issue");
        assertThat(prompt.content().stream().filter(ImageContent.class::isInstance).count()).isEqualTo(1);
    }
}
