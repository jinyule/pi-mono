package dev.pi.sdk;

import dev.pi.ai.registry.ModelRegistry;
import dev.pi.session.SessionManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class PiSdk {
    private final ModelRegistry modelRegistry;

    public PiSdk() {
        this(new ModelRegistry());
    }

    public PiSdk(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public SessionManager createInMemorySession(String cwd) {
        return SessionManager.inMemory(cwd);
    }

    public SessionManager createPersistentSession(Path sessionFile, String cwd) {
        return SessionManager.create(sessionFile, cwd);
    }

    public SessionManager openSession(Path sessionFile) throws IOException {
        return SessionManager.open(sessionFile);
    }

    public PiSdkSession createAgentSession(CreateAgentSessionOptions options) {
        Objects.requireNonNull(options, "options");
        return PiSdkSession.create(options);
    }
}
