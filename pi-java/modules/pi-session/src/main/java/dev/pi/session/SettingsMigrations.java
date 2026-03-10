package dev.pi.session;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

public final class SettingsMigrations {
    private SettingsMigrations() {
    }

    public static Settings migrate(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        var root = settings.toObjectNode();

        migrateQueueMode(root);
        migrateWebsockets(root);
        migrateLegacySkills(root);

        return new Settings(root);
    }

    private static void migrateQueueMode(ObjectNode root) {
        if (root.has("queueMode") && !root.has("steeringMode")) {
            root.set("steeringMode", root.get("queueMode").deepCopy());
            root.remove("queueMode");
        }
    }

    private static void migrateWebsockets(ObjectNode root) {
        if (!root.has("transport") && root.path("websockets").isBoolean()) {
            root.put("transport", root.path("websockets").asBoolean() ? "websocket" : "sse");
            root.remove("websockets");
        }
    }

    private static void migrateLegacySkills(ObjectNode root) {
        var skillsNode = root.get("skills");
        if (!(skillsNode instanceof ObjectNode skillsObject)) {
            return;
        }

        if (!root.has("enableSkillCommands") && skillsObject.path("enableSkillCommands").isBoolean()) {
            root.put("enableSkillCommands", skillsObject.path("enableSkillCommands").asBoolean());
        }

        var customDirectories = skillsObject.get("customDirectories");
        if (customDirectories instanceof ArrayNode directories && !directories.isEmpty()) {
            root.set("skills", directories.deepCopy());
            return;
        }

        root.remove("skills");
    }
}
