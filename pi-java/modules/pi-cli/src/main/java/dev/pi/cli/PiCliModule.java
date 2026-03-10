package dev.pi.cli;

public final class PiCliModule {
    public String id() {
        return "pi-cli";
    }

    public String description() {
        return "CLI entrypoint and runtime orchestration across interactive and non-interactive modes.";
    }

    public PiCliParser parser() {
        return new PiCliParser();
    }
}
