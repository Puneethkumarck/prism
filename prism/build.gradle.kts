plugins {
    id("prism.service")
}

dependencies {
    implementation(project(":prism-api"))

    implementation(platform(libs.jackson.bom))
    implementation(platform(libs.testcontainers.bom))
    implementation(platform(libs.junit.bom))

    implementation(libs.helidon.webserver)
    implementation(libs.helidon.webserver.cors)
    implementation(libs.helidon.http.media.jackson)

    implementation(libs.avaje.inject)
    annotationProcessor(libs.avaje.inject.generator)

    implementation(libs.pgjdbc)
    implementation(libs.hikari)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    implementation(libs.resilience4j.retry)

    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)

    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    implementation(libs.jansi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testFixturesCompileOnly(libs.lombok)
    testFixturesAnnotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)

    testFixturesImplementation(platform(libs.testcontainers.bom))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.mockito.core)
    testFixturesImplementation(libs.testcontainers.junit5)
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.pgjdbc)

    "integrationTestImplementation"(libs.testcontainers.junit5)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
}
