package dev.pi.tui;

public interface Overlay {
    Component component();

    OverlayOptions options();

    boolean isHidden();

    void setHidden(boolean hidden);
}
