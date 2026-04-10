plugins {
    id("prism.library")
}

dependencies {
    implementation(platform(libs.jackson.bom))

    implementation(libs.jackson.annotations)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}
