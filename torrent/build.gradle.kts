import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.libtorrent4j:libtorrent4j:2.1.0-39")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Desktop native library so the engine can be exercised on the CI machine.
    testImplementation("org.libtorrent4j:libtorrent4j-linux:2.1.0-39")
}

tasks.test {
    useJUnitPlatform()
    // The real-network test only runs when asked for (CI "engine" job).
    onlyIf { System.getenv("RUN_ENGINE_TEST") == "1" }
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
