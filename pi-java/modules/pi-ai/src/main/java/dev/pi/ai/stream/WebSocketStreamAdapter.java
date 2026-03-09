package dev.pi.ai.stream;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public final class WebSocketStreamAdapter
    extends EventStream<WebSocketMessageEvent, WebSocketMessageEvent.TerminalEvent>
    implements WebSocket.Listener {

    private final StringBuilder textBuffer = new StringBuilder();
    private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

    public WebSocketStreamAdapter() {
        super(
            event -> event instanceof WebSocketMessageEvent.TerminalEvent,
            event -> switch (event) {
                case WebSocketMessageEvent.Close close -> close;
                case WebSocketMessageEvent.Error error -> error;
                default -> throw new IllegalStateException("Unexpected terminal event: " + event.type());
            }
        );
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            push(new WebSocketMessageEvent.Message(textBuffer.toString()));
            textBuffer.setLength(0);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        var bytes = new byte[data.remaining()];
        data.get(bytes);
        binaryBuffer.write(bytes, 0, bytes.length);
        if (last) {
            push(new WebSocketMessageEvent.Message(binaryBuffer.toString(StandardCharsets.UTF_8)));
            binaryBuffer.reset();
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        push(new WebSocketMessageEvent.Close(statusCode, reason));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        push(new WebSocketMessageEvent.Error(error));
    }
}
