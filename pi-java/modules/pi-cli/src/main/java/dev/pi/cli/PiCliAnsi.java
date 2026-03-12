package dev.pi.cli;

final class PiCliAnsi {
    private PiCliAnsi() {
    }

    static String accent(String text) {
        return style("36", text);
    }

    static String muted(String text) {
        return style("90", text);
    }

    static String bold(String text) {
        return style("1", text);
    }

    static String warning(String text) {
        return style("33", text);
    }

    static String success(String text) {
        return style("32", text);
    }

    static String error(String text) {
        return style("31", text);
    }

    private static String style(String code, String text) {
        return "\u001b[" + code + "m" + text + "\u001b[0m";
    }
}
