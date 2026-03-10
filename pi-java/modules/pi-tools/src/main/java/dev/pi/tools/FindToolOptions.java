package dev.pi.tools;

public record FindToolOptions(
    FindOperations operations
) {
    public FindToolOptions {
        operations = operations == null ? FindOperations.local() : operations;
    }

    public static FindToolOptions defaults() {
        return new FindToolOptions(FindOperations.local());
    }
}
