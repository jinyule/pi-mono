package dev.pi.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.pi.ai.model.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ToolArgumentsValidator {
    private ToolArgumentsValidator() {
    }

    static JsonNode validate(AgentTool<?> tool, ToolCall toolCall) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(toolCall, "toolCall");

        var schema = Objects.requireNonNull(tool.parametersSchema(), "tool.parametersSchema");
        var normalizedArguments = coerceNode(schema, toolCall.arguments());
        var errors = new ArrayList<String>();
        validateNode(schema, normalizedArguments, "root", errors);

        if (errors.isEmpty()) {
            return normalizedArguments;
        }

        throw new IllegalArgumentException(
            "Validation failed for tool \"" + toolCall.name() + "\":\n"
                + String.join("\n", errors)
                + "\n\nReceived arguments:\n"
                + toolCall.arguments().toPrettyString()
        );
    }

    private static JsonNode coerceNode(JsonNode schema, JsonNode value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (schema == null || schema.isNull()) {
            return value.deepCopy();
        }

        var types = declaredTypes(schema);
        if (value.isObject()) {
            var objectValue = value.deepCopy();
            if (objectValue instanceof ObjectNode objectNode) {
                var properties = schema.path("properties");
                objectNode.fieldNames().forEachRemaining(fieldName -> {
                    JsonNode propertySchema = null;
                    if (properties.isObject() && properties.has(fieldName)) {
                        propertySchema = properties.get(fieldName);
                    } else if (schema.path("additionalProperties").isObject()) {
                        propertySchema = schema.path("additionalProperties");
                    }
                    if (propertySchema != null) {
                        objectNode.set(fieldName, coerceNode(propertySchema, objectNode.get(fieldName)));
                    }
                });
                return objectNode;
            }
        }

        if (value.isArray() && types.contains("array")) {
            var itemsSchema = schema.path("items");
            var arrayValue = (ArrayNode) value.deepCopy();
            for (int index = 0; index < arrayValue.size(); index++) {
                arrayValue.set(index, coerceNode(itemsSchema, arrayValue.get(index)));
            }
            return arrayValue;
        }

        if (value.isTextual()) {
            var text = value.textValue();
            if (types.contains("boolean")) {
                if ("true".equalsIgnoreCase(text)) {
                    return BooleanNode.TRUE;
                }
                if ("false".equalsIgnoreCase(text)) {
                    return BooleanNode.FALSE;
                }
            }
            if (types.contains("integer")) {
                try {
                    return LongNode.valueOf(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                }
            }
            if (types.contains("number")) {
                try {
                    return DoubleNode.valueOf(Double.parseDouble(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return value.deepCopy();
    }

    private static void validateNode(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema == null || schema.isNull()) {
            return;
        }

        var types = declaredTypes(schema);
        if (!types.isEmpty() && types.stream().noneMatch(type -> matchesType(type, value))) {
            errors.add("  - " + path + ": expected " + String.join(" or ", types));
            return;
        }

        validateEnum(schema, value, path, errors);
        validateConst(schema, value, path, errors);

        if (value.isObject()) {
            validateObject(schema, value, path, errors);
            return;
        }

        if (value.isArray()) {
            validateArray(schema, value, path, errors);
            return;
        }

        if (value.isTextual()) {
            validateString(schema, value, path, errors);
            return;
        }

        if (value.isNumber()) {
            validateNumber(schema, value, path, errors);
        }
    }

    private static void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var properties = schema.path("properties");
        var required = schema.path("required");
        if (required.isArray()) {
            for (var requiredProperty : required) {
                if (requiredProperty.isTextual() && !value.has(requiredProperty.textValue())) {
                    errors.add("  - " + path(requiredProperty.textValue(), path) + ": is required");
                }
            }
        }

        var declaredPropertyNames = new LinkedHashSet<String>();
        if (properties.isObject()) {
            properties.fieldNames().forEachRemaining(declaredPropertyNames::add);
            properties.fieldNames().forEachRemaining(propertyName -> {
                if (value.has(propertyName)) {
                    validateNode(properties.get(propertyName), value.get(propertyName), path(propertyName, path), errors);
                }
            });
        }

        var additionalProperties = schema.path("additionalProperties");
        value.fieldNames().forEachRemaining(propertyName -> {
            if (declaredPropertyNames.contains(propertyName)) {
                return;
            }
            if (additionalProperties.isBoolean() && !additionalProperties.booleanValue()) {
                errors.add("  - " + path(propertyName, path) + ": additional properties are not allowed");
                return;
            }
            if (additionalProperties.isObject()) {
                validateNode(additionalProperties, value.get(propertyName), path(propertyName, path), errors);
            }
        });
    }

    private static void validateArray(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var minItems = schema.get("minItems");
        if (minItems != null && value.size() < minItems.asInt()) {
            errors.add("  - " + path + ": must contain at least " + minItems.asInt() + " item(s)");
        }

        var maxItems = schema.get("maxItems");
        if (maxItems != null && value.size() > maxItems.asInt()) {
            errors.add("  - " + path + ": must contain at most " + maxItems.asInt() + " item(s)");
        }

        var items = schema.path("items");
        if (items.isObject()) {
            for (int index = 0; index < value.size(); index++) {
                validateNode(items, value.get(index), path + "[" + index + "]", errors);
            }
        }
    }

    private static void validateString(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var minLength = schema.get("minLength");
        if (minLength != null && value.textValue().length() < minLength.asInt()) {
            errors.add("  - " + path + ": length must be at least " + minLength.asInt());
        }

        var maxLength = schema.get("maxLength");
        if (maxLength != null && value.textValue().length() > maxLength.asInt()) {
            errors.add("  - " + path + ": length must be at most " + maxLength.asInt());
        }
    }

    private static void validateNumber(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var minimum = schema.get("minimum");
        if (minimum != null && value.asDouble() < minimum.asDouble()) {
            errors.add("  - " + path + ": must be >= " + minimum.asText());
        }

        var maximum = schema.get("maximum");
        if (maximum != null && value.asDouble() > maximum.asDouble()) {
            errors.add("  - " + path + ": must be <= " + maximum.asText());
        }
    }

    private static void validateEnum(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            for (var allowedValue : enumNode) {
                if (allowedValue.equals(value)) {
                    return;
                }
            }
            errors.add("  - " + path + ": must be one of " + enumNode);
        }
    }

    private static void validateConst(JsonNode schema, JsonNode value, String path, List<String> errors) {
        var constNode = schema.get("const");
        if (constNode != null && !constNode.equals(value)) {
            errors.add("  - " + path + ": must equal " + constNode);
        }
    }

    private static Set<String> declaredTypes(JsonNode schema) {
        var typeNode = schema.get("type");
        if (typeNode == null || typeNode.isNull()) {
            return Set.of();
        }
        if (typeNode.isTextual()) {
            return Set.of(typeNode.textValue());
        }
        if (!typeNode.isArray()) {
            return Set.of();
        }

        var types = new LinkedHashSet<String>();
        for (var node : typeNode) {
            if (node.isTextual()) {
                types.add(node.textValue());
            }
        }
        return Set.copyOf(types);
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private static String path(String child, String parent) {
        return "root".equals(parent) ? child : parent + "." + child;
    }
}
