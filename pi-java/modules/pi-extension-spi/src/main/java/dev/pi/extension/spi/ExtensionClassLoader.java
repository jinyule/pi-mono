package dev.pi.extension.spi;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;

public final class ExtensionClassLoader extends URLClassLoader {
    private volatile boolean closed;

    public ExtensionClassLoader(Path source, ClassLoader parent) throws IOException {
        this(new URL[] { Objects.requireNonNull(source, "source").toUri().toURL() }, parent);
    }

    ExtensionClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        super.close();
    }
}
