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
    private final ArrayList<Runnable> closeCallbacks = new ArrayList<>();
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
        var callbacksToRun = new ArrayList<Runnable>();
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
                callbacksToRun.addAll(closeCallbacks);
                closeCallbacks.clear();
            }
        }
        callbacksToRun.forEach(Runnable::run);
    }

    public void end(TResult finalResult) {
        var callbacksToRun = new ArrayList<Runnable>();
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
                callbacksToRun.addAll(closeCallbacks);
                closeCallbacks.clear();
            }
            result.complete(finalResult);
        }
        callbacksToRun.forEach(Runnable::run);
    }

    public void fail(Throwable throwable) {
        var callbacksToRun = new ArrayList<Runnable>();
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
                callbacksToRun.addAll(closeCallbacks);
                closeCallbacks.clear();
            }
            result.completeExceptionally(throwable);
        }
        callbacksToRun.forEach(Runnable::run);
    }

    public void cancel() {
        var callbacksToRun = new ArrayList<Runnable>();
        if (closed.compareAndSet(false, true)) {
            synchronized (monitor) {
                subscribers.clear();
                callbacksToRun.addAll(closeCallbacks);
                closeCallbacks.clear();
            }
            result.cancel(true);
        }
        callbacksToRun.forEach(Runnable::run);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public CompletableFuture<TResult> result() {
        return result;
    }

    public void onClose(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        var runImmediately = false;
        synchronized (monitor) {
            if (closed.get()) {
                runImmediately = true;
            } else {
                closeCallbacks.add(callback);
            }
        }
        if (runImmediately) {
            callback.run();
        }
    }

    @Override
    public void close() {
        cancel();
    }
}
