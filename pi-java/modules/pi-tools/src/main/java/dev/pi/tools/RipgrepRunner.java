package dev.pi.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public interface RipgrepRunner {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    SearchResult search(SearchRequest request, BooleanSupplier cancelled) throws IOException, InterruptedException;

    static RipgrepRunner local() {
        return (request, cancelled) -> {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(cancelled, "cancelled");

            var args = new ArrayList<String>();
            args.add("--json");
            args.add("--line-number");
            args.add("--color=never");
            args.add("--hidden");
            if (request.ignoreCase()) {
                args.add("--ignore-case");
            }
            if (request.literal()) {
                args.add("--fixed-strings");
            }
            if (request.glob() != null && !request.glob().isBlank()) {
                args.add("--glob");
                args.add(request.glob());
            }
            args.add(request.pattern());
            args.add(request.searchPath().toString());

            Process process;
            try {
                process = new ProcessBuilder("rg")
                    .command(buildCommand(args))
                    .start();
            } catch (IOException exception) {
                throw new IOException("ripgrep (rg) is not available", exception);
            }

            var stderr = new StringBuilder();
            var stderrThread = Thread.ofVirtual().name("pi-tools-grep-stderr").start(() -> {
                try (var reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                    var buffer = new char[2048];
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        if (read > 0) {
                            stderr.append(buffer, 0, read);
                        }
                    }
                } catch (IOException ignored) {
                }
            });

            var matches = new ArrayList<Match>();
            var limitReached = new AtomicBoolean(false);

            try (var reader = new java.io.BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.getAsBoolean()) {
                        process.destroyForcibly();
                        throw new IOException("Operation aborted");
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        var event = OBJECT_MAPPER.readTree(line);
                        if (!"match".equals(event.path("type").asText())) {
                            continue;
                        }
                        var filePath = event.path("data").path("path").path("text").asText(null);
                        var lineNumber = event.path("data").path("line_number").asInt(-1);
                        if (filePath != null && lineNumber > 0) {
                            matches.add(new Match(Path.of(filePath), lineNumber));
                            if (matches.size() >= request.limit()) {
                                limitReached.set(true);
                                process.destroy();
                                break;
                            }
                        }
                    } catch (RuntimeException parseFailure) {
                        // Ignore malformed JSON lines from rg output.
                    }
                }
            }
            int exitCode;
            try {
                exitCode = process.waitFor();
                stderrThread.join();
            } catch (InterruptedException interruptedException) {
                process.destroyForcibly();
                stderrThread.join();
                Thread.currentThread().interrupt();
                throw interruptedException;
            }
            if (!limitReached.get() && exitCode != 0 && exitCode != 1) {
                var error = stderr.toString().trim();
                throw new IOException(error.isEmpty() ? "ripgrep exited with code " + exitCode : error);
            }
            return new SearchResult(List.copyOf(matches), limitReached.get());
        };
    }

    private static List<String> buildCommand(List<String> args) {
        var command = new ArrayList<String>(args.size() + 1);
        command.add("rg");
        command.addAll(args);
        return command;
    }

    record SearchRequest(
        String pattern,
        Path searchPath,
        String glob,
        boolean ignoreCase,
        boolean literal,
        int limit
    ) {
        public SearchRequest {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(searchPath, "searchPath");
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
        }
    }

    record Match(
        Path filePath,
        int lineNumber
    ) {
        public Match {
            Objects.requireNonNull(filePath, "filePath");
            if (lineNumber <= 0) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
        }
    }

    record SearchResult(
        List<Match> matches,
        boolean matchLimitReached
    ) {
        public SearchResult {
            matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
        }
    }
}
