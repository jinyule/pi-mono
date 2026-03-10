package dev.pi.cli;

import dev.pi.session.SessionManager;
import dev.pi.session.SessionInfo;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public final class PiCliSessionResolver {
    private final Path cwd;
    private final SessionPicker sessionPicker;
    private final Path allSessionsRoot;

    public PiCliSessionResolver(Path cwd) {
        this(cwd, new PiSessionPicker());
    }

    public PiCliSessionResolver(Path cwd, SessionPicker sessionPicker) {
        this(cwd, sessionPicker, Path.of(System.getProperty("user.home"), ".pi", "agent", "sessions"));
    }

    PiCliSessionResolver(Path cwd, SessionPicker sessionPicker, Path allSessionsRoot) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.sessionPicker = Objects.requireNonNull(sessionPicker, "sessionPicker");
        this.allSessionsRoot = Objects.requireNonNull(allSessionsRoot, "allSessionsRoot").toAbsolutePath().normalize();
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
        var sessions = args.sessionDirectory() == null
            ? listAllSessions()
            : SessionManager.list(resolveSessionDirectory(args.sessionDirectory()));
        if (sessions.isEmpty()) {
            throw new IllegalStateException("No sessions available to resume");
        }

        var selectedPath = sessionPicker.pick(sessions);
        if (selectedPath == null) {
            throw new CancellationException("Session selection cancelled");
        }
        return SessionManager.open(selectedPath);
    }

    private List<SessionInfo> listAllSessions() throws IOException {
        if (!Files.exists(allSessionsRoot)) {
            return List.of();
        }

        var sessions = new ArrayList<SessionInfo>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(allSessionsRoot)) {
            for (var directory : directories) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                try {
                    sessions.addAll(SessionManager.list(directory));
                } catch (IOException ignored) {
                }
            }
        }
        sessions.sort(Comparator.comparing(SessionInfo::modified).reversed());
        return List.copyOf(sessions);
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
