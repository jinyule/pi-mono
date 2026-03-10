package dev.pi.tools;

public record EditToolOptions(
    EditOperations operations
) {
    public EditToolOptions {
        operations = operations == null ? EditOperations.local() : operations;
    }

    public static EditToolOptions defaults() {
        return new EditToolOptions(EditOperations.local());
    }
}
