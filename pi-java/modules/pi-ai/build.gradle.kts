description = "Unified LLM provider abstractions, model discovery, and streaming event normalization."

dependencies {
    api(platform(libs.aws.sdk.bom))
    api("software.amazon.awssdk:auth")
    api(libs.aws.sdk.bedrock.runtime)
    api("software.amazon.awssdk:netty-nio-client")
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.dataformat.yaml)
}
