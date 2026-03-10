package dev.pi.extension.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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

    static Path compileProjectJar(Path tempDir, Path projectDir, String jarName) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler").isNotNull();

        var classesRoot = tempDir.resolve("classes-" + sanitize(jarName));
        if (Files.exists(classesRoot)) {
            deleteRecursively(classesRoot);
        }
        Files.createDirectories(classesRoot);

        var sourceRoot = projectDir.resolve("src/main/java");
        var sourceFiles = Files.walk(sourceRoot)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .map(Path::toString)
            .toList();
        assertThat(sourceFiles).isNotEmpty();

        var compileArgs = new java.util.ArrayList<String>();
        compileArgs.add("-classpath");
        compileArgs.add(System.getProperty("java.class.path"));
        compileArgs.add("-d");
        compileArgs.add(classesRoot.toString());
        compileArgs.addAll(sourceFiles);

        var exitCode = compiler.run(
            null,
            null,
            null,
            compileArgs.toArray(String[]::new)
        );
        assertThat(exitCode).isZero();

        var resourcesRoot = projectDir.resolve("src/main/resources");
        if (Files.exists(resourcesRoot)) {
            try (var resources = Files.walk(resourcesRoot)) {
                for (var resource : resources.filter(Files::isRegularFile).toList()) {
                    var target = classesRoot.resolve(resourcesRoot.relativize(resource).toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        var jarPath = tempDir.resolve(sanitize(jarName) + ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (var paths = Files.walk(classesRoot)) {
                for (var file : paths.filter(Files::isRegularFile).toList()) {
                    var entryName = classesRoot.relativize(file).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(entryName));
                    output.write(Files.readAllBytes(file));
                    output.closeEntry();
                }
            }
        }

        return jarPath;
    }

    private static void deleteRecursively(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String sanitize(String className) {
        return className.replace('.', '-');
    }
}
