package dev.pi.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SessionJsonlCodec {
    private final ObjectMapper objectMapper;

    public SessionJsonlCodec() {
        this(new ObjectMapper());
    }

    public SessionJsonlCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<SessionFileEntry> parseLines(String jsonl) {
        Objects.requireNonNull(jsonl, "jsonl");
        var parsedEntries = new ArrayList<SessionFileEntry>();
        for (var line : jsonl.split("\\R")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            parseLine(line).ifPresent(parsedEntries::add);
        }
        return List.copyOf(parsedEntries);
    }

    public Optional<SessionDocument> parseDocument(String jsonl) {
        var parsedEntries = parseLines(jsonl);
        if (parsedEntries.isEmpty() || !(parsedEntries.getFirst() instanceof SessionHeader header)) {
            return Optional.empty();
        }

        var entries = parsedEntries.stream()
            .skip(1)
            .filter(SessionEntry.class::isInstance)
            .map(SessionEntry.class::cast)
            .toList();
        return Optional.of(new SessionDocument(header, entries));
    }

    public Optional<SessionDocument> readDocument(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return parseDocument(Files.readString(path, StandardCharsets.UTF_8));
    }

    public String writeDocument(SessionDocument document) {
        Objects.requireNonNull(document, "document");
        return writeLines(document.fileEntries());
    }

    public void writeDocument(Path path, SessionDocument document) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(document, "document");
        var parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, writeDocument(document), StandardCharsets.UTF_8);
    }

    public String writeLines(List<? extends SessionFileEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return "";
        }
        return entries.stream()
            .map(this::writeLine)
            .collect(Collectors.joining("\n", "", "\n"));
    }

    public String writeLine(SessionFileEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to write session JSONL line", exception);
        }
    }

    public JsonNode valueToTree(Object value) {
        return objectMapper.valueToTree(value);
    }

    private Optional<SessionFileEntry> parseLine(String line) {
        try {
            var json = objectMapper.readTree(line);
            if (json == null || !json.hasNonNull("type")) {
                return Optional.empty();
            }
            return switch (json.get("type").asText()) {
                case "session" -> Optional.of(objectMapper.treeToValue(json, SessionHeader.class));
                case "message" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.MessageEntry.class));
                case "thinking_level_change" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.ThinkingLevelChangeEntry.class));
                case "model_change" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.ModelChangeEntry.class));
                case "compaction" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.CompactionEntry.class));
                case "branch_summary" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.BranchSummaryEntry.class));
                case "custom" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.CustomEntry.class));
                case "custom_message" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.CustomMessageEntry.class));
                case "label" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.LabelEntry.class));
                case "session_info" -> Optional.of(objectMapper.treeToValue(json, SessionEntry.SessionInfoEntry.class));
                default -> Optional.empty();
            };
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }
}
