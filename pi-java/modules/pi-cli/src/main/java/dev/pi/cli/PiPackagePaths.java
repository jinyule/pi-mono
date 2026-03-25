package dev.pi.cli;

import dev.pi.session.PiAgentPaths;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

final class PiPackagePaths {
    static final String ENV_PACKAGE_DIR = "PI_PACKAGE_DIR";
    private static final String README_FILE = "README.md";
    private static final String CHANGELOG_FILE = "CHANGELOG.md";
    private static final String DOCS_DIR = "docs";
    private static final String EXAMPLES_DIR = "examples";

    private PiPackagePaths() {
    }

    static Path readmePath(String cwd) {
        return readmePath(System.getenv(), codeSourceBase(PiPackagePaths.class), workingDirectory(cwd));
    }

    static Path readmePath(Map<String, String> environment, Path codeSourceBase, Path cwd) {
        var packageRoot = packageRoot(environment, codeSourceBase, cwd);
        if (packageRoot == null) {
            return null;
        }
        var readme = packageRoot.resolve(README_FILE);
        return Files.isRegularFile(readme) ? readme.toAbsolutePath().normalize() : null;
    }

    static Path docsPath(String cwd) {
        return docsPath(System.getenv(), codeSourceBase(PiPackagePaths.class), workingDirectory(cwd));
    }

    static Path docsPath(Map<String, String> environment, Path codeSourceBase, Path cwd) {
        var packageRoot = packageRoot(environment, codeSourceBase, cwd);
        if (packageRoot == null) {
            return null;
        }
        var docs = packageRoot.resolve(DOCS_DIR);
        return Files.isDirectory(docs) ? docs.toAbsolutePath().normalize() : null;
    }

    static Path examplesPath(String cwd) {
        return examplesPath(System.getenv(), codeSourceBase(PiPackagePaths.class), workingDirectory(cwd));
    }

    static Path examplesPath(Map<String, String> environment, Path codeSourceBase, Path cwd) {
        var packageRoot = packageRoot(environment, codeSourceBase, cwd);
        if (packageRoot == null) {
            return null;
        }
        var examples = packageRoot.resolve(EXAMPLES_DIR);
        return Files.isDirectory(examples) ? examples.toAbsolutePath().normalize() : null;
    }

    static Path changelogPath(String cwd) {
        return changelogPath(System.getenv(), codeSourceBase(PiPackagePaths.class), workingDirectory(cwd));
    }

    static Path changelogPath(Map<String, String> environment, Path codeSourceBase, Path cwd) {
        Objects.requireNonNull(environment, "environment");
        var candidates = new LinkedHashSet<Path>();

        var configured = environment.get(ENV_PACKAGE_DIR);
        if (configured != null && !configured.isBlank()) {
            candidates.add(PiAgentPaths.expandPath(configured, Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()));
        }
        if (cwd != null) {
            candidates.add(cwd.toAbsolutePath().normalize());
        }
        if (codeSourceBase != null) {
            candidates.add(codeSourceBase.toAbsolutePath().normalize());
        }

        for (var candidate : candidates) {
            var resolved = searchForChangelog(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static Path packageRoot(Map<String, String> environment, Path codeSourceBase, Path cwd) {
        Objects.requireNonNull(environment, "environment");
        var candidates = new LinkedHashSet<Path>();

        var configured = environment.get(ENV_PACKAGE_DIR);
        if (configured != null && !configured.isBlank()) {
            candidates.add(PiAgentPaths.expandPath(configured, Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()));
        }
        if (cwd != null) {
            candidates.add(cwd.toAbsolutePath().normalize());
        }
        if (codeSourceBase != null) {
            candidates.add(codeSourceBase.toAbsolutePath().normalize());
        }

        for (var candidate : candidates) {
            var resolved = searchForPackageRoot(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static Path searchForPackageRoot(Path start) {
        var current = start;
        while (current != null) {
            if (isPackagedRoot(current)) {
                return current.toAbsolutePath().normalize();
            }
            var nestedPackage = current.resolve("packages").resolve("coding-agent");
            if (isPackagedRoot(nestedPackage)) {
                return nestedPackage.toAbsolutePath().normalize();
            }
            current = current.getParent();
        }
        return null;
    }

    private static Path searchForChangelog(Path start) {
        var current = start;
        while (current != null) {
            var packageChangelog = current.resolve("packages").resolve("coding-agent").resolve(CHANGELOG_FILE);
            if (Files.isRegularFile(packageChangelog)) {
                return packageChangelog.toAbsolutePath().normalize();
            }
            var directChangelog = current.resolve(CHANGELOG_FILE);
            if (Files.isRegularFile(directChangelog)) {
                return directChangelog.toAbsolutePath().normalize();
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isPackagedRoot(Path root) {
        return root != null
            && Files.isRegularFile(root.resolve(README_FILE))
            && Files.isDirectory(root.resolve(DOCS_DIR))
            && Files.isDirectory(root.resolve(EXAMPLES_DIR));
    }

    private static Path codeSourceBase(Class<?> anchor) {
        try {
            CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            var path = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (IllegalArgumentException | URISyntaxException | SecurityException exception) {
            return null;
        }
    }

    private static Path workingDirectory(String cwd) {
        try {
            return cwd == null || cwd.isBlank()
                ? Path.of("").toAbsolutePath().normalize()
                : Path.of(cwd).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return Path.of("").toAbsolutePath().normalize();
        }
    }
}
