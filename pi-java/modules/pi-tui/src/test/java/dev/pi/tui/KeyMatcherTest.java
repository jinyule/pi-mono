package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyMatcherTest {
    @Test
    void matchesConfiguredCtrlRSequence() {
        assertThat(KeyMatcher.matches("\u0012", "ctrl+r")).isTrue();
    }

    @Test
    void matchesTabSequence() {
        assertThat(KeyMatcher.matches("\t", "tab")).isTrue();
    }

    @Test
    void matchesShiftTabSequence() {
        assertThat(KeyMatcher.matches("\u001b[Z", "shift+tab")).isTrue();
    }

    @Test
    void matchesGenericAltLetterSequence() {
        assertThat(KeyMatcher.matches("\u001bn", "alt+n")).isTrue();
    }
}
