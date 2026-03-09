package dev.pi.ai.auth;

public sealed interface Credential permits ApiKeyCredential, BearerTokenCredential {
    String secret();
}
