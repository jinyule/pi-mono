package dev.pi.tools;

public record WriteToolOptions(
    WriteOperations operations
) {
    public WriteToolOptions {
        operations = operations == null ? WriteOperations.local() : operations;
    }

    public static WriteToolOptions defaults() {
        return new WriteToolOptions(WriteOperations.local());
    }
}
