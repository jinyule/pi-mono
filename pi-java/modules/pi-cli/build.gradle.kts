plugins {
    application
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.the
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

description = "Command-line entrypoint and interactive/print/json/rpc mode orchestration."

val cliMainClass = "dev.pi.cli.PiCliMain"
val fatJarName = "pi-java-cli-all.jar"
val distributionDir = layout.buildDirectory.dir("dist")
val generatedScriptsDir = layout.buildDirectory.dir("generated/dist-scripts")
val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val jpackageOutputDir = layout.buildDirectory.dir("jpackage/image")
val jpackageInstallerDir = layout.buildDirectory.dir("jpackage/installer")
val releaseBundleDir = layout.buildDirectory.dir("release")
val packagedAssetsRoot = rootProject.projectDir.parentFile.resolve("packages").resolve("coding-agent")
val packagedReadme = packagedAssetsRoot.resolve("README.md")
val packagedDocs = packagedAssetsRoot.resolve("docs")
val packagedExamples = packagedAssetsRoot.resolve("examples")
val packagedChangelog = rootProject.projectDir.parentFile.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md")
val releaseVersion = project.version.toString()
val normalizedAppVersion = project.version.toString().substringBefore('-')
val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

application {
    mainClass.set(cliMainClass)
}

dependencies {
    implementation(project(":pi-agent-runtime"))
    implementation(project(":pi-session"))
    implementation(project(":pi-sdk"))
    implementation(project(":pi-tools"))
    implementation(project(":pi-extension-spi"))
    implementation(project(":pi-tui"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.picocli)
}

val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds a self-contained executable jar for pi-java CLI."
    archiveFileName.set(fatJarName)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to cliMainClass,
            "Implementation-Version" to project.version,
        )
    }

    val sourceSets = project.the<SourceSetContainer>()
    from(sourceSets.named("main").get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/INDEX.LIST",
        "module-info.class",
    )
}

val generateDistributionScripts by tasks.registering {
    group = "distribution"
    description = "Generates launcher scripts for the pi-java CLI distribution."
    val unixScript = generatedScriptsDir.map { it.file("pi-java").asFile }
    val windowsScript = generatedScriptsDir.map { it.file("pi-java.bat").asFile }
    outputs.files(unixScript, windowsScript)

    doLast {
        val unixFile = unixScript.get()
        unixFile.parentFile.mkdirs()
        unixFile.writeText(
            """
            #!/usr/bin/env sh
            set -eu
            SCRIPT_DIR=${'$'}(CDPATH= cd -- "${'$'}(dirname -- "${'$'}0")" && pwd)
            exec java -jar "${'$'}SCRIPT_DIR/../lib/$fatJarName" "${'$'}@"
            """.trimIndent() + System.lineSeparator()
        )
        unixFile.setExecutable(true)

        val windowsFile = windowsScript.get()
        windowsFile.writeText(
            """
            @echo off
            setlocal
            set SCRIPT_DIR=%~dp0
            java -jar "%SCRIPT_DIR%..\lib\$fatJarName" %*
            """.trimIndent().replace("\n", "\r\n") + "\r\n"
        )
    }
}

tasks.register<Sync>("piDistDir") {
    group = "distribution"
    description = "Assembles the runnable pi-java CLI distribution directory."
    dependsOn(fatJar, generateDistributionScripts)
    into(distributionDir)

    from(fatJar) {
        into("lib")
    }
    from(generatedScriptsDir) {
        into("bin")
        include("pi-java", "pi-java.bat")
        filePermissions {
            user.read = true
            user.write = true
            user.execute = true
            group.read = true
            group.execute = true
            other.read = true
            other.execute = true
        }
    }
    from(packagedChangelog) {
        into(".")
        rename { "CHANGELOG.md" }
    }
    from(packagedReadme) {
        into(".")
        rename { "README.md" }
    }
    from(packagedDocs) {
        into("docs")
    }
    from(packagedExamples) {
        into("examples")
    }
}

tasks.register<Zip>("piDistZip") {
    group = "distribution"
    description = "Builds a zip archive of the runnable pi-java CLI distribution."
    dependsOn("piDistDir")
    archiveBaseName.set("pi-java-cli")
    archiveVersion.set(releaseVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(distributionDir)
}

tasks.register<Sync>("prepareJpackageInput") {
    group = "distribution"
    description = "Prepares the self-contained jar input directory for jpackage."
    dependsOn(fatJar)
    into(jpackageInputDir)
    from(fatJar.flatMap { it.archiveFile })
}

tasks.register<Exec>("piAppImage") {
    group = "distribution"
    description = "Builds a native app-image for pi-java CLI via jpackage."
    dependsOn("prepareJpackageInput")

    inputs.dir(jpackageInputDir)
    outputs.dir(jpackageOutputDir.map { it.dir("pi-java") })

    doFirst {
        delete(jpackageOutputDir)
        jpackageOutputDir.get().asFile.mkdirs()
    }

    commandLine(
        "jpackage",
        "--type", "app-image",
        "--input", jpackageInputDir.get().asFile.absolutePath,
        "--dest", jpackageOutputDir.get().asFile.absolutePath,
        "--name", "pi-java",
        "--main-jar", fatJarName,
        "--main-class", cliMainClass,
        "--app-version", normalizedAppVersion,
        "--vendor", "dev.pi",
        "--description", "pi-java CLI"
    )

    doLast {
        copy {
            into(jpackageOutputDir.get().dir("pi-java"))
            from(packagedChangelog) {
                rename { "CHANGELOG.md" }
            }
            from(packagedReadme) {
                rename { "README.md" }
            }
            from(packagedDocs) {
                into("docs")
            }
            from(packagedExamples) {
                into("examples")
            }
        }
    }
}

tasks.register<Zip>("piAppImageZip") {
    group = "distribution"
    description = "Builds a zip archive of the native pi-java app-image."
    dependsOn("piAppImage")
    archiveBaseName.set("pi-java-app-image")
    archiveVersion.set(releaseVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(jpackageOutputDir.map { it.dir("pi-java") })
}

tasks.register("piInstallerExe") {
    group = "distribution"
    description = "Builds a Windows installer when WiX is available, otherwise skips with a clear preflight message."
    dependsOn("piAppImage")
    inputs.dir(jpackageOutputDir.map { it.dir("pi-java") })
    inputs.property("targetPlatform", if (isWindows) "windows" else "other")
    inputs.property("wixToolchainAvailable", wixToolchainAvailable())
    outputs.file(installerArtifactPath())

    doLast {
        if (!isWindows) {
            delete(installerArtifactPath())
            delete(jpackageInstallerDir)
            logger.lifecycle("Skipping piInstallerExe: Windows installer packaging is only available on Windows.")
            return@doLast
        }
        if (!wixToolchainAvailable()) {
            delete(installerArtifactPath())
            delete(jpackageInstallerDir)
            logger.lifecycle("Skipping piInstallerExe: WiX toolchain not found. Install wix.exe or candle.exe/light.exe to build the installer.")
            return@doLast
        }

        delete(jpackageInstallerDir)
        jpackageInstallerDir.get().asFile.mkdirs()

        val (exitCode, output) = runCommand(
            "jpackage",
            "--type", "exe",
            "--dest", jpackageInstallerDir.get().asFile.absolutePath,
            "--name", "pi-java",
            "--app-image", jpackageOutputDir.get().dir("pi-java").asFile.absolutePath,
            "--app-version", normalizedAppVersion,
            "--vendor", "dev.pi",
            "--description", "pi-java CLI",
            "--win-console",
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut"
        )
        if (exitCode != 0) {
            throw GradleException("jpackage installer build failed ($exitCode):\n$output")
        }

        val installerOutput = jpackageInstallerDir.get().asFile.listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("exe", ignoreCase = true) && it.name != installerArtifactPath().name }
            ?: throw GradleException("Expected jpackage installer output under ${jpackageInstallerDir.get().asFile}")
        installerOutput.copyTo(installerArtifactPath(), overwrite = true)
    }
}

fun sha256(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun nativeLauncherPath(): java.io.File = jpackageOutputDir.get().dir("pi-java").asFile.let { root ->
    when {
        isWindows -> root.resolve("pi-java.exe")
        isMac -> root.resolve("Contents").resolve("MacOS").resolve("pi-java")
        else -> root.resolve("bin").resolve("pi-java")
    }
}

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    return exitCode to output
}

fun windowsCommandExists(name: String): Boolean {
    if (!isWindows) {
        return false
    }
    val candidateNames = if (name.contains('.')) {
        listOf(name)
    } else {
        listOf(name, "$name.exe", "$name.cmd", "$name.bat")
    }
    return System.getenv("PATH")
        .orEmpty()
        .split(';')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { entry: String -> File(entry) }
        .filter { directory: File -> directory.isDirectory }
        .any { directory: File ->
            candidateNames.any { candidate -> directory.resolve(candidate).isFile }
        }
}

fun wixToolchainAvailable(): Boolean {
    if (!isWindows) {
        return false
    }
    return windowsCommandExists("wix.exe") || (windowsCommandExists("candle.exe") && windowsCommandExists("light.exe"))
}

fun installerArtifactPath(): java.io.File = jpackageInstallerDir.get().file("pi-java-installer-$releaseVersion.exe").asFile

val piSmokeTestFatJar by tasks.registering(Exec::class) {
    group = "verification"
    description = "Smoke-tests the runnable fat jar with --version."
    dependsOn(fatJar)
    val output = ByteArrayOutputStream()
    standardOutput = output
    errorOutput = output
    commandLine("java", "-jar", fatJar.get().archiveFile.get().asFile.absolutePath, "--version")
    doLast {
        val text = output.toString().trim()
        require(text.startsWith("pi-java ")) {
            "Fat jar smoke test failed: $text"
        }
    }
}

val piSmokeTestDistLauncher by tasks.registering(Exec::class) {
    group = "verification"
    description = "Smoke-tests the staged distribution launcher with --version."
    dependsOn("piDistDir")
    val output = ByteArrayOutputStream()
    standardOutput = output
    errorOutput = output
    val launcher = distributionDir.get().file("bin/${if (isWindows) "pi-java.bat" else "pi-java"}").asFile
    val launcherCommand = if (isWindows) {
        listOf("cmd", "/c", launcher.absolutePath, "--version")
    } else {
        listOf("sh", launcher.absolutePath, "--version")
    }
    commandLine(*launcherCommand.toTypedArray())
    doLast {
        val text = output.toString().trim()
        require(text.startsWith("pi-java ")) {
            "Distribution launcher smoke test failed: $text"
        }
    }
}

val piSmokeTestNativeLauncher by tasks.registering(Exec::class) {
    group = "verification"
    description = "Smoke-tests the native app-image launcher with --version."
    dependsOn("piAppImage")
    val output = ByteArrayOutputStream()
    standardOutput = output
    errorOutput = output
    val launcher = nativeLauncherPath()
    commandLine(launcher.absolutePath, "--version")
    doLast {
        val text = output.toString().trim()
        require(text.startsWith("pi-java ")) {
            "Native launcher smoke test failed: $text"
        }
    }
}

tasks.register("piSmokeTestArtifacts") {
    group = "verification"
    description = "Runs smoke tests for all release-shaped pi-java CLI artifacts."
    dependsOn(piSmokeTestFatJar, piSmokeTestDistLauncher, piSmokeTestNativeLauncher)
}

tasks.register("piReleaseBundle") {
    group = "distribution"
    description = "Builds a release bundle directory with versioned artifacts, checksums, and a manifest."
    dependsOn(fatJar, "piDistZip", "piAppImageZip", "piSmokeTestArtifacts", "piInstallerExe")
    inputs.files(
        fatJar.flatMap { it.archiveFile },
        tasks.named<Zip>("piDistZip").flatMap { it.archiveFile },
        tasks.named<Zip>("piAppImageZip").flatMap { it.archiveFile },
    )
    outputs.dir(releaseBundleDir)

    doLast {
        val releaseDir = releaseBundleDir.get().asFile
        delete(releaseDir)
        releaseDir.mkdirs()

        val artifactMappings = listOf(
            fatJar.get().archiveFile.get().asFile to "pi-java-cli-$releaseVersion.jar",
            tasks.named<Zip>("piDistZip").get().archiveFile.get().asFile to tasks.named<Zip>("piDistZip").get().archiveFileName.get(),
            tasks.named<Zip>("piAppImageZip").get().archiveFile.get().asFile to tasks.named<Zip>("piAppImageZip").get().archiveFileName.get(),
        ) + listOfNotNull(installerArtifactPath().takeIf { it.exists() }?.let { it to it.name })

        val bundledArtifacts = artifactMappings.map { (source, targetName) ->
            val target = releaseDir.resolve(targetName)
            source.copyTo(target, overwrite = true)
            target
        }

        packagedChangelog.copyTo(releaseDir.resolve("CHANGELOG.md"), overwrite = true)

        val checksums = bundledArtifacts.joinToString(System.lineSeparator()) { artifact ->
            "${sha256(artifact)}  ${artifact.name}"
        } + System.lineSeparator()
        releaseDir.resolve("SHA256SUMS.txt").writeText(checksums)

        val manifestArtifacts = bundledArtifacts.joinToString(",\n") { artifact ->
            """
            |    {
            |      "name": "${artifact.name}",
            |      "size": ${artifact.length()},
            |      "sha256": "${sha256(artifact)}"
            |    }
            """.trimMargin()
        }
        releaseDir.resolve("release-manifest.json").writeText(
            """
            |{
            |  "version": "$releaseVersion",
            |  "artifacts": [
            |$manifestArtifacts
            |  ]
            |}
            """.trimMargin() + System.lineSeparator()
        )
    }
}
