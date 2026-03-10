package dev.pi.extension.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ExtensionEventBus {
    private final List<LoadedExtension> extensions;

    public ExtensionEventBus(List<LoadedExtension> extensions) {
        this.extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
    }

    public boolean hasHandlers(Class<? extends ExtensionEvent> eventType) {
        Objects.requireNonNull(eventType, "eventType");
        for (var extension : extensions) {
            var handlers = extension.eventHandlers().get(eventType);
            if (handlers != null && !handlers.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public <E extends ExtensionEvent, R> CompletionStage<ExtensionEventDispatchResult<R>> emit(E event, ExtensionContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");

        var results = new ArrayList<R>();
        var failures = new ArrayList<ExtensionEventFailure>();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);

        for (var extension : extensions) {
            var handlers = extension.eventHandlers().getOrDefault(event.getClass(), List.of());
            for (var handler : handlers) {
                chain = chain.thenCompose(ignored -> invoke(extension, handler, event, context, results, failures));
            }
        }

        return chain.thenApply(ignored -> new ExtensionEventDispatchResult<>(results, failures));
    }

    @SuppressWarnings("unchecked")
    private static <E extends ExtensionEvent, R> CompletionStage<Void> invoke(
        LoadedExtension extension,
        ExtensionHandler<?, ?> handler,
        E event,
        ExtensionContext context,
        List<R> results,
        List<ExtensionEventFailure> failures
    ) {
        try {
            var typedHandler = (ExtensionHandler<E, R>) handler;
            return typedHandler.handle(event, context)
                .handle((result, error) -> {
                    if (error != null) {
                        failures.add(new ExtensionEventFailure(
                            extension.id(),
                            extension.source(),
                            event.type(),
                            unwrap(error)
                        ));
                    } else if (result != null) {
                        results.add(result);
                    }
                    return null;
                });
        } catch (RuntimeException exception) {
            failures.add(new ExtensionEventFailure(extension.id(), extension.source(), event.type(), exception));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException || throwable instanceof java.util.concurrent.ExecutionException) {
            return throwable.getCause() == null ? throwable : throwable.getCause();
        }
        return throwable;
    }
}
