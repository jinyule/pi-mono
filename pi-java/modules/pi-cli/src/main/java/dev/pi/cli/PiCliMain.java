package dev.pi.cli;

public final class PiCliMain {
    private PiCliMain() {
    }

    public static void main(String[] args) {
        try {
            new PiCliModule().run(args).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            var root = rootCause(exception);
            var message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            System.err.println(message);
            System.exit(1);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
