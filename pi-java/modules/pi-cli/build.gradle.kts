plugins {
    application
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.the

description = "Command-line entrypoint and interactive/print/json/rpc mode orchestration."

val cliMainClass = "dev.pi.cli.PiCliMain"
val fatJarName = "pi-java-cli-all.jar"
val distributionDir = layout.buildDirectory.dir("dist")
val generatedScriptsDir = layout.buildDirectory.dir("generated/dist-scripts")
val packagedChangelog = rootProject.projectDir.parentFile.resolve("packages").resolve("coding-agent").resolve("CHANGELOG.md")

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
}

tasks.register<Zip>("piDistZip") {
    group = "distribution"
    description = "Builds a zip archive of the runnable pi-java CLI distribution."
    dependsOn("piDistDir")
    archiveBaseName.set("pi-java-cli")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(distributionDir)
}
