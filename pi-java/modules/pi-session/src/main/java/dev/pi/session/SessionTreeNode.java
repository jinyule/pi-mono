package dev.pi.session;

import java.util.List;
import java.util.Objects;

public record SessionTreeNode(
    SessionEntry entry,
    List<SessionTreeNode> children,
    String label
) {
    public SessionTreeNode {
        Objects.requireNonNull(entry, "entry");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }
}
