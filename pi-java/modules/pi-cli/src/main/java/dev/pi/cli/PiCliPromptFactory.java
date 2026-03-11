package dev.pi.cli;

import dev.pi.agent.runtime.AgentMessage;
import dev.pi.ai.model.ImageContent;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.UserContent;
import dev.pi.tools.ImageResizer;
import dev.pi.tools.ReadOperations;
import dev.pi.tools.ToolPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class PiCliPromptFactory {
    private static final ReadOperations READ_OPERATIONS = ReadOperations.local();
    private static final ImageResizer IMAGE_RESIZER = new ImageResizer();

    private PiCliPromptFactory() {
    }

    public static AgentMessage.UserMessage createInitialPrompt(PiCliArgs args, Path cwd) throws IOException {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(cwd, "cwd");

        var text = new StringBuilder();
        var images = new ArrayList<ImageContent>();

        for (var fileArg : args.fileArgs()) {
            appendFileArgument(fileArg, cwd, text, images);
        }

        if (!args.messages().isEmpty()) {
            text.append(String.join(System.lineSeparator(), args.messages()));
        }

        var content = new ArrayList<UserContent>();
        if (text.length() > 0) {
            content.add(new TextContent(text.toString(), null));
        }
        content.addAll(images);
        if (content.isEmpty()) {
            return null;
        }
        return new AgentMessage.UserMessage(content, System.currentTimeMillis());
    }

    private static void appendFileArgument(
        Path fileArg,
        Path cwd,
        StringBuilder text,
        List<ImageContent> images
    ) throws IOException {
        var absolutePath = ToolPaths.resolveReadPath(fileArg.toString(), cwd);
        READ_OPERATIONS.access(absolutePath);
        if (Files.size(absolutePath) == 0L) {
            return;
        }

        var mimeType = READ_OPERATIONS.detectImageMimeType(absolutePath);
        if (mimeType != null) {
            appendImageFile(absolutePath, mimeType, text, images);
            return;
        }
        appendTextFile(absolutePath, text);
    }

    private static void appendImageFile(
        Path absolutePath,
        String mimeType,
        StringBuilder text,
        List<ImageContent> images
    ) throws IOException {
        var bytes = READ_OPERATIONS.readFile(absolutePath);
        var resized = IMAGE_RESIZER.resize(bytes, mimeType, null);
        var dimensionNote = ImageResizer.formatDimensionNote(resized);
        text.append("<file name=\"")
            .append(absolutePath)
            .append("\">");
        if (dimensionNote != null) {
            text.append(dimensionNote);
        }
        text.append("</file>").append('\n');
        images.add(new ImageContent(Base64.getEncoder().encodeToString(resized.data()), resized.mimeType()));
    }

    private static void appendTextFile(Path absolutePath, StringBuilder text) throws IOException {
        text.append("<file name=\"")
            .append(absolutePath)
            .append("\">")
            .append('\n')
            .append(new String(READ_OPERATIONS.readFile(absolutePath), StandardCharsets.UTF_8))
            .append('\n')
            .append("</file>")
            .append('\n');
    }
}
