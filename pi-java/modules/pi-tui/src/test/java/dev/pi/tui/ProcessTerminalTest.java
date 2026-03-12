package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.jline.terminal.Attributes;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.Test;

class ProcessTerminalTest {
    @Test
    void startEnablesRawModeBracketedPasteAndResizeHandling() {
        var backend = new FakeBackend();
        var terminal = new ProcessTerminal(backend);
        var resizeCount = new AtomicInteger();

        terminal.start(data -> {
        }, resizeCount::incrementAndGet);
        backend.fireResize();
        terminal.stop();

        assertThat(backend.enterRawModeCalls()).isEqualTo(1);
        assertThat(backend.writes()).contains("\u001b[?2004h", "\u001b[?u", "\u001b[?2004l");
        assertThat(resizeCount.get()).isEqualTo(1);
        assertThat(backend.restoredAttributes()).isSameAs(backend.rawModeAttributes());
        assertThat(backend.closed()).isTrue();
    }

    @Test
    void kittyProtocolResponseIsInterceptedAndEnablesProtocol() {
        var backend = new FakeBackend();
        var terminal = new ProcessTerminal(backend);
        var events = new ArrayList<String>();

        terminal.start(events::add, () -> {
        });
        backend.enqueue("\u001b[?1u");
        waitUntil(() -> terminal.kittyProtocolActive());
        terminal.stop();

        assertThat(terminal.kittyProtocolActive()).isFalse();
        assertThat(events).isEmpty();
        assertThat(backend.writes()).contains("\u001b[>7u", "\u001b[<u");
    }

    @Test
    void emitsInputAndTerminalControlSequences() {
        var backend = new FakeBackend();
        var terminal = new ProcessTerminal(backend);
        var events = new ArrayList<String>();

        terminal.start(events::add, () -> {
        });
        backend.enqueue("\u001b[200~hello");
        backend.enqueue(" world\u001b[201~");
        backend.enqueue("x");
        waitUntil(() -> events.size() == 2);

        terminal.write("payload");
        terminal.moveBy(-2);
        terminal.moveBy(3);
        terminal.hideCursor();
        terminal.showCursor();
        terminal.clearLine();
        terminal.clearFromCursor();
        terminal.clearScreen();
        terminal.setTitle("Pi");
        terminal.stop();

        assertThat(events).containsExactly("\u001b[200~hello world\u001b[201~", "x");
        assertThat(backend.writes()).contains(
            "payload",
            "\u001b[2A",
            "\u001b[3B",
            "\u001b[?25l",
            "\u001b[?25h",
            "\u001b[K",
            "\u001b[J",
            "\u001b[2J\u001b[H",
            "\u001b]0;Pi\u0007"
        );
    }

    @Test
    void canRestartWithFreshBackendAfterStop() {
        var backends = new ArrayList<FakeBackend>();
        var terminal = new ProcessTerminal(() -> {
            var backend = new FakeBackend();
            backends.add(backend);
            return backend;
        });
        var firstEvents = new ArrayList<String>();

        terminal.start(firstEvents::add, () -> {
        });
        backends.getFirst().enqueue("a");
        waitUntil(() -> firstEvents.size() == 1);
        terminal.stop();

        var secondEvents = new ArrayList<String>();
        terminal.start(secondEvents::add, () -> {
        });
        backends.get(1).enqueue("b");
        waitUntil(() -> secondEvents.size() == 1);
        terminal.stop();

        assertThat(backends).hasSize(2);
        assertThat(firstEvents).containsExactly("a");
        assertThat(secondEvents).containsExactly("b");
        assertThat(backends.getFirst().closed()).isTrue();
        assertThat(backends.get(1).closed()).isTrue();
        assertThat(backends.get(1).enterRawModeCalls()).isEqualTo(1);
    }

    private static void waitUntil(Check check) {
        var deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (check.matches()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", exception);
            }
        }
        throw new AssertionError("Condition not met before timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }

    private static final class FakeBackend implements ProcessTerminal.Backend {
        private final ConcurrentLinkedQueue<String> reads = new ConcurrentLinkedQueue<>();
        private final List<String> writes = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Attributes rawModeAttributes = new Attributes();

        private Runnable resizeHandler;
        private Attributes restoredAttributes;
        private int enterRawModeCalls;
        private boolean closed;

        @Override
        public Attributes enterRawMode() {
            enterRawModeCalls += 1;
            return rawModeAttributes;
        }

        @Override
        public void restore(Attributes attributes) {
            restoredAttributes = attributes;
        }

        @Override
        public int read(char[] buffer, long timeoutMillis) throws IOException {
            var next = reads.poll();
            if (next == null) {
                try {
                    Thread.sleep(Math.min(timeoutMillis, 10));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return NonBlockingReader.READ_EXPIRED;
            }

            var length = next.length();
            next.getChars(0, length, buffer, 0);
            return length;
        }

        @Override
        public void write(String data) {
            writes.add(Objects.requireNonNull(data, "data"));
        }

        @Override
        public void flush() {
        }

        @Override
        public int columns() {
            return 120;
        }

        @Override
        public int rows() {
            return 40;
        }

        @Override
        public Registration onResize(Runnable onResize) {
            resizeHandler = onResize;
            return () -> resizeHandler = null;
        }

        @Override
        public void close() {
            closed = true;
        }

        void enqueue(String data) {
            reads.add(data);
        }

        void fireResize() {
            if (resizeHandler != null) {
                resizeHandler.run();
            }
        }

        int enterRawModeCalls() {
            return enterRawModeCalls;
        }

        List<String> writes() {
            synchronized (writes) {
                return List.copyOf(writes);
            }
        }

        Attributes rawModeAttributes() {
            return rawModeAttributes;
        }

        Attributes restoredAttributes() {
            return restoredAttributes;
        }

        boolean closed() {
            return closed;
        }
    }
}
