plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.5.3")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.6")
}
