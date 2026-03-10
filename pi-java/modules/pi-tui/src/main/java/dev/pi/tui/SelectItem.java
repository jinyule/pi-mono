package dev.pi.tui;

public record SelectItem(
    String value,
    String label,
    String description
) {
    public SelectItem {
        value = value == null ? "" : value;
        label = label == null ? "" : label;
        description = description == null ? null : description;
    }

    public String displayValue() {
        return label.isBlank() ? value : label;
    }
}
