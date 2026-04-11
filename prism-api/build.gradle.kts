plugins {
    id("prism.library")
}

dependencies {
    api(platform(libs.jackson.bom))

    api(libs.jackson.annotations)
    api(libs.jackson.databind)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}
