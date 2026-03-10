package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

final class ExtensionTestJars {
    private ExtensionTestJars() {
    }

    static Path compileJar(Path tempDir, String className, String source) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();

        var sourceRoot = tempDir.resolve("src-" + sanitize(className));
        var classesRoot = tempDir.resolve("classes-" + sanitize(className));
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classesRoot);

        var relativeSourcePath = className.replace('.', '/') + ".java";
        var sourcePath = sourceRoot.resolve(relativeSourcePath);
        Files.createDirectories(sourcePath.getParent());
        Files.writeString(sourcePath, source, StandardCharsets.UTF_8);

        var exitCode = compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            classesRoot.toString(),
            sourcePath.toString()
        );
        assertThat(exitCode).isZero();

        var jarPath = tempDir.resolve(sanitize(className) + ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (var paths = Files.walk(classesRoot)) {
                for (var classFile : paths.filter(Files::isRegularFile).toList()) {
                    var entryName = classesRoot.relativize(classFile).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(entryName));
                    output.write(Files.readAllBytes(classFile));
                    output.closeEntry();
                }
            }

            var serviceEntry = "META-INF/services/" + PiExtension.class.getName();
            output.putNextEntry(new JarEntry(serviceEntry));
            output.write((className + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        return jarPath;
    }

    private static String sanitize(String className) {
        return className.replace('.', '-');
    }
}
