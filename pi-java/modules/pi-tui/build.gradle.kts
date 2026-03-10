description = "Terminal abstraction, diff rendering, overlays, and interactive UI components."

dependencies {
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.jline)
}
