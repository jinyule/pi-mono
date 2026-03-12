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

    @Test
    void matchesCtrlShiftPSequence() {
        assertThat(KeyMatcher.matches("\u001b[112;6u", "shift+ctrl+p")).isTrue();
        assertThat(KeyMatcher.matches("\u001b[112;6u", "ctrl+shift+p")).isTrue();
    }

    @Test
    void matchesAltUpSequence() {
        assertThat(KeyMatcher.matches("\u001bp", "alt+up")).isTrue();
        assertThat(KeyMatcher.matches("\u001b[1;3A", "alt+up")).isTrue();
    }
}
