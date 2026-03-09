package dev.pi.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageContent(
    String type,
    String data,
    String mimeType
) implements UserContent {
    public ImageContent(String data, String mimeType) {
        this("image", data, mimeType);
    }

    public ImageContent {
        if (!"image".equals(type)) {
            throw new IllegalArgumentException("ImageContent type must be 'image'");
        }
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(mimeType, "mimeType");
    }
}

