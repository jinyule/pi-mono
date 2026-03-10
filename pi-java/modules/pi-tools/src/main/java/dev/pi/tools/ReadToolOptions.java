package dev.pi.tools;

public record ReadToolOptions(
    boolean autoResizeImages,
    ReadOperations operations
) {
    public ReadToolOptions {
        operations = operations == null ? ReadOperations.local() : operations;
    }

    public static ReadToolOptions defaults() {
        return new ReadToolOptions(true, ReadOperations.local());
    }
}
