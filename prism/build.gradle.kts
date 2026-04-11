import com.google.protobuf.gradle.id

plugins {
    id("prism.service")
    id("com.google.protobuf")
}

dependencies {
    implementation(project(":prism-api"))

    implementation(platform(libs.jackson.bom))
    implementation(platform(libs.testcontainers.bom))
    implementation(platform(libs.junit.bom))

    implementation(libs.helidon.webserver)
    implementation(libs.helidon.webserver.cors)
    implementation(libs.helidon.http.media.jackson)
    implementation(libs.helidon.webclient.grpc)

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

    implementation(libs.protobuf.java)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.api)

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
    testImplementation(libs.awaitility)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.grpc.testing)

    testFixturesImplementation(platform(libs.testcontainers.bom))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.assertj.core)
    testFixturesImplementation(libs.mockito.core)
    testFixturesImplementation(libs.testcontainers.junit5)
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.pgjdbc)
    testFixturesImplementation(libs.protobuf.java)
    testFixturesImplementation(platform(libs.jackson.bom))
    testFixturesImplementation(libs.jackson.databind)

    "integrationTestImplementation"(libs.testcontainers.junit5)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.helidon.webserver.grpc)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                id("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}
