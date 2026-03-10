package dev.pi.session;

import com.fasterxml.jackson.databind.JsonNode;
import dev.pi.ai.model.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SessionManager {
    private final SessionJsonlCodec codec;
    private Path sessionFile;
    private final boolean persistent;
    private SessionDocument document;
    private final Map<String, SessionEntry> byId = new LinkedHashMap<>();
    private final Map<String, String> labelsByTargetId = new LinkedHashMap<>();
    private String leafId;
    private boolean flushed;

    private SessionManager(SessionJsonlCodec codec, SessionDocument document, Path sessionFile, boolean persistent, boolean flushed) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.document = Objects.requireNonNull(document, "document");
        this.sessionFile = sessionFile;
        this.persistent = persistent;
        this.flushed = flushed;
        rebuildIndexes();
    }

    public static SessionManager inMemory() {
        return inMemory("");
    }

    public static SessionManager inMemory(String cwd) {
        var timestamp = nowIso();
        var header = new SessionHeader(SessionMigrations.CURRENT_SESSION_VERSION, UUID.randomUUID().toString(), timestamp, cwd, null);
        return new SessionManager(new SessionJsonlCodec(), new SessionDocument(header, List.of()), null, false, false);
    }

    public static SessionManager create(String cwd) {
        return create(cwd, defaultSessionDirectory(cwd));
    }

    public static SessionManager create(String cwd, Path sessionDirectory) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        return create(nextSessionFile(sessionDirectory), cwd);
    }

    public static SessionManager create(Path sessionFile, String cwd) {
        Objects.requireNonNull(sessionFile, "sessionFile");
        var timestamp = nowIso();
        var header = new SessionHeader(SessionMigrations.CURRENT_SESSION_VERSION, UUID.randomUUID().toString(), timestamp, cwd, null);
        return new SessionManager(new SessionJsonlCodec(), new SessionDocument(header, List.of()), sessionFile, true, false);
    }

    public static SessionManager continueRecent(String cwd) throws IOException {
        return continueRecent(cwd, defaultSessionDirectory(cwd));
    }

    public static SessionManager continueRecent(String cwd, Path sessionDirectory) throws IOException {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        var recentSession = findMostRecentSession(sessionDirectory);
        if (recentSession != null) {
            return open(recentSession);
        }
        return create(cwd, sessionDirectory);
    }

    public static SessionManager open(Path sessionFile) throws IOException {
        Objects.requireNonNull(sessionFile, "sessionFile");
        var codec = new SessionJsonlCodec();
        var originalDocument = codec.readDocument(sessionFile)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or missing session file: " + sessionFile));
        var migratedDocument = SessionMigrations.migrateToCurrentVersion(originalDocument);
        var manager = new SessionManager(
            codec,
            migratedDocument,
            sessionFile,
            true,
            Files.exists(sessionFile) && hasAssistantMessage(migratedDocument.entries())
        );
        if (needsRewrite(originalDocument)) {
            codec.writeDocument(sessionFile, migratedDocument);
            manager.flushed = Files.exists(sessionFile) && hasAssistantMessage(migratedDocument.entries());
        }
        return manager;
    }

    public static Path defaultSessionDirectory(String cwd) {
        Objects.requireNonNull(cwd, "cwd");
        var normalized = cwd.replaceFirst("^[\\\\/]+", "").replaceAll("[\\\\/:]", "-");
        var safePath = "--" + normalized + "--";
        return Path.of(System.getProperty("user.home"), ".pi", "agent", "sessions", safePath);
    }

    public static Path findMostRecentSession(Path sessionDirectory) throws IOException {
        Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        if (!Files.isDirectory(sessionDirectory)) {
            return null;
        }
        try (var stream = Files.list(sessionDirectory)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .filter(SessionManager::isValidSessionFile)
                .max(Comparator.comparing(SessionManager::lastModifiedTime))
                .orElse(null);
        }
    }

    public static List<SessionInfo> list(Path sessionDirectory) throws IOException {
        Objects.requireNonNull(sessionDirectory, "sessionDirectory");
        if (!Files.isDirectory(sessionDirectory)) {
            return List.of();
        }
        var sessions = new ArrayList<SessionInfo>();
        try (var stream = Files.list(sessionDirectory)) {
            for (var path : stream
                .filter(candidate -> candidate.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.comparing(SessionManager::lastModifiedTime).reversed())
                .toList()) {
                var info = buildSessionInfo(path);
                if (info != null) {
                    sessions.add(info);
                }
            }
        }
        sessions.sort(Comparator.comparing(SessionInfo::modified).reversed());
        return List.copyOf(sessions);
    }

    public String sessionId() {
        return document.header().id();
    }

    public SessionHeader header() {
        return document.header();
    }

    public Path sessionFile() {
        return sessionFile;
    }

    public boolean persistent() {
        return persistent;
    }

    public String leafId() {
        return leafId;
    }

    public SessionEntry leafEntry() {
        return leafId == null ? null : byId.get(leafId);
    }

    public SessionEntry entry(String id) {
        return byId.get(id);
    }

    public String label(String targetId) {
        return labelsByTargetId.get(targetId);
    }

    public List<SessionEntry> entries() {
        return document.entries();
    }

    public SessionContext buildSessionContext() {
        return SessionContexts.buildSessionContext(document.entries(), leafId);
    }

    public SessionContext buildSessionContext(String explicitLeafId) {
        return SessionContexts.buildSessionContext(document.entries(), explicitLeafId);
    }

    public List<SessionEntry> branch() {
        return branch(leafId);
    }

    public List<SessionEntry> branch(String fromId) {
        if (fromId == null) {
            return List.of();
        }
        var path = new ArrayList<SessionEntry>();
        SessionEntry current = byId.get(fromId);
        while (current != null) {
            path.addFirst(current);
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return List.copyOf(path);
    }

    public List<SessionTreeNode> tree() {
        var nodeById = new LinkedHashMap<String, MutableTreeNode>();
        var roots = new ArrayList<MutableTreeNode>();

        for (var entry : document.entries()) {
            nodeById.put(entry.id(), new MutableTreeNode(entry, labelsByTargetId.get(entry.id())));
        }

        for (var entry : document.entries()) {
            var node = nodeById.get(entry.id());
            if (node == null) {
                continue;
            }
            if (entry.parentId() == null || entry.parentId().equals(entry.id())) {
                roots.add(node);
                continue;
            }
            var parent = nodeById.get(entry.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }

        var stack = new ArrayList<MutableTreeNode>(roots);
        while (!stack.isEmpty()) {
            var node = stack.removeLast();
            node.children.sort((left, right) -> left.entry.timestamp().compareTo(right.entry.timestamp()));
            stack.addAll(node.children);
        }

        return roots.stream().map(SessionManager::freezeTreeNode).toList();
    }

    public void navigate(String newLeafId) {
        if (newLeafId == null) {
            leafId = null;
            return;
        }
        if (!byId.containsKey(newLeafId)) {
            throw new IllegalArgumentException("Unknown session entry: " + newLeafId);
        }
        leafId = newLeafId;
    }

    public String branchWithSummary(String branchFromId, String summary, JsonNode details, Boolean fromHook) throws IOException {
        navigate(branchFromId);
        var entry = new SessionEntry.BranchSummaryEntry(
            nextEntryId(),
            branchFromId,
            nowIso(),
            branchFromId == null ? "root" : branchFromId,
            summary,
            details,
            fromHook
        );
        appendEntry(entry);
        return entry.id();
    }

    public Path createBranchedSession(String targetLeafId) throws IOException {
        if (targetLeafId != null && !byId.containsKey(targetLeafId)) {
            throw new IllegalArgumentException("Unknown session entry: " + targetLeafId);
        }

        var path = branch(targetLeafId);
        var pathEntryIds = path.stream()
            .map(SessionEntry::id)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        var pathWithoutLabels = path.stream()
            .filter(entry -> !(entry instanceof SessionEntry.LabelEntry))
            .toList();

        var newSessionId = UUID.randomUUID().toString();
        var timestamp = nowIso();
        Path newSessionFile = null;
        if (persistent && sessionFile != null) {
            var fileName = timestamp.replace(":", "-").replace(".", "-") + "_" + newSessionId + ".jsonl";
            newSessionFile = sessionFile.getParent() == null ? Path.of(fileName) : sessionFile.getParent().resolve(fileName);
        }

        var newHeader = new SessionHeader(
            "session",
            SessionMigrations.CURRENT_SESSION_VERSION,
            newSessionId,
            timestamp,
            document.header().cwd(),
            sessionFile == null ? null : sessionFile.toString(),
            document.header().provider(),
            document.header().modelId(),
            document.header().thinkingLevel()
        );

        var rewrittenEntries = new ArrayList<SessionEntry>(pathWithoutLabels);
        String parentId = pathWithoutLabels.isEmpty() ? null : pathWithoutLabels.getLast().id();
        for (var labelEntry : preservedLabelEntries(pathEntryIds, parentId)) {
            rewrittenEntries.add(labelEntry);
            parentId = labelEntry.id();
        }

        document = new SessionDocument(newHeader, rewrittenEntries);
        sessionFile = newSessionFile;
        rebuildIndexes();

        if (persistent && sessionFile != null) {
            if (hasAssistantMessage(document.entries())) {
                codec.writeDocument(sessionFile, document);
                flushed = true;
            } else {
                flushed = false;
            }
        } else {
            flushed = false;
        }

        return sessionFile;
    }

    public String appendMessage(Message message) throws IOException {
        Objects.requireNonNull(message, "message");
        return appendRawMessage(codec.valueToTree(message));
    }

    public String appendRawMessage(JsonNode message) throws IOException {
        Objects.requireNonNull(message, "message");
        var entry = new SessionEntry.MessageEntry(nextEntryId(), leafId, nowIso(), message);
        appendEntry(entry);
        return entry.id();
    }

    public String appendThinkingLevelChange(String thinkingLevel) throws IOException {
        var entry = new SessionEntry.ThinkingLevelChangeEntry(nextEntryId(), leafId, nowIso(), thinkingLevel);
        appendEntry(entry);
        return entry.id();
    }

    public String appendModelChange(String provider, String modelId) throws IOException {
        var entry = new SessionEntry.ModelChangeEntry(nextEntryId(), leafId, nowIso(), provider, modelId);
        appendEntry(entry);
        return entry.id();
    }

    public String appendCompaction(String summary, String firstKeptEntryId, int tokensBefore, JsonNode details, Boolean fromHook)
        throws IOException {
        var entry = new SessionEntry.CompactionEntry(
            nextEntryId(),
            leafId,
            nowIso(),
            summary,
            firstKeptEntryId,
            tokensBefore,
            details,
            fromHook
        );
        appendEntry(entry);
        return entry.id();
    }

    public String appendBranchSummary(String fromId, String summary, JsonNode details, Boolean fromHook) throws IOException {
        var entry = new SessionEntry.BranchSummaryEntry(nextEntryId(), leafId, nowIso(), fromId, summary, details, fromHook);
        appendEntry(entry);
        return entry.id();
    }

    public String appendCustomEntry(String customType, JsonNode data) throws IOException {
        var entry = new SessionEntry.CustomEntry(nextEntryId(), leafId, nowIso(), customType, data);
        appendEntry(entry);
        return entry.id();
    }

    public String appendCustomMessage(String customType, JsonNode content, JsonNode details, boolean display) throws IOException {
        var entry = new SessionEntry.CustomMessageEntry(nextEntryId(), leafId, nowIso(), customType, content, details, display);
        appendEntry(entry);
        return entry.id();
    }

    public String appendLabelChange(String targetId, String label) throws IOException {
        if (!byId.containsKey(targetId)) {
            throw new IllegalArgumentException("Unknown session entry: " + targetId);
        }
        var entry = new SessionEntry.LabelEntry(nextEntryId(), leafId, nowIso(), targetId, label);
        appendEntry(entry);
        if (label == null || label.isBlank()) {
            labelsByTargetId.remove(targetId);
        } else {
            labelsByTargetId.put(targetId, label);
        }
        return entry.id();
    }

    public String appendSessionInfo(String name) throws IOException {
        var entry = new SessionEntry.SessionInfoEntry(nextEntryId(), leafId, nowIso(), name);
        appendEntry(entry);
        return entry.id();
    }

    private void appendEntry(SessionEntry entry) throws IOException {
        var entries = new ArrayList<>(document.entries());
        entries.add(entry);
        document = new SessionDocument(document.header(), entries);
        rebuildIndexes();
        persist(entry);
    }

    private void rebuildIndexes() {
        byId.clear();
        labelsByTargetId.clear();
        leafId = null;
        for (var entry : document.entries()) {
            byId.put(entry.id(), entry);
            leafId = entry.id();
            if (entry instanceof SessionEntry.LabelEntry labelEntry) {
                if (labelEntry.label() == null || labelEntry.label().isBlank()) {
                    labelsByTargetId.remove(labelEntry.targetId());
                } else {
                    labelsByTargetId.put(labelEntry.targetId(), labelEntry.label());
                }
            }
        }
    }

    private void persist(SessionEntry entry) throws IOException {
        if (!persistent || sessionFile == null) {
            return;
        }
        if (!hasAssistantMessage(document.entries())) {
            flushed = false;
            return;
        }
        if (!flushed) {
            codec.writeDocument(sessionFile, document);
            flushed = true;
            return;
        }
        var parent = sessionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(sessionFile, codec.writeLine(entry) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private String nextEntryId() {
        while (true) {
            var id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (!byId.containsKey(id)) {
                return id;
            }
        }
    }

    private static String nowIso() {
        return Instant.now().toString();
    }

    private List<SessionEntry.LabelEntry> preservedLabelEntries(java.util.Set<String> pathEntryIds, String initialParentId) {
        var labelEntries = new ArrayList<SessionEntry.LabelEntry>();
        var parentId = initialParentId;
        for (var entryId : pathEntryIds) {
            var label = labelsByTargetId.get(entryId);
            if (label == null || label.isBlank()) {
                continue;
            }
            var labelEntry = new SessionEntry.LabelEntry(nextEntryId(), parentId, nowIso(), entryId, label);
            labelEntries.add(labelEntry);
            parentId = labelEntry.id();
        }
        return labelEntries;
    }

    private static boolean needsRewrite(SessionDocument document) {
        return document.header().version() == null || document.header().version() < SessionMigrations.CURRENT_SESSION_VERSION;
    }

    private static boolean hasAssistantMessage(List<SessionEntry> entries) {
        for (var entry : entries) {
            if (entry instanceof SessionEntry.MessageEntry messageEntry && "assistant".equals(messageEntry.message().path("role").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private static SessionInfo buildSessionInfo(Path sessionFile) {
        try {
            var codec = new SessionJsonlCodec();
            var document = codec.readDocument(sessionFile)
                .map(SessionMigrations::migrateToCurrentVersion)
                .orElse(null);
            if (document == null) {
                return null;
            }

            var header = document.header();
            var created = parseInstant(header.timestamp(), Instant.EPOCH);
            var modified = lastModifiedInstant(document.entries(), created, sessionFile);
            var sessionName = "";
            var messageCount = 0;
            var firstMessage = "";
            var allMessages = new ArrayList<String>();

            for (var entry : document.entries()) {
                if (entry instanceof SessionEntry.SessionInfoEntry infoEntry && infoEntry.name() != null && !infoEntry.name().isBlank()) {
                    sessionName = infoEntry.name().trim();
                    continue;
                }
                if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                    continue;
                }

                var message = messageEntry.message();
                var role = message.path("role").asText("");
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }

                messageCount += 1;
                var text = extractTextContent(message);
                if (text.isBlank()) {
                    continue;
                }

                allMessages.add(text);
                if (firstMessage.isBlank() && "user".equals(role)) {
                    firstMessage = text;
                }
            }

            return new SessionInfo(
                sessionFile,
                header.id(),
                header.cwd(),
                sessionName.isBlank() ? null : sessionName,
                header.parentSession(),
                created,
                modified,
                messageCount,
                firstMessage,
                String.join(" ", allMessages)
            );
        } catch (IOException exception) {
            return null;
        }
    }

    private static String extractTextContent(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return "";
        }

        var content = message.path("content");
        if (content.isTextual()) {
            return content.asText("").trim();
        }
        if (!content.isArray()) {
            return "";
        }

        var text = new ArrayList<String>();
        for (var item : content) {
            if ("text".equals(item.path("type").asText(""))) {
                var value = item.path("text").asText("");
                if (!value.isBlank()) {
                    text.add(value.trim());
                }
            }
        }
        return String.join(" ", text);
    }

    private static Instant lastModifiedInstant(List<SessionEntry> entries, Instant created, Path sessionFile) {
        Instant latest = created;
        for (var entry : entries) {
            if (!(entry instanceof SessionEntry.MessageEntry messageEntry)) {
                continue;
            }

            var role = messageEntry.message().path("role").asText("");
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }

            var candidate = parseMessageInstant(messageEntry.message(), parseInstant(entry.timestamp(), created));
            if (candidate.isAfter(latest)) {
                latest = candidate;
            }
        }

        var fileModified = lastModifiedTime(sessionFile).toInstant();
        return fileModified.isAfter(latest) ? fileModified : latest;
    }

    private static Instant parseMessageInstant(JsonNode message, Instant fallback) {
        var timestampNode = message.path("timestamp");
        if (timestampNode.canConvertToLong()) {
            try {
                return Instant.ofEpochMilli(timestampNode.asLong());
            } catch (DateTimeException exception) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            return fallback;
        }
    }

    private static Path nextSessionFile(Path sessionDirectory) {
        var timestamp = nowIso().replace(":", "-").replace(".", "-");
        var sessionId = UUID.randomUUID().toString();
        return sessionDirectory.resolve(timestamp + "_" + sessionId + ".jsonl");
    }

    private static boolean isValidSessionFile(Path path) {
        try {
            return new SessionJsonlCodec().readDocument(path).isPresent();
        } catch (IOException exception) {
            return false;
        }
    }

    private static FileTime lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            return FileTime.fromMillis(Long.MIN_VALUE);
        }
    }

    private static SessionTreeNode freezeTreeNode(MutableTreeNode node) {
        return new SessionTreeNode(
            node.entry,
            node.children.stream().map(SessionManager::freezeTreeNode).toList(),
            node.label
        );
    }

    private static final class MutableTreeNode {
        private final SessionEntry entry;
        private final List<MutableTreeNode> children = new ArrayList<>();
        private final String label;

        private MutableTreeNode(SessionEntry entry, String label) {
            this.entry = entry;
            this.label = label;
        }
    }
}
