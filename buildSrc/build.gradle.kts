plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.5.3")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
}
