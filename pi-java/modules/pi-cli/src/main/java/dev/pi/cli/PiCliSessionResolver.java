package dev.pi.cli;

import dev.pi.session.SessionManager;
import dev.pi.session.SessionInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public final class PiCliSessionResolver {
    private final Path cwd;
    private final SessionPicker sessionPicker;

    public PiCliSessionResolver(Path cwd) {
        this(cwd, new PiSessionPicker());
    }

    public PiCliSessionResolver(Path cwd, SessionPicker sessionPicker) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.sessionPicker = Objects.requireNonNull(sessionPicker, "sessionPicker");
    }

    public SessionManager resolve(PiCliArgs args) throws IOException {
        Objects.requireNonNull(args, "args");

        if (args.noSession()) {
            return SessionManager.inMemory(cwd.toString());
        }

        if (args.sessionPath() != null) {
            return resolveExplicitSessionPath(args.sessionPath());
        }

        if (args.resumeRequested()) {
            return resolvePickedSession(args);
        }

        if (args.continueSession()) {
            return SessionManager.continueRecent(cwd.toString(), resolveSessionDirectory(args.sessionDirectory()));
        }

        if (args.sessionDirectory() != null) {
            return SessionManager.create(cwd.toString(), resolveSessionDirectory(args.sessionDirectory()));
        }

        return SessionManager.create(cwd.toString());
    }

    private SessionManager resolveExplicitSessionPath(Path sessionPath) throws IOException {
        var normalizedPath = sessionPath.toAbsolutePath().normalize();
        if (Files.exists(normalizedPath)) {
            return SessionManager.open(normalizedPath);
        }
        return SessionManager.create(normalizedPath, cwd.toString());
    }

    private SessionManager resolvePickedSession(PiCliArgs args) throws IOException {
        var sessionDirectory = resolveSessionDirectory(args.sessionDirectory());
        var sessions = SessionManager.list(sessionDirectory);
        if (sessions.isEmpty()) {
            throw new IllegalStateException("No sessions available to resume");
        }

        var selectedPath = sessionPicker.pick(sessions);
        if (selectedPath == null) {
            throw new CancellationException("Session selection cancelled");
        }
        return SessionManager.open(selectedPath);
    }

    private Path resolveSessionDirectory(Path sessionDirectory) {
        if (sessionDirectory == null) {
            return SessionManager.defaultSessionDirectory(cwd.toString());
        }
        return sessionDirectory.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    public interface SessionPicker {
        Path pick(List<SessionInfo> sessions);
    }
}
