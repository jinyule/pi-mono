package dev.pi.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PackageSource(
    String source,
    List<String> extensions,
    List<String> skills,
    List<String> prompts,
    List<String> themes
) {
    public PackageSource {
        Objects.requireNonNull(source, "source");
        source = source.trim();
        if (source.isBlank()) {
            throw new IllegalArgumentException("Package source must not be blank");
        }
        extensions = copyList(extensions);
        skills = copyList(skills);
        prompts = copyList(prompts);
        themes = copyList(themes);
    }

    public PackageSource(String source) {
        this(source, List.of(), List.of(), List.of(), List.of());
    }

    public static PackageSource parse(JsonNode node) {
        Objects.requireNonNull(node, "node");
        if (node.isTextual()) {
            return new PackageSource(node.asText());
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Package source must be a string or object");
        }

        var source = node.path("source");
        if (!source.isTextual() || source.asText().isBlank()) {
            throw new IllegalArgumentException("Package source object must have a non-empty source");
        }

        return new PackageSource(
            source.asText(),
            readStringList(node.path("extensions")),
            readStringList(node.path("skills")),
            readStringList(node.path("prompts")),
            readStringList(node.path("themes"))
        );
    }

    public JsonNode toJsonNode() {
        if (extensions.isEmpty() && skills.isEmpty() && prompts.isEmpty() && themes.isEmpty()) {
            return JsonNodeFactory.instance.textNode(source);
        }

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("source", source);
        writeList(node, "extensions", extensions);
        writeList(node, "skills", skills);
        writeList(node, "prompts", prompts);
        writeList(node, "themes", themes);
        return node;
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        var copy = new ArrayList<String>(values.size());
        for (var value : values) {
            if (value == null) {
                continue;
            }
            var trimmed = value.trim();
            if (!trimmed.isBlank()) {
                copy.add(trimmed);
            }
        }
        return List.copyOf(copy);
    }

    private static List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }

        var values = new ArrayList<String>(node.size());
        for (var element : node) {
            if (!element.isTextual()) {
                continue;
            }
            var trimmed = element.asText().trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private static void writeList(ObjectNode node, String fieldName, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        ArrayNode array = node.putArray(fieldName);
        for (var value : values) {
            array.add(value);
        }
    }
}
