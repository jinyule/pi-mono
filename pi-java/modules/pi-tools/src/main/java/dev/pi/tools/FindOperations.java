package dev.pi.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public interface FindOperations {
    boolean exists(Path absolutePath);

    List<String> glob(String pattern, Path searchPath, SearchOptions options) throws IOException;

    static FindOperations local() {
        return new FindOperations() {
            @Override
            public boolean exists(Path absolutePath) {
                return Files.exists(absolutePath);
            }

            @Override
            public List<String> glob(String pattern, Path searchPath, SearchOptions options) throws IOException {
                Objects.requireNonNull(pattern, "pattern");
                Objects.requireNonNull(searchPath, "searchPath");
                var appliedOptions = options == null ? SearchOptions.defaults() : options;
                var results = new ArrayList<String>();
                var gitIgnoreRules = loadRootGitignore(searchPath);

                if (Files.isRegularFile(searchPath)) {
                    var fileName = searchPath.getFileName();
                    if (fileName != null) {
                        var relative = fileName.toString();
                        if (!isIgnored(relative, false, gitIgnoreRules) && matchesPattern(pattern, relative)) {
                            results.add(relative);
                        }
                    }
                    return List.copyOf(results);
                }

                Files.walkFileTree(searchPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (dir.equals(searchPath)) {
                            return FileVisitResult.CONTINUE;
                        }
                        var relative = normalizeRelative(searchPath, dir);
                        if (isImplicitlyIgnored(dir.getFileName() == null ? "" : dir.getFileName().toString())
                            || isIgnored(relative, true, gitIgnoreRules)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (matchesPattern(pattern, relative)) {
                            results.add(relative + "/");
                            if (results.size() >= appliedOptions.limit()) {
                                return FileVisitResult.TERMINATE;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        var relative = normalizeRelative(searchPath, file);
                        if (isIgnored(relative, false, gitIgnoreRules)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (matchesPattern(pattern, relative)) {
                            results.add(relative);
                            if (results.size() >= appliedOptions.limit()) {
                                return FileVisitResult.TERMINATE;
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                return List.copyOf(results);
            }
        };
    }

    record SearchOptions(
        int limit
    ) {
        public SearchOptions {
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
        }

        public static SearchOptions defaults() {
            return new SearchOptions(1000);
        }
    }

    private static boolean isImplicitlyIgnored(String name) {
        return ".git".equals(name) || "node_modules".equals(name);
    }

    private static List<String> loadRootGitignore(Path searchPath) throws IOException {
        var root = Files.isDirectory(searchPath) ? searchPath : searchPath.getParent();
        if (root == null) {
            return List.of();
        }
        var gitignore = root.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            return List.of();
        }
        var rules = new ArrayList<String>();
        for (var line : Files.readAllLines(gitignore, StandardCharsets.UTF_8)) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            rules.add(trimmed);
        }
        return List.copyOf(rules);
    }

    private static boolean isIgnored(String relativePath, boolean directory, List<String> rules) {
        for (var rule : rules) {
            var directoryOnly = rule.endsWith("/");
            var normalizedRule = directoryOnly ? rule.substring(0, rule.length() - 1) : rule;
            if (directoryOnly && !directory) {
                continue;
            }
            if (normalizedRule.contains("/")) {
                if (matchesGlob(normalizedRule, relativePath)) {
                    return true;
                }
                if (directory && relativePath.startsWith(normalizedRule + "/")) {
                    return true;
                }
                continue;
            }
            var fileName = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
            if (matchesGlob(normalizedRule, fileName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String pattern, String relativePath) {
        if (pattern.contains("/")) {
            return matchesGlob(pattern, relativePath);
        }
        var fileName = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        return matchesGlob(pattern, fileName);
    }

    private static boolean matchesGlob(String pattern, String value) {
        return Pattern.compile(globToRegex(pattern)).matcher(value).matches();
    }

    private static String normalizeRelative(Path searchPath, Path value) {
        return searchPath.relativize(value).toString().replace('\\', '/');
    }

    private static String globToRegex(String glob) {
        var regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            var current = glob.charAt(index);
            if (current == '*') {
                var isDoubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                if (isDoubleStar) {
                    var followedBySlash = index + 2 < glob.length() && glob.charAt(index + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        index += 2;
                    } else {
                        regex.append(".*");
                        index++;
                    }
                } else {
                    regex.append("[^/]*");
                }
                continue;
            }
            if (current == '?') {
                regex.append("[^/]");
                continue;
            }
            if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                regex.append('\\');
            }
            regex.append(current);
        }
        regex.append('$');
        return regex.toString();
    }
}
