package dev.pi.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class WebSocketStreamAdapterTest {
    @Test
    void emitsMergedTextAndBinaryMessagesThenCompletesOnClose() {
        var adapter = new WebSocketStreamAdapter();
        var socket = new StubWebSocket();
        var events = new ArrayList<WebSocketMessageEvent>();

        try (var ignored = adapter.subscribe(events::add)) {
            adapter.onOpen(socket);
            adapter.onText(socket, "Hel", false).toCompletableFuture().join();
            adapter.onText(socket, "lo", true).toCompletableFuture().join();
            adapter.onBinary(socket, ByteBuffer.wrap("Wor".getBytes()), false).toCompletableFuture().join();
            adapter.onBinary(socket, ByteBuffer.wrap("ld".getBytes()), true).toCompletableFuture().join();
            adapter.onClose(socket, 1000, "done").toCompletableFuture().join();
        }

        assertThat(socket.requests()).isEqualTo(5);
        assertThat(events).containsExactly(
            new WebSocketMessageEvent.Message("Hello"),
            new WebSocketMessageEvent.Message("World"),
            new WebSocketMessageEvent.Close(1000, "done")
        );
        assertThat(adapter.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .isEqualTo(new WebSocketMessageEvent.Close(1000, "done"));
    }

    @Test
    void completesWithErrorEventOnListenerFailure() {
        var adapter = new WebSocketStreamAdapter();
        var socket = new StubWebSocket();
        var failure = new IllegalStateException("boom");

        adapter.onError(socket, failure);

        assertThat(adapter.result())
            .succeedsWithin(Duration.ofSeconds(1))
            .isEqualTo(new WebSocketMessageEvent.Error(failure));
    }

    private static final class StubWebSocket implements WebSocket {
        private long requests;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            requests += n;
        }

        @Override
        public String getSubprotocol() {
            return null;
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
        }

        long requests() {
            return requests;
        }
    }
}
