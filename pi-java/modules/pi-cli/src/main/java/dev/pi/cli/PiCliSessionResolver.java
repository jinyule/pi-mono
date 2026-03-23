package dev.pi.cli;

import dev.pi.session.PiAgentPaths;
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
        this(cwd, sessionPicker, PiAgentPaths.sessionsDir());
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
        var currentDirectory = args.sessionDirectory() == null
            ? resolveSessionDirectory(null)
            : resolveSessionDirectory(args.sessionDirectory());
        PiSessionPicker.SessionLoader currentLoader = progress -> loadCurrentSessions(currentDirectory, progress);
        PiSessionPicker.SessionLoader allLoader = args.sessionDirectory() == null
            ? this::listAllSessions
            : progress -> loadCurrentSessions(currentDirectory, progress);

        var selectedPath = sessionPicker.pick(currentLoader, allLoader);
        if (selectedPath == null) {
            throw new CancellationException("Session selection cancelled");
        }
        return SessionManager.open(selectedPath);
    }

    private List<SessionInfo> loadCurrentSessions(Path directory, PiSessionPicker.SessionLoadProgress progress) throws IOException {
        var sessions = SessionManager.list(directory);
        if (progress != null) {
            progress.onProgress(1, 1);
        }
        return sessions;
    }

    private List<SessionInfo> listAllSessions(PiSessionPicker.SessionLoadProgress progress) throws IOException {
        if (!Files.exists(allSessionsRoot)) {
            if (progress != null) {
                progress.onProgress(0, 0);
            }
            return List.of();
        }

        var directories = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(allSessionsRoot)) {
            for (var path : stream) {
                if (Files.isDirectory(path)) {
                    directories.add(path);
                }
            }
        }

        var sessions = new ArrayList<SessionInfo>();
        for (var index = 0; index < directories.size(); index += 1) {
            var directory = directories.get(index);
            try {
                sessions.addAll(SessionManager.list(directory));
            } catch (IOException ignored) {
            }
            if (progress != null) {
                progress.onProgress(index + 1, directories.size());
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
        Path pick(PiSessionPicker.SessionLoader currentLoader, PiSessionPicker.SessionLoader allLoader) throws IOException;
    }
}
