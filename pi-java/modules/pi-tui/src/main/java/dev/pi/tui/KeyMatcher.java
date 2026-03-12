package dev.pi.tui;

import java.util.Map;
import java.util.Objects;

public final class KeyMatcher {
    private static final Map<String, String> NAMED_SEQUENCES = Map.ofEntries(
        Map.entry("enter", "\r"),
        Map.entry("tab", "\t"),
        Map.entry("shift+tab", "\u001b[Z"),
        Map.entry("escape", "\u001b"),
        Map.entry("backspace", "\u007f"),
        Map.entry("delete", "\u001b[3~"),
        Map.entry("up", "\u001b[A"),
        Map.entry("down", "\u001b[B"),
        Map.entry("left", "\u001b[D"),
        Map.entry("right", "\u001b[C"),
        Map.entry("home", "\u001b[H"),
        Map.entry("end", "\u001b[F"),
        Map.entry("alt+enter", "\u001b\r"),
        Map.entry("alt+up", "\u001bp"),
        Map.entry("ctrl+a", "\u0001"),
        Map.entry("ctrl+b", "\u0002"),
        Map.entry("ctrl+c", "\u0003"),
        Map.entry("ctrl+d", "\u0004"),
        Map.entry("ctrl+e", "\u0005"),
        Map.entry("ctrl+f", "\u0006"),
        Map.entry("ctrl+g", "\u0007"),
        Map.entry("ctrl+k", "\u000b"),
        Map.entry("ctrl+l", "\u000c"),
        Map.entry("ctrl+n", "\u000e"),
        Map.entry("ctrl+o", "\u000f"),
        Map.entry("ctrl+p", "\u0010"),
        Map.entry("shift+ctrl+p", "\u001b[112;6u"),
        Map.entry("ctrl+r", "\u0012"),
        Map.entry("ctrl+s", "\u0013"),
        Map.entry("ctrl+t", "\u0014"),
        Map.entry("ctrl+u", "\u0015"),
        Map.entry("ctrl+v", "\u0016"),
        Map.entry("ctrl+w", "\u0017"),
        Map.entry("ctrl+y", "\u0019"),
        Map.entry("ctrl+z", "\u001a"),
        Map.entry("ctrl+-", "\u001f"),
        Map.entry("alt+b", "\u001bb"),
        Map.entry("alt+d", "\u001bd"),
        Map.entry("alt+f", "\u001bf"),
        Map.entry("alt+y", "\u001by"),
        Map.entry("alt+backspace", "\u001b\u007f"),
        Map.entry("alt+delete", "\u001b[3;3~"),
        Map.entry("alt+left", "\u001b[1;3D"),
        Map.entry("alt+right", "\u001b[1;3C"),
        Map.entry("ctrl+left", "\u001b[1;5D"),
        Map.entry("ctrl+right", "\u001b[1;5C")
    );

    private KeyMatcher() {
    }

    public static boolean matches(String data, String keyId) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(keyId, "keyId");
        var genericAltSequence = genericAltSequence(keyId);
        if (genericAltSequence != null) {
            return genericAltSequence.equals(data);
        }
        return switch (keyId) {
            case "enter" -> "\r".equals(data) || "\n".equals(data);
            case "tab" -> "\t".equals(data);
            case "shift+tab" -> "\u001b[Z".equals(data);
            case "escape" -> "\u001b".equals(data);
            case "backspace" -> "\u007f".equals(data) || "\b".equals(data);
            case "delete" -> "\u001b[3~".equals(data);
            case "up" -> "\u001b[A".equals(data);
            case "down" -> "\u001b[B".equals(data);
            case "left" -> "\u001b[D".equals(data);
            case "right" -> "\u001b[C".equals(data);
            case "home" -> "\u001b[H".equals(data) || "\u001bOH".equals(data);
            case "end" -> "\u001b[F".equals(data) || "\u001bOF".equals(data);
            case "alt+up" -> "\u001bp".equals(data) || "\u001b[1;3A".equals(data);
            case "alt+left" -> "\u001b[1;3D".equals(data) || "\u001bB".equals(data);
            case "alt+right" -> "\u001b[1;3C".equals(data) || "\u001bF".equals(data);
            case "alt+enter" -> "\u001b\r".equals(data);
            case "ctrl+left" -> "\u001b[1;5D".equals(data) || "\u001b[5D".equals(data);
            case "ctrl+right" -> "\u001b[1;5C".equals(data) || "\u001b[5C".equals(data);
            case "shift+ctrl+p", "ctrl+shift+p" -> "\u001b[112;6u".equals(data) || "\u001b[80;6u".equals(data);
            case "alt+backspace" -> "\u001b\u007f".equals(data);
            case "alt+delete" -> "\u001b[3;3~".equals(data);
            default -> data.equals(NAMED_SEQUENCES.get(keyId));
        };
    }

    private static String genericAltSequence(String keyId) {
        if (!keyId.startsWith("alt+") || keyId.length() != 5) {
            return null;
        }
        var ch = keyId.charAt(4);
        if (!Character.isLetterOrDigit(ch)) {
            return null;
        }
        return "\u001b" + Character.toLowerCase(ch);
    }
}
