package dev.pi.tui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.jline.terminal.Attributes;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

public final class ProcessTerminal implements Terminal {
    private static final String ENABLE_BRACKETED_PASTE = "\u001b[?2004h";
    private static final String DISABLE_BRACKETED_PASTE = "\u001b[?2004l";
    private static final String QUERY_KITTY_KEYBOARD_PROTOCOL = "\u001b[?u";
    private static final String ENABLE_KITTY_KEYBOARD_PROTOCOL = "\u001b[>7u";
    private static final String DISABLE_KITTY_KEYBOARD_PROTOCOL = "\u001b[<u";
    private static final Pattern KITTY_PROTOCOL_RESPONSE = Pattern.compile("^\u001b\\[\\?(\\d+)u$");

    private final BackendFactory backendFactory;
    private final TerminalInputBuffer inputBuffer = new TerminalInputBuffer();
    private final Object lifecycleMonitor = new Object();

    private Backend backend;
    private volatile InputHandler inputHandler;
    private volatile boolean kittyProtocolActive;

    private Attributes originalAttributes;
    private Backend.Registration resizeRegistration;
    private Thread inputThread;
    private AtomicBoolean running = new AtomicBoolean(false);

    public ProcessTerminal() {
        this(JlineBackend::new);
    }

    ProcessTerminal(Backend backend) {
        this(() -> Objects.requireNonNull(backend, "backend"));
    }

    ProcessTerminal(BackendFactory backendFactory) {
        this.backendFactory = Objects.requireNonNull(backendFactory, "backendFactory");
    }

    @Override
    public void start(InputHandler onInput, Runnable onResize) {
        Objects.requireNonNull(onInput, "onInput");
        Objects.requireNonNull(onResize, "onResize");

        synchronized (lifecycleMonitor) {
            if (running.get()) {
                return;
            }

            backend = backendFactory.create();
            inputBuffer.clear();
            kittyProtocolActive = false;
            inputHandler = onInput;
            originalAttributes = backend.enterRawMode();
            resizeRegistration = backend.onResize(onResize);
            backend.write(ENABLE_BRACKETED_PASTE);
            backend.write(QUERY_KITTY_KEYBOARD_PROTOCOL);
            backend.flush();

            running = new AtomicBoolean(true);
            inputThread = Thread.ofVirtual().name("pi-tui-input").start(this::readLoop);
        }
    }

    @Override
    public void stop() {
        Thread threadToJoin;
        Backend.Registration registrationToClose;
        Attributes attributesToRestore;
        Backend backendToClose;

        synchronized (lifecycleMonitor) {
            if (!running.get()) {
                return;
            }

            running.set(false);
            threadToJoin = inputThread;
            registrationToClose = resizeRegistration;
            attributesToRestore = originalAttributes;
            backendToClose = backend;

            inputThread = null;
            resizeRegistration = null;
            originalAttributes = null;
            inputHandler = null;
            backend = null;
        }

        if (threadToJoin != null) {
            threadToJoin.interrupt();
            joinQuietly(threadToJoin);
        }

        inputBuffer.clear();

        if (kittyProtocolActive) {
            backendToClose.write(DISABLE_KITTY_KEYBOARD_PROTOCOL);
            kittyProtocolActive = false;
        }
        backendToClose.write(DISABLE_BRACKETED_PASTE);
        backendToClose.flush();

        if (registrationToClose != null) {
            registrationToClose.close();
        }
        if (attributesToRestore != null) {
            backendToClose.restore(attributesToRestore);
        }

        backendToClose.close();
    }

    @Override
    public void write(String data) {
        var currentBackend = backend;
        if (currentBackend == null) {
            return;
        }
        currentBackend.write(data);
        currentBackend.flush();
    }

    @Override
    public int columns() {
        return requireBackend().columns();
    }

    @Override
    public int rows() {
        return requireBackend().rows();
    }

    @Override
    public boolean kittyProtocolActive() {
        return kittyProtocolActive;
    }

    @Override
    public void moveBy(int lines) {
        if (lines > 0) {
            write("\u001b[" + lines + "B");
        } else if (lines < 0) {
            write("\u001b[" + (-lines) + "A");
        }
    }

    @Override
    public void hideCursor() {
        write("\u001b[?25l");
    }

    @Override
    public void showCursor() {
        write("\u001b[?25h");
    }

    @Override
    public void clearLine() {
        write("\u001b[K");
    }

    @Override
    public void clearFromCursor() {
        write("\u001b[J");
    }

    @Override
    public void clearScreen() {
        write("\u001b[2J\u001b[H");
    }

    @Override
    public void setTitle(String title) {
        write("\u001b]0;" + Objects.requireNonNull(title, "title") + "\u0007");
    }

    private void readLoop() {
        var activeBackend = requireBackend();
        var readBuffer = new char[1024];

        try {
            while (running.get()) {
                var read = activeBackend.read(readBuffer, 50);
                if (read == NonBlockingReader.READ_EXPIRED) {
                    dispatch(inputBuffer.flush());
                    continue;
                }
                if (read == NonBlockingReader.EOF) {
                    dispatch(inputBuffer.flush());
                    return;
                }
                if (read <= 0) {
                    continue;
                }

                dispatch(inputBuffer.feed(new String(readBuffer, 0, read)));
            }
        } catch (IOException ignored) {
        } finally {
            dispatch(inputBuffer.flush());
        }
    }

    private void dispatch(Iterable<String> sequences) {
        for (var sequence : sequences) {
            if (!running.get()) {
                return;
            }
            if (handleKittyProtocolResponse(sequence)) {
                continue;
            }
            var handler = inputHandler;
            if (handler != null) {
                handler.onInput(sequence);
            }
        }
    }

    private boolean handleKittyProtocolResponse(String sequence) {
        if (kittyProtocolActive) {
            return false;
        }
        if (!KITTY_PROTOCOL_RESPONSE.matcher(sequence).matches()) {
            return false;
        }

        kittyProtocolActive = true;
        var currentBackend = backend;
        if (currentBackend != null) {
            currentBackend.write(ENABLE_KITTY_KEYBOARD_PROTOCOL);
            currentBackend.flush();
        }
        return true;
    }

    private Backend requireBackend() {
        var currentBackend = backend;
        if (currentBackend == null) {
            throw new IllegalStateException("Terminal backend is not active");
        }
        return currentBackend;
    }

    private static void joinQuietly(Thread thread) {
        var interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    interface Backend {
        Attributes enterRawMode();

        void restore(Attributes attributes);

        int read(char[] buffer, long timeoutMillis) throws IOException;

        void write(String data);

        void flush();

        int columns();

        int rows();

        Registration onResize(Runnable onResize);

        void close();

        interface Registration {
            void close();
        }
    }

    @FunctionalInterface
    interface BackendFactory {
        Backend create();
    }

    private static final class JlineBackend implements Backend {
        private final org.jline.terminal.Terminal terminal;

        private JlineBackend() {
            try {
                this.terminal = TerminalBuilder.builder().system(true).build();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public Attributes enterRawMode() {
            return terminal.enterRawMode();
        }

        @Override
        public void restore(Attributes attributes) {
            terminal.setAttributes(attributes);
        }

        @Override
        public int read(char[] buffer, long timeoutMillis) throws IOException {
            return terminal.reader().readBuffered(buffer, 0, buffer.length, timeoutMillis);
        }

        @Override
        public void write(String data) {
            terminal.writer().print(data);
        }

        @Override
        public void flush() {
            terminal.writer().flush();
        }

        @Override
        public int columns() {
            return terminal.getWidth();
        }

        @Override
        public int rows() {
            return terminal.getHeight();
        }

        @Override
        public Registration onResize(Runnable onResize) {
            var previous = terminal.handle(org.jline.terminal.Terminal.Signal.WINCH, signal -> onResize.run());
            return () -> terminal.handle(org.jline.terminal.Terminal.Signal.WINCH, previous);
        }

        @Override
        public void close() {
            try {
                terminal.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
