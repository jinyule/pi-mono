package dev.pi.tui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyMatcherTest {
    @Test
    void matchesConfiguredCtrlRSequence() {
        assertThat(KeyMatcher.matches("\u0012", "ctrl+r")).isTrue();
    }

    @Test
    void matchesConfiguredCtrlLSequence() {
        assertThat(KeyMatcher.matches("\u000c", "ctrl+l")).isTrue();
    }

    @Test
    void matchesConfiguredCtrlOSequence() {
        assertThat(KeyMatcher.matches("\u000f", "ctrl+o")).isTrue();
    }

    @Test
    void matchesConfiguredCtrlTSequence() {
        assertThat(KeyMatcher.matches("\u0014", "ctrl+t")).isTrue();
    }

    @Test
    void matchesConfiguredCtrlVSequence() {
        assertThat(KeyMatcher.matches("\u0016", "ctrl+v")).isTrue();
    }

    @Test
    void matchesConfiguredCtrlZSequence() {
        assertThat(KeyMatcher.matches("\u001a", "ctrl+z")).isTrue();
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
