plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    application
}

application {
    mainClass.set("MainKt")
}

group = "com.ala.agentkoogtest"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:0.8.0")
    implementation("io.ktor:ktor-client-cio:3.2.2")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
