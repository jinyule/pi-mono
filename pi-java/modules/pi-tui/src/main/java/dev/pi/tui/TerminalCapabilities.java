package dev.pi.tui;

public record TerminalCapabilities(
    ImageProtocol images,
    boolean trueColor,
    boolean hyperlinks
) {
}
