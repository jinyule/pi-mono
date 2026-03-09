description = "Command-line entrypoint and interactive/print/json/rpc mode orchestration."

dependencies {
    implementation(project(":pi-agent-runtime"))
    implementation(project(":pi-session"))
    implementation(project(":pi-tools"))
    implementation(project(":pi-extension-spi"))
    implementation(project(":pi-tui"))
    implementation(libs.picocli)
}

