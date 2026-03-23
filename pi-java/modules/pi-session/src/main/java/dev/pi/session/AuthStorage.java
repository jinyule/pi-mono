package dev.pi.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthStorage {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Storage storage;
    private Map<String, AuthCredential> credentials = Map.of();
    private final List<Exception> errors = new ArrayList<>();

    private AuthStorage(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
        reload();
    }

    public static AuthStorage create() {
        return create(PiAgentPaths.agentDir().resolve("auth.json"));
    }

    public static AuthStorage create(Path authPath) {
        Objects.requireNonNull(authPath, "authPath");
        return new AuthStorage(new FileStorage(authPath));
    }

    public static AuthStorage inMemory() {
        return inMemory(Map.of());
    }

    public static AuthStorage inMemory(Map<String, AuthCredential> initialCredentials) {
        var storage = new InMemoryStorage(serialize(initialCredentials));
        return new AuthStorage(storage);
    }

    public synchronized void reload() {
        try {
            credentials = storage.read(AuthStorage::parseCredentials);
        } catch (IOException | RuntimeException exception) {
            errors.add(exception);
        }
    }

    public synchronized AuthCredential get(String provider) {
        return credentials.get(provider);
    }

    public synchronized String getApiKey(String provider) {
        var credential = credentials.get(provider);
        if (credential instanceof ApiKeyCredential apiKeyCredential) {
            return apiKeyCredential.key();
        }
        if (credential instanceof OAuthCredential oauthCredential) {
            return oauthCredential.accessToken();
        }
        return null;
    }

    public synchronized boolean has(String provider) {
        return credentials.containsKey(provider);
    }

    public synchronized List<String> providers() {
        return credentials.keySet().stream().sorted().toList();
    }

    public synchronized void setApiKey(String provider, String apiKey) {
        var normalizedProvider = normalizeProvider(provider);
        var normalizedApiKey = normalizeApiKey(apiKey);
        update(current -> {
            current.put(normalizedProvider, new ApiKeyCredential(normalizedApiKey));
            return current;
        });
    }

    public synchronized void remove(String provider) {
        var normalizedProvider = normalizeProvider(provider);
        update(current -> {
            current.remove(normalizedProvider);
            return current;
        });
    }

    public synchronized List<Exception> drainErrors() {
        var drained = List.copyOf(errors);
        errors.clear();
        return drained;
    }

    private void update(java.util.function.UnaryOperator<Map<String, AuthCredential>> update) {
        try {
            credentials = storage.update(current -> {
                var next = new LinkedHashMap<>(current);
                var updated = new LinkedHashMap<>(Objects.requireNonNull(update.apply(next), "update result"));
                return new UpdateResult<>(Map.copyOf(updated), serialize(updated));
            });
        } catch (IOException | RuntimeException exception) {
            errors.add(exception);
        }
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        return provider.trim();
    }

    private static String normalizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        return apiKey.trim();
    }

    private static Map<String, AuthCredential> parseCredentials(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            var json = OBJECT_MAPPER.readTree(content);
            if (json == null || json.isNull()) {
                return Map.of();
            }
            if (!(json instanceof ObjectNode objectNode)) {
                throw new IllegalArgumentException("Auth JSON must be an object");
            }

            var parsed = new LinkedHashMap<String, AuthCredential>();
            var fields = objectNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var provider = entry.getKey();
                var node = entry.getValue();
                if (!(node instanceof ObjectNode credentialNode)) {
                    continue;
                }
                var type = credentialNode.path("type").asText("");
                if ("api_key".equals(type)) {
                    var key = credentialNode.path("key").asText("");
                    if (!key.isBlank()) {
                        parsed.put(provider, new ApiKeyCredential(key));
                    }
                    continue;
                }
                if ("oauth".equals(type)) {
                    var access = credentialNode.path("access").asText("");
                    if (!access.isBlank()) {
                        parsed.put(provider, new OAuthCredential(access, credentialNode.deepCopy()));
                    }
                }
            }
            return Map.copyOf(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse auth JSON", exception);
        }
    }

    private static String serialize(Map<String, AuthCredential> credentials) {
        var root = OBJECT_MAPPER.createObjectNode();
        credentials.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> root.set(entry.getKey(), entry.getValue().toObjectNode()));
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize auth JSON", exception);
        }
    }

    @FunctionalInterface
    interface Reader<T> {
        T apply(String current) throws IOException;
    }

    @FunctionalInterface
    interface Updater<T> {
        UpdateResult<T> apply(Map<String, AuthCredential> current) throws IOException;
    }

    interface Storage {
        <T> T read(Reader<T> reader) throws IOException;

        <T> T update(Updater<T> updater) throws IOException;
    }

    static final class FileStorage implements Storage {
        private static final ConcurrentHashMap<Path, Object> PROCESS_LOCKS = new ConcurrentHashMap<>();

        private final Path authPath;

        private FileStorage(Path authPath) {
            this.authPath = Objects.requireNonNull(authPath, "authPath");
        }

        @Override
        public <T> T read(Reader<T> reader) throws IOException {
            Objects.requireNonNull(reader, "reader");
            if (!Files.exists(authPath)) {
                return reader.apply(null);
            }
            return withLock(false, content -> new UpdateResult<>(reader.apply(content), null));
        }

        @Override
        public <T> T update(Updater<T> updater) throws IOException {
            Objects.requireNonNull(updater, "updater");
            return withLock(true, content -> updater.apply(parseCredentials(content)));
        }

        private <T> T withLock(boolean createParent, Reader<UpdateResult<T>> action) throws IOException {
            var parent = authPath.getParent();
            if (createParent && parent != null) {
                Files.createDirectories(parent);
            }

            var lockPath = authPath.resolveSibling(authPath.getFileName() + ".lock");
            var mutex = PROCESS_LOCKS.computeIfAbsent(lockPath.toAbsolutePath().normalize(), ignored -> new Object());

            synchronized (mutex) {
                try (
                    var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var ignored = channel.lock()
                ) {
                    var current = Files.exists(authPath) ? Files.readString(authPath, StandardCharsets.UTF_8) : null;
                    var result = action.apply(current);
                    if (result.serialized() != null) {
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.writeString(authPath, result.serialized(), StandardCharsets.UTF_8);
                    }
                    return result.value();
                }
            }
        }
    }

    static final class InMemoryStorage implements Storage {
        private String content;

        private InMemoryStorage(String content) {
            this.content = content;
        }

        @Override
        public synchronized <T> T read(Reader<T> reader) throws IOException {
            return reader.apply(content);
        }

        @Override
        public synchronized <T> T update(Updater<T> updater) throws IOException {
            var result = updater.apply(parseCredentials(content));
            if (result.serialized() != null) {
                content = result.serialized();
            }
            return result.value();
        }
    }

    public sealed interface AuthCredential permits ApiKeyCredential, OAuthCredential {
        ObjectNode toObjectNode();
    }

    public record ApiKeyCredential(String key) implements AuthCredential {
        public ApiKeyCredential {
            key = normalizeApiKey(key);
        }

        @Override
        public ObjectNode toObjectNode() {
            var node = OBJECT_MAPPER.createObjectNode();
            node.put("type", "api_key");
            node.put("key", key);
            return node;
        }
    }

    public record OAuthCredential(
        String accessToken,
        ObjectNode raw
    ) implements AuthCredential {
        public OAuthCredential {
            accessToken = normalizeApiKey(accessToken);
            raw = Objects.requireNonNull(raw, "raw").deepCopy();
        }

        @Override
        public ObjectNode toObjectNode() {
            return raw.deepCopy();
        }
    }

    record UpdateResult<T>(
        T value,
        String serialized
    ) {
    }
}
