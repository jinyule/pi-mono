package dev.pi.cli;

import dev.pi.ai.model.Model;
import dev.pi.ai.registry.ModelRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PiListModelsCommand {
    private final ModelRegistry modelRegistry;
    private final Appendable output;

    public PiListModelsCommand(ModelRegistry modelRegistry, Appendable output) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run(String query) {
        var models = getSortedModels();
        if (models.isEmpty()) {
            appendLine("No models available.");
            return;
        }

        var filteredModels = filterModels(models, query);
        if (filteredModels.isEmpty()) {
            appendLine("No models matching \"%s\"".formatted(query));
            return;
        }

        var rows = filteredModels.stream()
            .map(ModelRow::from)
            .toList();
        var widths = ColumnWidths.from(rows);

        appendLine(widths.renderHeader());
        for (var row : rows) {
            appendLine(widths.renderRow(row));
        }
    }

    private List<Model> getSortedModels() {
        var models = new ArrayList<Model>();
        for (var provider : modelRegistry.getProviders()) {
            models.addAll(modelRegistry.getModels(provider));
        }
        models.sort(Comparator
            .comparing(Model::provider)
            .thenComparing(Model::id));
        return List.copyOf(models);
    }

    private static List<Model> filterModels(List<Model> models, String query) {
        if (query == null || query.isBlank()) {
            return models;
        }
        var normalizedQuery = query.toLowerCase(Locale.ROOT);
        return models.stream()
            .filter(model -> fuzzyMatch("%s %s".formatted(model.provider(), model.id()).toLowerCase(Locale.ROOT), normalizedQuery))
            .toList();
    }

    private static boolean fuzzyMatch(String text, String query) {
        int queryIndex = 0;
        for (int textIndex = 0; textIndex < text.length() && queryIndex < query.length(); textIndex += 1) {
            if (text.charAt(textIndex) == query.charAt(queryIndex)) {
                queryIndex += 1;
            }
        }
        return queryIndex == query.length();
    }

    private void appendLine(String line) {
        try {
            output.append(line);
            output.append(System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write list-models output", exception);
        }
    }

    private record ModelRow(
        String provider,
        String model,
        String context,
        String maxOut,
        String thinking,
        String images
    ) {
        private static ModelRow from(Model model) {
            return new ModelRow(
                model.provider(),
                model.id(),
                formatTokenCount(model.contextWindow()),
                formatTokenCount(model.maxTokens()),
                model.reasoning() ? "yes" : "no",
                model.input().contains("image") ? "yes" : "no"
            );
        }

        private static String formatTokenCount(int count) {
            if (count >= 1_000_000) {
                var millions = count / 1_000_000.0;
                return millions % 1 == 0 ? "%.0fM".formatted(millions) : "%.1fM".formatted(millions);
            }
            if (count >= 1_000) {
                var thousands = count / 1_000.0;
                return thousands % 1 == 0 ? "%.0fK".formatted(thousands) : "%.1fK".formatted(thousands);
            }
            return Integer.toString(count);
        }
    }

    private record ColumnWidths(
        int provider,
        int model,
        int context,
        int maxOut,
        int thinking,
        int images
    ) {
        private static final String PROVIDER_HEADER = "provider";
        private static final String MODEL_HEADER = "model";
        private static final String CONTEXT_HEADER = "context";
        private static final String MAX_OUT_HEADER = "max-out";
        private static final String THINKING_HEADER = "thinking";
        private static final String IMAGES_HEADER = "images";

        private static ColumnWidths from(List<ModelRow> rows) {
            return new ColumnWidths(
                maxWidth(PROVIDER_HEADER, rows.stream().map(ModelRow::provider).toList()),
                maxWidth(MODEL_HEADER, rows.stream().map(ModelRow::model).toList()),
                maxWidth(CONTEXT_HEADER, rows.stream().map(ModelRow::context).toList()),
                maxWidth(MAX_OUT_HEADER, rows.stream().map(ModelRow::maxOut).toList()),
                maxWidth(THINKING_HEADER, rows.stream().map(ModelRow::thinking).toList()),
                maxWidth(IMAGES_HEADER, rows.stream().map(ModelRow::images).toList())
            );
        }

        private static int maxWidth(String header, List<String> values) {
            var width = header.length();
            for (var value : values) {
                width = Math.max(width, value.length());
            }
            return width;
        }

        private String renderHeader() {
            return renderRow(new ModelRow(
                PROVIDER_HEADER,
                MODEL_HEADER,
                CONTEXT_HEADER,
                MAX_OUT_HEADER,
                THINKING_HEADER,
                IMAGES_HEADER
            ));
        }

        private String renderRow(ModelRow row) {
            return String.join(
                "  ",
                pad(row.provider(), provider),
                pad(row.model(), model),
                pad(row.context(), context),
                pad(row.maxOut(), maxOut),
                pad(row.thinking(), thinking),
                pad(row.images(), images)
            );
        }

        private static String pad(String value, int width) {
            return value + " ".repeat(Math.max(0, width - value.length()));
        }
    }
}
