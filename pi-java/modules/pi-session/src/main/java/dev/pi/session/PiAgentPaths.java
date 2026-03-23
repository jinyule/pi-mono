package dev.pi.session;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class PiAgentPaths {
    static final String ENV_AGENT_DIR = "PI_CODING_AGENT_DIR";

    private PiAgentPaths() {
    }

    public static Path agentDir() {
        return agentDir(System.getenv(), homeDir());
    }

    public static Path sessionsDir() {
        return agentDir().resolve("sessions").toAbsolutePath().normalize();
    }

    public static Path debugLogPath() {
        return agentDir().resolve("debug.log").toAbsolutePath().normalize();
    }

    static Path homeDir() {
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    static Path agentDir(Map<String, String> environment, Path homeDir) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(homeDir, "homeDir");
        var configured = environment.get(ENV_AGENT_DIR);
        if (configured == null || configured.isBlank()) {
            return homeDir.resolve(".pi").resolve("agent").toAbsolutePath().normalize();
        }
        return expandPath(configured, homeDir);
    }

    public static Path expandPath(String rawPath, Path homeDir) {
        Objects.requireNonNull(rawPath, "rawPath");
        Objects.requireNonNull(homeDir, "homeDir");
        var trimmed = rawPath.trim();
        if (trimmed.equals("~")) {
            return homeDir.toAbsolutePath().normalize();
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return homeDir.resolve(trimmed.substring(2)).toAbsolutePath().normalize();
        }
        return Path.of(trimmed).toAbsolutePath().normalize();
    }
}
