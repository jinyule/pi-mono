import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "pi-java"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

listOf(
    "pi-ai",
    "pi-agent-runtime",
    "pi-session",
    "pi-tools",
    "pi-extension-spi",
    "pi-tui",
    "pi-cli",
    "pi-sdk",
).forEach { moduleName ->
    include(moduleName)
    project(":$moduleName").projectDir = file("modules/$moduleName")
}

