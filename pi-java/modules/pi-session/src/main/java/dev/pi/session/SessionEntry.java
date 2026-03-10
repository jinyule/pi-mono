package dev.pi.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionEntry.MessageEntry.class, name = "message"),
    @JsonSubTypes.Type(value = SessionEntry.ThinkingLevelChangeEntry.class, name = "thinking_level_change"),
    @JsonSubTypes.Type(value = SessionEntry.ModelChangeEntry.class, name = "model_change"),
    @JsonSubTypes.Type(value = SessionEntry.CompactionEntry.class, name = "compaction"),
    @JsonSubTypes.Type(value = SessionEntry.BranchSummaryEntry.class, name = "branch_summary"),
    @JsonSubTypes.Type(value = SessionEntry.CustomEntry.class, name = "custom"),
    @JsonSubTypes.Type(value = SessionEntry.CustomMessageEntry.class, name = "custom_message"),
    @JsonSubTypes.Type(value = SessionEntry.LabelEntry.class, name = "label"),
    @JsonSubTypes.Type(value = SessionEntry.SessionInfoEntry.class, name = "session_info"),
})
public sealed interface SessionEntry extends SessionFileEntry permits
    SessionEntry.MessageEntry,
    SessionEntry.ThinkingLevelChangeEntry,
    SessionEntry.ModelChangeEntry,
    SessionEntry.CompactionEntry,
    SessionEntry.BranchSummaryEntry,
    SessionEntry.CustomEntry,
    SessionEntry.CustomMessageEntry,
    SessionEntry.LabelEntry,
    SessionEntry.SessionInfoEntry {

    String id();

    String parentId();

    String timestamp();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        JsonNode message
    ) implements SessionEntry {
        public MessageEntry(String id, String parentId, String timestamp, JsonNode message) {
            this("message", id, parentId, timestamp, message);
        }

        public MessageEntry {
            if (!"message".equals(type)) {
                throw new IllegalArgumentException("MessageEntry type must be 'message'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(message, "message");
            message = message.deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ThinkingLevelChangeEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String thinkingLevel
    ) implements SessionEntry {
        public ThinkingLevelChangeEntry(String id, String parentId, String timestamp, String thinkingLevel) {
            this("thinking_level_change", id, parentId, timestamp, thinkingLevel);
        }

        public ThinkingLevelChangeEntry {
            if (!"thinking_level_change".equals(type)) {
                throw new IllegalArgumentException("ThinkingLevelChangeEntry type must be 'thinking_level_change'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(thinkingLevel, "thinkingLevel");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelChangeEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String provider,
        String modelId
    ) implements SessionEntry {
        public ModelChangeEntry(String id, String parentId, String timestamp, String provider, String modelId) {
            this("model_change", id, parentId, timestamp, provider, modelId);
        }

        public ModelChangeEntry {
            if (!"model_change".equals(type)) {
                throw new IllegalArgumentException("ModelChangeEntry type must be 'model_change'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(modelId, "modelId");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompactionEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String summary,
        String firstKeptEntryId,
        Integer tokensBefore,
        JsonNode details,
        Boolean fromHook
    ) implements SessionEntry {
        public CompactionEntry(
            String id,
            String parentId,
            String timestamp,
            String summary,
            String firstKeptEntryId,
            Integer tokensBefore,
            JsonNode details,
            Boolean fromHook
        ) {
            this("compaction", id, parentId, timestamp, summary, firstKeptEntryId, tokensBefore, details, fromHook);
        }

        public CompactionEntry {
            if (!"compaction".equals(type)) {
                throw new IllegalArgumentException("CompactionEntry type must be 'compaction'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(summary, "summary");
            details = details == null || details.isNull() ? null : details.deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BranchSummaryEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String fromId,
        String summary,
        JsonNode details,
        Boolean fromHook
    ) implements SessionEntry {
        public BranchSummaryEntry(
            String id,
            String parentId,
            String timestamp,
            String fromId,
            String summary,
            JsonNode details,
            Boolean fromHook
        ) {
            this("branch_summary", id, parentId, timestamp, fromId, summary, details, fromHook);
        }

        public BranchSummaryEntry {
            if (!"branch_summary".equals(type)) {
                throw new IllegalArgumentException("BranchSummaryEntry type must be 'branch_summary'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(fromId, "fromId");
            Objects.requireNonNull(summary, "summary");
            details = details == null || details.isNull() ? null : details.deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String customType,
        JsonNode data
    ) implements SessionEntry {
        public CustomEntry(String id, String parentId, String timestamp, String customType, JsonNode data) {
            this("custom", id, parentId, timestamp, customType, data);
        }

        public CustomEntry {
            if (!"custom".equals(type)) {
                throw new IllegalArgumentException("CustomEntry type must be 'custom'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(customType, "customType");
            data = data == null || data.isNull() ? null : data.deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomMessageEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String customType,
        JsonNode content,
        JsonNode details,
        boolean display
    ) implements SessionEntry {
        public CustomMessageEntry(
            String id,
            String parentId,
            String timestamp,
            String customType,
            JsonNode content,
            JsonNode details,
            boolean display
        ) {
            this("custom_message", id, parentId, timestamp, customType, content, details, display);
        }

        public CustomMessageEntry {
            if (!"custom_message".equals(type)) {
                throw new IllegalArgumentException("CustomMessageEntry type must be 'custom_message'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(customType, "customType");
            Objects.requireNonNull(content, "content");
            content = content.deepCopy();
            details = details == null || details.isNull() ? null : details.deepCopy();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LabelEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String targetId,
        String label
    ) implements SessionEntry {
        public LabelEntry(String id, String parentId, String timestamp, String targetId, String label) {
            this("label", id, parentId, timestamp, targetId, label);
        }

        public LabelEntry {
            if (!"label".equals(type)) {
                throw new IllegalArgumentException("LabelEntry type must be 'label'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SessionInfoEntry(
        String type,
        String id,
        String parentId,
        String timestamp,
        String name
    ) implements SessionEntry {
        public SessionInfoEntry(String id, String parentId, String timestamp, String name) {
            this("session_info", id, parentId, timestamp, name);
        }

        public SessionInfoEntry {
            if (!"session_info".equals(type)) {
                throw new IllegalArgumentException("SessionInfoEntry type must be 'session_info'");
            }
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }
}
