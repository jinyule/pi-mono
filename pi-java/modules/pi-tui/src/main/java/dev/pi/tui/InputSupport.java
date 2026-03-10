package dev.pi.tui;

final class InputSupport {
    private InputSupport() {
    }

    static boolean containsControlCharacters(String text) {
        for (var index = 0; index < text.length(); ) {
            var codePoint = text.codePointAt(index);
            if (codePoint < 32 || codePoint == 0x7f || (codePoint >= 0x80 && codePoint <= 0x9f)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    static boolean isWhitespace(String text) {
        return Character.isWhitespace(text.codePointAt(0));
    }

    static boolean isPunctuation(String text) {
        var type = Character.getType(text.codePointAt(0));
        return switch (type) {
            case Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
