package dev.pi.cli;

public enum PiCliMode {
    INTERACTIVE("interactive"),
    PRINT("print"),
    JSON("json"),
    RPC("rpc");

    private final String value;

    PiCliMode(String value) {
        this.value = value;
    }

    public static PiCliMode fromValue(String value) {
        return switch (value) {
            case "interactive" -> INTERACTIVE;
            case "print", "text" -> PRINT;
            case "json" -> JSON;
            case "rpc" -> RPC;
            default -> throw new IllegalArgumentException("Unknown CLI mode: " + value);
        };
    }

    public String value() {
        return value;
    }
}
