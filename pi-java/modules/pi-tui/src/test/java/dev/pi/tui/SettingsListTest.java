package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SettingsListTest {
    private static final SettingsListTheme THEME = new SettingsListTheme() {
        @Override
        public String label(String text, boolean selected) {
            return text;
        }

        @Override
        public String value(String text, boolean selected) {
            return text;
        }

        @Override
        public String description(String text) {
            return text;
        }

        @Override
        public String cursor() {
            return "→ ";
        }

        @Override
        public String hint(String text) {
            return text;
        }
    };

    @Test
    void cyclesValuesAndCallsOnChange() {
        var changed = new AtomicReference<String>();
        var settings = new SettingsList(
            List.of(new SettingItem("theme", "Theme", "Color theme", "dark", List.of("dark", "light"), null)),
            5,
            THEME,
            (id, value) -> changed.set(id + "=" + value),
            () -> {
            }
        );

        settings.handleInput(" ");

        assertThat(changed.get()).isEqualTo("theme=light");
        assertThat(settings.render(60)).anyMatch(line -> line.contains("light"));
        assertThat(settings.render(60)).anyMatch(line -> line.contains("Color theme"));
    }

    @Test
    void delegatesToSubmenuAndAppliesReturnedValue() {
        var changed = new AtomicReference<String>();
        var submenu = new SettingSubmenuFactory() {
            @Override
            public Component create(String currentValue, ValueConsumer done) {
                return new Component() {
                    @Override
                    public List<String> render(int width) {
                        return List.of("submenu:" + currentValue);
                    }

                    @Override
                    public void handleInput(String data) {
                        done.accept("claude");
                    }
                };
            }
        };
        var settings = new SettingsList(
            List.of(new SettingItem("model", "Model", null, "gpt", List.of(), submenu)),
            5,
            THEME,
            (id, value) -> changed.set(id + "=" + value),
            () -> {
            }
        );

        settings.handleInput("\r");
        assertThat(settings.render(40)).containsExactly("submenu:gpt");

        settings.handleInput("x");

        assertThat(changed.get()).isEqualTo("model=claude");
        assertThat(settings.render(40)).anyMatch(line -> line.contains("claude"));
    }

    @Test
    void supportsSearchAndNoMatchRendering() {
        var settings = new SettingsList(
            List.of(
                new SettingItem("theme", "Theme", null, "dark", List.of("dark", "light"), null),
                new SettingItem("model", "Model", null, "gpt", List.of("gpt", "claude"), null)
            ),
            5,
            THEME,
            (id, value) -> {
            },
            () -> {
            },
            new SettingsListOptions(true)
        );
        settings.setFocused(true);

        settings.handleInput("m");

        assertThat(settings.render(60)).anyMatch(line -> line.contains("Model"));

        settings.handleInput("\u0001");
        settings.handleInput("\u007f");
        settings.handleInput("\u007f");
        settings.handleInput("z");

        assertThat(settings.render(60)).anyMatch(line -> line.contains("No matching settings"));
    }
}
