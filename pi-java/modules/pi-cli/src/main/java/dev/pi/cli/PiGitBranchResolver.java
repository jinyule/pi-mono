package dev.pi.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class PiGitBranchResolver {
    private static final String GIT_DIR_PREFIX = "gitdir: ";
    private static final String BRANCH_PREFIX = "ref: refs/heads/";

    private PiGitBranchResolver() {
    }

    static String resolve(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return null;
        }
        try {
            var headPath = headPath(cwd);
            if (headPath == null || !Files.isRegularFile(headPath)) {
                return null;
            }
            var head = Files.readString(headPath, StandardCharsets.UTF_8).trim();
            if (head.startsWith(BRANCH_PREFIX)) {
                return head.substring(BRANCH_PREFIX.length());
            }
            return head.isBlank() ? null : "detached";
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    static Path headPath(String cwd) throws IOException {
        if (cwd == null || cwd.isBlank()) {
            return null;
        }
        return findHeadPath(Path.of(cwd));
    }

    private static Path findHeadPath(Path start) throws IOException {
        var current = start.toAbsolutePath().normalize();
        while (current != null) {
            var gitPath = current.resolve(".git");
            if (Files.isDirectory(gitPath)) {
                var headPath = gitPath.resolve("HEAD");
                if (Files.isRegularFile(headPath)) {
                    return headPath;
                }
            } else if (Files.isRegularFile(gitPath)) {
                var content = Files.readString(gitPath, StandardCharsets.UTF_8).trim();
                if (content.startsWith(GIT_DIR_PREFIX)) {
                    var gitDir = current.resolve(content.substring(GIT_DIR_PREFIX.length()).trim()).normalize();
                    var headPath = gitDir.resolve("HEAD");
                    if (Files.isRegularFile(headPath)) {
                        return headPath;
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }
}
