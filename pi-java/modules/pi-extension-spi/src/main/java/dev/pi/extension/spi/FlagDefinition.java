package dev.pi.extension.spi;

public record FlagDefinition(
    String name,
    String description,
    Type type,
    Object defaultValue
) {
    public FlagDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be a non-empty string");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must be non-null");
        }
        validateDefault(type, defaultValue);
    }

    private static void validateDefault(Type type, Object defaultValue) {
        if (defaultValue == null) {
            return;
        }
        switch (type) {
            case BOOLEAN -> {
                if (!(defaultValue instanceof Boolean)) {
                    throw new IllegalArgumentException("boolean flag defaultValue must be Boolean");
                }
            }
            case STRING -> {
                if (!(defaultValue instanceof String)) {
                    throw new IllegalArgumentException("string flag defaultValue must be String");
                }
            }
        }
    }

    public enum Type {
        BOOLEAN,
        STRING
    }
}
