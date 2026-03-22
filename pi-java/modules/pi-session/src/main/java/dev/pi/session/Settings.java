package dev.pi.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class Settings {
    private final ObjectNode root;

    public Settings(ObjectNode root) {
        this.root = root == null ? JsonNodeFactory.instance.objectNode() : root.deepCopy();
    }

    public static Settings empty() {
        return new Settings(JsonNodeFactory.instance.objectNode());
    }

    public ObjectNode toObjectNode() {
        return root.deepCopy();
    }

    public JsonNode at(String jsonPointer) {
        Objects.requireNonNull(jsonPointer, "jsonPointer");
        return root.at(jsonPointer).deepCopy();
    }

    public String getString(String jsonPointer) {
        var node = at(jsonPointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    public boolean getBoolean(String jsonPointer, boolean defaultValue) {
        var node = at(jsonPointer);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asBoolean();
    }

    public int getInt(String jsonPointer, int defaultValue) {
        var node = at(jsonPointer);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asInt();
    }

    public List<String> getStringList(String jsonPointer) {
        var node = at(jsonPointer);
        if (!node.isArray()) {
            return List.of();
        }

        var values = new ArrayList<String>(node.size());
        for (var element : node) {
            if (element.isTextual()) {
                values.add(element.asText());
            }
        }
        return List.copyOf(values);
    }

    public List<PackageSource> getPackageSources(String jsonPointer) {
        var node = at(jsonPointer);
        if (!node.isArray()) {
            return List.of();
        }

        var values = new ArrayList<PackageSource>(node.size());
        for (var element : node) {
            try {
                values.add(PackageSource.parse(element));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(values);
    }

    public Settings merge(Settings overrides) {
        Objects.requireNonNull(overrides, "overrides");
        return new Settings(deepMerge(root.deepCopy(), overrides.root));
    }

    public Settings withMutations(Consumer<ObjectNode> mutator) {
        Objects.requireNonNull(mutator, "mutator");
        var copy = root.deepCopy();
        mutator.accept(copy);
        return new Settings(copy);
    }

    public Settings withStringList(String fieldName, List<String> values) {
        Objects.requireNonNull(fieldName, "fieldName");
        return withMutations(root -> {
            if (values == null) {
                root.remove(fieldName);
                return;
            }

            ArrayNode array = root.putArray(fieldName);
            for (var value : values) {
                if (value == null) {
                    continue;
                }
                array.add(value);
            }
        });
    }

    public Settings withPackageSources(String fieldName, List<PackageSource> sources) {
        Objects.requireNonNull(fieldName, "fieldName");
        return withMutations(root -> {
            if (sources == null) {
                root.remove(fieldName);
                return;
            }

            ArrayNode array = root.putArray(fieldName);
            for (var source : sources) {
                if (source == null) {
                    continue;
                }
                array.add(source.toJsonNode());
            }
        });
    }

    private static ObjectNode deepMerge(ObjectNode base, ObjectNode overrides) {
        overrides.fields().forEachRemaining(entry -> {
            var fieldName = entry.getKey();
            var overrideValue = entry.getValue();
            var baseValue = base.get(fieldName);

            if (overrideValue != null && overrideValue.isObject() && baseValue != null && baseValue.isObject()) {
                base.set(fieldName, deepMerge(((ObjectNode) baseValue).deepCopy(), (ObjectNode) overrideValue));
                return;
            }

            base.set(fieldName, overrideValue == null ? null : overrideValue.deepCopy());
        });
        return base;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Settings settings)) {
            return false;
        }
        return root.equals(settings.root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
