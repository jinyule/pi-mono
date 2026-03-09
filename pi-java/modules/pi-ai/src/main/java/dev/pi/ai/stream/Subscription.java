package dev.pi.ai.stream;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    void unsubscribe();

    @Override
    default void close() {
        unsubscribe();
    }
}

