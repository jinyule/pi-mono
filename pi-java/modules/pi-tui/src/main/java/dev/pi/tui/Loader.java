package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public class Loader implements Component, AutoCloseable {
    private static final List<String> FRAMES = List.of("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏");

    private final Tui ui;
    private final UnaryOperator<String> spinnerColor;
    private final UnaryOperator<String> messageColor;
    private final long frameIntervalMillis;
    private final Text text = new Text("", 1, 0, null);

    private int currentFrame;
    private String message;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    public Loader(Tui ui, UnaryOperator<String> spinnerColor, UnaryOperator<String> messageColor) {
        this(ui, spinnerColor, messageColor, "Loading...");
    }

    public Loader(Tui ui, UnaryOperator<String> spinnerColor, UnaryOperator<String> messageColor, String message) {
        this(ui, spinnerColor, messageColor, message, 80, true);
    }

    Loader(
        Tui ui,
        UnaryOperator<String> spinnerColor,
        UnaryOperator<String> messageColor,
        String message,
        long frameIntervalMillis,
        boolean autoStart
    ) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.spinnerColor = spinnerColor == null ? UnaryOperator.identity() : spinnerColor;
        this.messageColor = messageColor == null ? UnaryOperator.identity() : messageColor;
        this.message = message == null ? "Loading..." : message;
        this.frameIntervalMillis = Math.max(1, frameIntervalMillis);
        updateDisplay();
        if (autoStart) {
            start();
        }
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add("");
        lines.addAll(text.render(width));
        return List.copyOf(lines);
    }

    @Override
    public void invalidate() {
        text.invalidate();
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        updateDisplay();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "pi-loader");
            thread.setDaemon(true);
            return thread;
        });
        task = executor.scheduleAtFixedRate(this::advanceFrame, frameIntervalMillis, frameIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized void setMessage(String message) {
        this.message = message == null ? "" : message;
        updateDisplay();
    }

    synchronized void advanceFrame() {
        currentFrame = (currentFrame + 1) % FRAMES.size();
        updateDisplay();
    }

    @Override
    public void close() {
        stop();
    }

    private void updateDisplay() {
        var frame = FRAMES.get(currentFrame);
        text.setText(spinnerColor.apply(frame) + " " + messageColor.apply(message));
        ui.requestRender();
    }
}
