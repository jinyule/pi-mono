package dev.pi.tui;

@FunctionalInterface
public interface SettingSubmenuFactory {
    Component create(String currentValue, ValueConsumer done);

    @FunctionalInterface
    interface ValueConsumer {
        void accept(String selectedValue);
    }
}
