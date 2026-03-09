package dev.pi.ai.stream;

import java.util.Objects;
import java.util.ArrayList;
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
    private final ArrayList<TEvent> history = new ArrayList<>();
    private final CompletableFuture<TResult> result = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object monitor = new Object();

    public EventStream(Predicate<TEvent> terminalEvent, Function<TEvent, TResult> terminalResult) {
        this.terminalEvent = Objects.requireNonNull(terminalEvent, "terminalEvent");
        this.terminalResult = Objects.requireNonNull(terminalResult, "terminalResult");
    }

    public Subscription subscribe(Consumer<TEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            for (var event : history) {
                listener.accept(event);
            }
            if (!closed.get()) {
                subscribers.add(listener);
            }
        }
        return () -> {
            synchronized (monitor) {
                subscribers.remove(listener);
            }
        };
    }

    public void push(TEvent event) {
        Objects.requireNonNull(event, "event");
        synchronized (monitor) {
            if (closed.get()) {
                return;
            }

            history.add(event);
            for (var subscriber : subscribers) {
                subscriber.accept(event);
            }

            if (terminalEvent.test(event) && closed.compareAndSet(false, true)) {
                subscribers.clear();
                result.complete(terminalResult.apply(event));
            }
        }
    }

    public void end(TResult finalResult) {
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
            }
            result.complete(finalResult);
        }
    }

    public void fail(Throwable throwable) {
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
            }
            result.completeExceptionally(throwable);
        }
    }

    public void cancel() {
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
            }
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
