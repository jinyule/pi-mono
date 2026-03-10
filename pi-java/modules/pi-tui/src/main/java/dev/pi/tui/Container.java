package dev.pi.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Container implements Component {
    private final List<Component> children = new ArrayList<>();

    public void addChild(Component component) {
        children.add(Objects.requireNonNull(component, "component"));
    }

    public void removeChild(Component component) {
        children.remove(component);
    }

    public void clear() {
        children.clear();
    }

    @Override
    public void invalidate() {
        for (var child : children) {
            child.invalidate();
        }
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        for (var child : children) {
            lines.addAll(child.render(width));
        }
        return List.copyOf(lines);
    }
}
