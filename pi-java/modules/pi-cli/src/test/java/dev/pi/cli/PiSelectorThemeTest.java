package dev.pi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pi.session.SessionEntry;
import dev.pi.session.SessionTreeNode;
import dev.pi.tui.EditorAction;
import dev.pi.tui.EditorKeybindings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiSelectorThemeTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void treeSelectorUsesSharedAnsiHierarchy() {
        var previous = EditorKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings(Map.of(
                EditorAction.SUBMIT, List.of("ctrl+j"),
                EditorAction.SELECT_CANCEL, List.of("ctrl+x")
            )));
            var selector = new PiTreeSelector(
                List.of(new SessionTreeNode(
                    new SessionEntry.MessageEntry(
                        "entry-1",
                        null,
                        "2026-03-11T00:00:00Z",
                        userMessage("hello tree")
                    ),
                    List.of(),
                    null
                )),
                "entry-1",
                ignored -> {
                },
                () -> {
                },
                () -> {
                }
            );

            var rendered = String.join("\n", selector.render(80));

            assertThat(rendered)
                .contains("\u001b[1mNavigate session tree\u001b[0m")
                .contains("\u001b[90mType to filter. \u001b[0m")
                .contains("\u001b[2;37mctrl+j\u001b[0m")
                .contains("\u001b[90m selects.\u001b[0m")
                .contains("\u001b[2;37mctrl+x\u001b[0m")
                .contains("\u001b[90m cancels.\u001b[0m")
                .contains("\u001b[36m")
                .contains("\u001b[1m");
        } finally {
            EditorKeybindings.setGlobal(previous);
        }
    }

    @Test
    void forkSelectorUsesSharedAnsiHierarchy() {
        var previous = EditorKeybindings.global();
        try {
            EditorKeybindings.setGlobal(new EditorKeybindings(Map.of(
                EditorAction.SUBMIT, List.of("ctrl+j"),
                EditorAction.SELECT_CANCEL, List.of("ctrl+x")
            )));
            var selector = new PiForkSelector(
                List.of(
                    new PiInteractiveSession.ForkMessage("entry-1", "first"),
                    new PiInteractiveSession.ForkMessage("entry-2", "second")
                ),
                ignored -> {
                },
                () -> {
                },
                () -> {
                }
            );

            var rendered = String.join("\n", selector.render(80));

            assertThat(rendered)
                .contains("\u001b[1mFork from previous message\u001b[0m")
                .contains("\u001b[90mType to filter. \u001b[0m")
                .contains("\u001b[2;37mctrl+j\u001b[0m")
                .contains("\u001b[90m forks.\u001b[0m")
                .contains("\u001b[2;37mctrl+x\u001b[0m")
                .contains("\u001b[90m cancels.\u001b[0m")
                .contains("\u001b[36m")
                .contains("\u001b[1m");
        } finally {
            EditorKeybindings.setGlobal(previous);
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode userMessage(String text) {
        var content = OBJECT_MAPPER.createArrayNode()
            .add(OBJECT_MAPPER.createObjectNode()
                .put("type", "text")
                .put("text", text));
        return OBJECT_MAPPER.createObjectNode()
            .put("role", "user")
            .set("content", content);
    }
}
