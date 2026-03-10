package dev.pi.agent.runtime;

import dev.pi.ai.model.CacheRetention;
import dev.pi.ai.model.Message;
import dev.pi.ai.model.Model;
import dev.pi.ai.model.TextContent;
import dev.pi.ai.model.ThinkingBudgets;
import dev.pi.ai.model.ThinkingLevel;
import dev.pi.ai.model.Transport;
import dev.pi.ai.stream.Subscription;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Agent {
    public enum QueueMode {
        ONE_AT_A_TIME,
        ALL,
    }

    private final Object monitor = new Object();
    private final CopyOnWriteArrayList<Consumer<AgentEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<AgentState>> stateListeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<AgentMessage> steeringQueue = new ArrayDeque<>();
    private final ArrayDeque<AgentMessage> followUpQueue = new ArrayDeque<>();
    private final AgentLoopConfig.MessageConverter convertToLlm;
    private final AgentLoopConfig.ContextTransformer transformContext;
    private final AgentLoopConfig.AssistantStreamFunction streamFunction;
    private final AgentLoopConfig.ApiKeyProvider apiKeyProvider;
    private final String apiKey;
    private final Transport transport;
    private final CacheRetention cacheRetention;
    private final String sessionId;
    private final Map<String, String> headers;
    private final Long maxRetryDelayMs;
    private final ThinkingBudgets thinkingBudgets;

    private volatile AgentState state;
    private volatile QueueMode steeringMode;
    private volatile QueueMode followUpMode;
    private volatile CompletableFuture<Void> runningPrompt;
    private volatile AgentEventStream activeStream;
    private volatile Subscription activeSubscription;

    private Agent(Builder builder) {
        this.state = new AgentState(
            builder.systemPrompt,
            builder.model,
            builder.thinkingLevel,
            builder.tools,
            builder.messages,
            false,
            null,
            null,
            null
        );
        this.convertToLlm = builder.convertToLlm == null ? Agent::defaultConvertToLlm : builder.convertToLlm;
        this.transformContext = builder.transformContext;
        this.streamFunction = builder.streamFunction;
        this.apiKeyProvider = builder.apiKeyProvider;
        this.apiKey = builder.apiKey;
        this.transport = builder.transport;
        this.cacheRetention = builder.cacheRetention;
        this.sessionId = builder.sessionId;
        this.headers = Map.copyOf(builder.headers);
        this.maxRetryDelayMs = builder.maxRetryDelayMs;
        this.thinkingBudgets = builder.thinkingBudgets;
        this.steeringMode = builder.steeringMode;
        this.followUpMode = builder.followUpMode;
    }

    public static Builder builder(Model model) {
        return new Builder(model);
    }

    public AgentState state() {
        return state;
    }

    public Subscription subscribe(Consumer<AgentEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        eventListeners.add(listener);
        return () -> eventListeners.remove(listener);
    }

    public Subscription subscribeState(Consumer<AgentState> listener) {
        Objects.requireNonNull(listener, "listener");
        listener.accept(state);
        stateListeners.add(listener);
        return () -> stateListeners.remove(listener);
    }

    public void setSystemPrompt(String systemPrompt) {
        emitState(updateState(current -> current.withSystemPrompt(systemPrompt)));
    }

    public void setModel(Model model) {
        emitState(updateState(current -> current.withModel(model)));
    }

    public void setThinkingLevel(ThinkingLevel thinkingLevel) {
        emitState(updateState(current -> current.withThinkingLevel(thinkingLevel)));
    }

    public void setTools(List<AgentTool<?>> tools) {
        emitState(updateState(current -> current.withTools(tools)));
    }

    public void replaceMessages(List<AgentMessage> messages) {
        emitState(updateState(current -> current.withMessages(messages)));
    }

    public void appendMessage(AgentMessage message) {
        emitState(updateState(current -> current.appendMessage(message)));
    }

    public void clearMessages() {
        emitState(updateState(AgentState::clearMessages));
    }

    public void steer(AgentMessage message) {
        synchronized (monitor) {
            steeringQueue.addLast(Objects.requireNonNull(message, "message"));
        }
    }

    public void followUp(AgentMessage message) {
        synchronized (monitor) {
            followUpQueue.addLast(Objects.requireNonNull(message, "message"));
        }
    }

    public void clearSteeringQueue() {
        synchronized (monitor) {
            steeringQueue.clear();
        }
    }

    public void clearFollowUpQueue() {
        synchronized (monitor) {
            followUpQueue.clear();
        }
    }

    public void clearAllQueues() {
        synchronized (monitor) {
            steeringQueue.clear();
            followUpQueue.clear();
        }
    }

    public boolean hasQueuedMessages() {
        synchronized (monitor) {
            return !steeringQueue.isEmpty() || !followUpQueue.isEmpty();
        }
    }

    public void setSteeringMode(QueueMode steeringMode) {
        this.steeringMode = Objects.requireNonNull(steeringMode, "steeringMode");
    }

    public QueueMode steeringMode() {
        return steeringMode;
    }

    public void setFollowUpMode(QueueMode followUpMode) {
        this.followUpMode = Objects.requireNonNull(followUpMode, "followUpMode");
    }

    public QueueMode followUpMode() {
        return followUpMode;
    }

    public CompletionStage<Void> prompt(String text) {
        return prompt(new AgentMessage.UserMessage(List.of(new TextContent(text, null)), System.currentTimeMillis()));
    }

    public CompletionStage<Void> prompt(AgentMessage message) {
        return prompt(List.of(message));
    }

    public CompletionStage<Void> prompt(List<AgentMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Prompt messages must not be empty");
        }
        return startRun(List.copyOf(messages), false, false);
    }

    public CompletionStage<Void> resume() {
        var currentState = state;
        if (currentState.isStreaming()) {
            throw new IllegalStateException("Agent is already processing");
        }
        if (currentState.messages().isEmpty()) {
            throw new IllegalStateException("No messages to continue from");
        }
        var lastMessage = currentState.messages().get(currentState.messages().size() - 1);
        if (lastMessage instanceof AgentMessage.AssistantMessage) {
            var queuedSteering = dequeueSteeringMessages();
            if (!queuedSteering.isEmpty()) {
                return startRun(queuedSteering, false, true);
            }
            var queuedFollowUp = dequeueFollowUpMessages();
            if (!queuedFollowUp.isEmpty()) {
                return startRun(queuedFollowUp, false, false);
            }
            throw new IllegalStateException("Cannot continue from assistant message");
        }
        return startRun(null, true, false);
    }

    public void abort() {
        var stream = activeStream;
        if (stream != null) {
            stream.close();
        }
        emitState(updateState(current -> current.finishStreaming(current.messages())
            .clearPendingToolCalls()
            .withError("Request was aborted")));
    }

    public CompletionStage<Void> waitForIdle() {
        var prompt = runningPrompt;
        return prompt == null ? CompletableFuture.completedFuture(null) : prompt;
    }

    public void reset() {
        synchronized (monitor) {
            steeringQueue.clear();
            followUpQueue.clear();
        }
        emitState(updateState(current -> new AgentState(
            current.systemPrompt(),
            current.model(),
            current.thinkingLevel(),
            current.tools(),
            List.of(),
            false,
            null,
            null,
            null
        )));
    }

    private CompletionStage<Void> startRun(
        List<AgentMessage> promptMessages,
        boolean continueOnly,
        boolean skipInitialSteeringPoll
    ) {
        synchronized (monitor) {
            if (state.isStreaming()) {
                throw new IllegalStateException("Agent is already processing");
            }
        }

        var future = new CompletableFuture<Void>();
        runningPrompt = future;
        emitState(updateState(current -> current.startStreaming(null).clearPendingToolCalls().clearError()));

        var stateSnapshot = state;
        Thread.ofVirtual().name("agent-facade").start(() -> runLoop(
            stateSnapshot,
            promptMessages,
            continueOnly,
            skipInitialSteeringPoll,
            future
        ));
        return future;
    }

    private void runLoop(
        AgentState stateSnapshot,
        List<AgentMessage> promptMessages,
        boolean continueOnly,
        boolean skipInitialSteeringPoll,
        CompletableFuture<Void> future
    ) {
        var context = new AgentContext(
            stateSnapshot.systemPrompt(),
            stateSnapshot.messages(),
            stateSnapshot.tools()
        );
        var skipFirstSteeringPoll = new AtomicBoolean(skipInitialSteeringPoll);
        var loopConfig = AgentLoopConfig.builder(
            stateSnapshot.model(),
            streamFunction,
            convertToLlm
        )
            .thinkingLevel(stateSnapshot.thinkingLevel())
            .transformContext(transformContext)
            .apiKeyProvider(apiKeyProvider)
            .steeringMessages(() -> AgentLoopConfig.completedFuture(
                skipFirstSteeringPoll.getAndSet(false)
                    ? List.of()
                    : dequeueSteeringMessages()
            ))
            .followUpMessages(() -> AgentLoopConfig.completedFuture(dequeueFollowUpMessages()))
            .apiKey(apiKey)
            .transport(transport)
            .cacheRetention(cacheRetention)
            .sessionId(sessionId)
            .headers(headers)
            .maxRetryDelayMs(maxRetryDelayMs)
            .thinkingBudgets(thinkingBudgets)
            .build();

        var stream = continueOnly
            ? AgentLoop.continueLoop(context, loopConfig)
            : AgentLoop.start(promptMessages, context, loopConfig);
        var subscription = stream.subscribe(this::handleEvent);
        activeStream = stream;
        activeSubscription = subscription;

        try {
            stream.result().join();
            future.complete(null);
        } catch (CancellationException cancellationException) {
            future.complete(null);
        } catch (Exception exception) {
            var message = rootMessage(exception);
            emitState(updateState(current -> current.finishStreaming(current.messages())
                .clearPendingToolCalls()
                .withError(message)));
            future.completeExceptionally(exception);
        } finally {
            subscription.unsubscribe();
            activeSubscription = null;
            activeStream = null;
            runningPrompt = null;
        }
    }

    private void handleEvent(AgentEvent event) {
        var updatedState = updateState(current -> switch (event) {
            case AgentEvent.MessageStart messageStart ->
                current.startStreaming(messageStart.message());
            case AgentEvent.MessageUpdate messageUpdate ->
                current.withStreamingMessage(messageUpdate.message());
            case AgentEvent.MessageEnd messageEnd ->
                current.withStreamingMessage(null).appendMessage(messageEnd.message());
            case AgentEvent.ToolExecutionStart toolExecutionStart ->
                current.addPendingToolCall(toolExecutionStart.toolCallId());
            case AgentEvent.ToolExecutionEnd toolExecutionEnd ->
                current.removePendingToolCall(toolExecutionEnd.toolCallId());
            case AgentEvent.TurnEnd turnEnd -> turnEnd.message().errorMessage() != null
                ? current.withError(turnEnd.message().errorMessage())
                : current;
            case AgentEvent.AgentEnd ignored ->
                current.finishStreaming(current.messages()).clearPendingToolCalls();
            default -> current;
        });
        emitState(updatedState);
        emitEvent(event);
    }

    private AgentState updateState(java.util.function.UnaryOperator<AgentState> updater) {
        Objects.requireNonNull(updater, "updater");
        synchronized (monitor) {
            state = Objects.requireNonNull(updater.apply(state), "updatedState");
            return state;
        }
    }

    private void emitEvent(AgentEvent event) {
        for (var listener : eventListeners) {
            listener.accept(event);
        }
    }

    private void emitState(AgentState updatedState) {
        for (var listener : stateListeners) {
            listener.accept(updatedState);
        }
    }

    private static CompletionStage<List<Message>> defaultConvertToLlm(List<AgentMessage> messages) {
        return AgentLoopConfig.completedFuture(messages.stream()
            .filter(message -> !(message instanceof AgentMessage.CustomMessage))
            .map(AgentMessages::toLlmMessage)
            .toList());
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private List<AgentMessage> dequeueSteeringMessages() {
        return dequeueMessages(steeringQueue, steeringMode);
    }

    private List<AgentMessage> dequeueFollowUpMessages() {
        return dequeueMessages(followUpQueue, followUpMode);
    }

    private List<AgentMessage> dequeueMessages(ArrayDeque<AgentMessage> queue, QueueMode mode) {
        synchronized (monitor) {
            if (queue.isEmpty()) {
                return List.of();
            }
            if (mode == QueueMode.ONE_AT_A_TIME) {
                return List.of(queue.removeFirst());
            }
            var messages = new ArrayList<AgentMessage>(queue);
            queue.clear();
            return List.copyOf(messages);
        }
    }

    public static final class Builder {
        private final Model model;
        private String systemPrompt = "";
        private ThinkingLevel thinkingLevel;
        private List<AgentTool<?>> tools = List.of();
        private List<AgentMessage> messages = List.of();
        private AgentLoopConfig.MessageConverter convertToLlm;
        private AgentLoopConfig.ContextTransformer transformContext;
        private QueueMode steeringMode = QueueMode.ONE_AT_A_TIME;
        private QueueMode followUpMode = QueueMode.ONE_AT_A_TIME;
        private AgentLoopConfig.AssistantStreamFunction streamFunction;
        private String apiKey;
        private AgentLoopConfig.ApiKeyProvider apiKeyProvider;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Long maxRetryDelayMs;
        private ThinkingBudgets thinkingBudgets;

        private Builder(Model model) {
            this.model = Objects.requireNonNull(model, "model");
        }

        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder thinkingLevel(ThinkingLevel thinkingLevel) { this.thinkingLevel = thinkingLevel; return this; }
        public Builder tools(List<AgentTool<?>> tools) { this.tools = List.copyOf(tools); return this; }
        public Builder messages(List<AgentMessage> messages) { this.messages = List.copyOf(messages); return this; }
        public Builder convertToLlm(AgentLoopConfig.MessageConverter convertToLlm) { this.convertToLlm = convertToLlm; return this; }
        public Builder transformContext(AgentLoopConfig.ContextTransformer transformContext) { this.transformContext = transformContext; return this; }
        public Builder steeringMode(QueueMode steeringMode) { this.steeringMode = steeringMode; return this; }
        public Builder followUpMode(QueueMode followUpMode) { this.followUpMode = followUpMode; return this; }
        public Builder streamFunction(AgentLoopConfig.AssistantStreamFunction streamFunction) { this.streamFunction = streamFunction; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder apiKeyProvider(AgentLoopConfig.ApiKeyProvider apiKeyProvider) { this.apiKeyProvider = apiKeyProvider; return this; }
        public Builder transport(Transport transport) { this.transport = transport; return this; }
        public Builder cacheRetention(CacheRetention cacheRetention) { this.cacheRetention = cacheRetention; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder headers(Map<String, String> headers) { this.headers.clear(); this.headers.putAll(headers); return this; }
        public Builder header(String key, String value) { this.headers.put(key, value); return this; }
        public Builder maxRetryDelayMs(Long maxRetryDelayMs) { this.maxRetryDelayMs = maxRetryDelayMs; return this; }
        public Builder thinkingBudgets(ThinkingBudgets thinkingBudgets) { this.thinkingBudgets = thinkingBudgets; return this; }

        public Agent build() {
            if (streamFunction == null) {
                throw new IllegalStateException("Agent streamFunction must be configured");
            }
            return new Agent(this);
        }
    }
}
