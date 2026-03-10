package dev.pi.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class SupportedImageMimeTypes {
    private SupportedImageMimeTypes() {
    }

    public static String detectFromFile(Path filePath) throws IOException {
        var bytes = Files.readAllBytes(filePath);
        return detect(bytes);
    }

    public static String detect(byte[] bytes) {
        if (bytes.length >= 8 && startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (bytes.length >= 3 && startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
            return "image/jpeg";
        }
        if (bytes.length >= 6 && (startsWith(bytes, "GIF87a".getBytes()) || startsWith(bytes, "GIF89a".getBytes()))) {
            return "image/gif";
        }
        if (bytes.length >= 12
            && startsWith(bytes, "RIFF".getBytes())
            && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes())) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (var index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (var index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
