package dev.pi.ai.stream;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EventStream<TEvent, TResult> implements AutoCloseable {
    private final Predicate<TEvent> terminalEvent;
    private final Function<TEvent, TResult> terminalResult;
    private final CopyOnWriteArrayList<Consumer<TEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final CompletableFuture<TResult> result = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public EventStream(Predicate<TEvent> terminalEvent, Function<TEvent, TResult> terminalResult) {
        this.terminalEvent = Objects.requireNonNull(terminalEvent, "terminalEvent");
        this.terminalResult = Objects.requireNonNull(terminalResult, "terminalResult");
    }

    public Subscription subscribe(Consumer<TEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    public void push(TEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed.get()) {
            return;
        }

        for (var subscriber : subscribers) {
            subscriber.accept(event);
        }

        if (terminalEvent.test(event) && closed.compareAndSet(false, true)) {
            result.complete(terminalResult.apply(event));
        }
    }

    public void end(TResult finalResult) {
        if (closed.compareAndSet(false, true)) {
            result.complete(finalResult);
        }
    }

    public void fail(Throwable throwable) {
        if (closed.compareAndSet(false, true)) {
            result.completeExceptionally(throwable);
        }
    }

    public void cancel() {
        if (closed.compareAndSet(false, true)) {
            result.cancel(true);
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public CompletableFuture<TResult> result() {
        return result;
    }

    @Override
    public void close() {
        cancel();
    }
}

