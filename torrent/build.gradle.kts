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

val natives: Configuration by configurations.creating

dependencies {
    natives("org.libtorrent4j:libtorrent4j-linux:2.1.0-39")
    api("org.libtorrent4j:libtorrent4j:2.1.0-39")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Desktop native library so the engine can be exercised on the CI machine.
    testImplementation("org.libtorrent4j:libtorrent4j-linux:2.1.0-39")
}

// The desktop .so ships inside a jar; unpack it so the JVM can load it in tests.
val unpackNatives = tasks.register<Copy>("unpackNatives") {
    from(natives.map { zipTree(it) }) {
        include("**/*.so")
        eachFile {
            // Keep only the 64-bit x86 build (the CI machine).
            if (!path.contains("x86_64") && !path.contains("amd64")) exclude() else path = name
        }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("natives"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(unpackNatives)
    systemProperty("java.library.path", layout.buildDirectory.dir("natives").get().asFile.absolutePath)
    // The real-network test only runs when asked for (CI "engine" job).
    onlyIf { System.getenv("RUN_ENGINE_TEST") == "1" }
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
