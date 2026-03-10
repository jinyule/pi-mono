package dev.pi.tools;

public record LsToolOptions(
    LsOperations operations
) {
    public LsToolOptions {
        operations = operations == null ? LsOperations.local() : operations;
    }

    public static LsToolOptions defaults() {
        return new LsToolOptions(LsOperations.local());
    }
}
