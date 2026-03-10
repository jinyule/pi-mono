package dev.pi.tui;

import java.util.List;

public final class SettingItem {
    private final String id;
    private final String label;
    private final String description;
    private final List<String> values;
    private final SettingSubmenuFactory submenu;
    private String currentValue;

    public SettingItem(String id, String label, String description, String currentValue, List<String> values, SettingSubmenuFactory submenu) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.description = description;
        this.currentValue = currentValue == null ? "" : currentValue;
        this.values = values == null ? List.of() : List.copyOf(values);
        this.submenu = submenu;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String currentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue == null ? "" : currentValue;
    }

    public List<String> values() {
        return values;
    }

    public SettingSubmenuFactory submenu() {
        return submenu;
    }
}
