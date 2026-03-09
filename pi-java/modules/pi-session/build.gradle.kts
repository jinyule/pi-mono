description = "Session JSONL persistence, replay, tree navigation, and settings layering."

dependencies {
    api(project(":pi-ai"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

