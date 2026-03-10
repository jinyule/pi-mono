package dev.pi.sdk;

public final class PiSdkModule {
    public String id() {
        return "pi-sdk";
    }

    public String description() {
        return "Embeddable facade for creating agent sessions from JVM applications.";
    }

    public PiSdk sdk() {
        return new PiSdk();
    }
}
