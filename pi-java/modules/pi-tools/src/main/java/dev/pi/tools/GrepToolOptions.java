package dev.pi.tools;

public record GrepToolOptions(
    GrepOperations operations,
    RipgrepRunner ripgrepRunner
) {
    public GrepToolOptions {
        operations = operations == null ? GrepOperations.local() : operations;
        ripgrepRunner = ripgrepRunner == null ? RipgrepRunner.local() : ripgrepRunner;
    }

    public static GrepToolOptions defaults() {
        return new GrepToolOptions(GrepOperations.local(), RipgrepRunner.local());
    }
}
