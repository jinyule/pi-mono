package dev.pi.cli;

import dev.pi.session.SessionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PiCliSessionResolver {
    private final Path cwd;

    public PiCliSessionResolver(Path cwd) {
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
    }

    public SessionManager resolve(PiCliArgs args) throws IOException {
        Objects.requireNonNull(args, "args");

        if (args.noSession()) {
            return SessionManager.inMemory(cwd.toString());
        }

        if (args.resumeRequested()) {
            throw new UnsupportedOperationException("`--resume` session picker is not implemented yet");
        }

        if (args.sessionPath() != null) {
            return resolveExplicitSessionPath(args.sessionPath());
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

    private Path resolveSessionDirectory(Path sessionDirectory) {
        return sessionDirectory.toAbsolutePath().normalize();
    }
}
