package dev.pi.cli;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PiCliApplication {
    private final PiCliParser parser;
    private final ListModelsHandler listModelsHandler;
    private final ExportHandler exportHandler;
    private final SessionFactory sessionFactory;
    private final ModeHandler interactiveHandler;
    private final ModeHandler printHandler;
    private final ModeHandler jsonHandler;
    private final ModeHandler rpcHandler;

    private PiCliApplication(Builder builder) {
        this.parser = builder.parser;
        this.listModelsHandler = builder.listModelsHandler;
        this.exportHandler = builder.exportHandler;
        this.sessionFactory = builder.sessionFactory;
        this.interactiveHandler = builder.interactiveHandler;
        this.printHandler = builder.printHandler;
        this.jsonHandler = builder.jsonHandler;
        this.rpcHandler = builder.rpcHandler;
    }

    public static Builder builder(SessionFactory sessionFactory) {
        return new Builder(sessionFactory);
    }

    public CompletionStage<Void> run(String... argv) {
        var args = parser.parse(argv);
        if (args.listModelsRequested()) {
            return listModelsHandler.run(args);
        }
        if (args.exportRequested()) {
            return exportHandler.run(args);
        }
        final PiInteractiveSession session;
        try {
            session = sessionFactory.create(args);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return switch (args.mode()) {
            case INTERACTIVE -> interactiveHandler.run(args, session);
            case PRINT -> printHandler.run(args, session);
            case JSON -> jsonHandler.run(args, session);
            case RPC -> rpcHandler.run(args, session);
        };
    }

    @FunctionalInterface
    public interface SessionFactory {
        PiInteractiveSession create(PiCliArgs args) throws Exception;
    }

    @FunctionalInterface
    public interface ListModelsHandler {
        CompletionStage<Void> run(PiCliArgs args);
    }

    @FunctionalInterface
    public interface ExportHandler {
        CompletionStage<Void> run(PiCliArgs args);
    }

    @FunctionalInterface
    public interface ModeHandler {
        CompletionStage<Void> run(PiCliArgs args, PiInteractiveSession session);
    }

    public static final class Builder {
        private final SessionFactory sessionFactory;
        private PiCliParser parser = new PiCliParser();
        private ListModelsHandler listModelsHandler = noOpListModelsCommand();
        private ExportHandler exportHandler = noOpExportCommand();
        private ModeHandler interactiveHandler = noOp();
        private ModeHandler printHandler = noOp();
        private ModeHandler jsonHandler = noOp();
        private ModeHandler rpcHandler = noOp();

        private Builder(SessionFactory sessionFactory) {
            this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        }

        public Builder parser(PiCliParser parser) {
            this.parser = Objects.requireNonNull(parser, "parser");
            return this;
        }

        public Builder listModelsHandler(ListModelsHandler listModelsHandler) {
            this.listModelsHandler = Objects.requireNonNull(listModelsHandler, "listModelsHandler");
            return this;
        }

        public Builder exportHandler(ExportHandler exportHandler) {
            this.exportHandler = Objects.requireNonNull(exportHandler, "exportHandler");
            return this;
        }

        public Builder interactiveHandler(ModeHandler interactiveHandler) {
            this.interactiveHandler = Objects.requireNonNull(interactiveHandler, "interactiveHandler");
            return this;
        }

        public Builder printHandler(ModeHandler printHandler) {
            this.printHandler = Objects.requireNonNull(printHandler, "printHandler");
            return this;
        }

        public Builder jsonHandler(ModeHandler jsonHandler) {
            this.jsonHandler = Objects.requireNonNull(jsonHandler, "jsonHandler");
            return this;
        }

        public Builder rpcHandler(ModeHandler rpcHandler) {
            this.rpcHandler = Objects.requireNonNull(rpcHandler, "rpcHandler");
            return this;
        }

        public PiCliApplication build() {
            return new PiCliApplication(this);
        }

        private static ModeHandler noOp() {
            return (args, session) -> CompletableFuture.completedFuture(null);
        }

        private static ListModelsHandler noOpListModelsCommand() {
            return args -> CompletableFuture.completedFuture(null);
        }

        private static ExportHandler noOpExportCommand() {
            return args -> CompletableFuture.completedFuture(null);
        }
    }
}
