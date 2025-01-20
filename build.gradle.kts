plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        implementation = JvmImplementation.VENDOR_SPECIFIC
        vendor = JvmVendorSpec.GRAAL_VM
    }
}

application {
    mainClass = "dev.monosoul.cookiemover.App"
}

graalvmNative {
    toolchainDetection = true
    binaries.all {
        buildArgs(
            "--initialize-at-build-time=kotlin.DeprecationLevel",
            "--initialize-at-build-time=dev.monosoul.cookiemover.App",
            "--initialize-at-build-time=pt.davidafsilva.apple.OSXKeychain",
            "-march=native",
            "-R:MaxHeapSize=128m",
            "--strict-image-heap",
        )
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("pt.davidafsilva.apple:jkeychain:1.1.0")

    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation(enforcedPlatform("org.jetbrains.exposed:exposed-bom:0.57.0"))
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-jdbc")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation(enforcedPlatform("org.slf4j:slf4j-bom:2.0.16"))
    implementation("org.slf4j:slf4j-jdk14")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("commons-io:commons-io:2.18.0")

    implementation(enforcedPlatform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    implementation("org.hildan.chrome:chrome-devtools-kotlin:7.0.0-1400418")
    implementation(enforcedPlatform("io.ktor:ktor-bom:3.0.3"))
    implementation("io.ktor:ktor-client-java")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-websockets")
}

tasks {
    val extensionDist by registering(Zip::class) {
        group = "distribution"
        from("extension/add-on")
        archiveBaseName = "cookiemover-extension"
        destinationDirectory = layout.buildDirectory.dir("extension-distribution")
    }

    register<Zip>("assembleFullDist") {
        group = "distribution"
        from(nativeCompile)
        from(extensionDist)
        from("extension/app/cookiemover.json")
        from("README.txt")
        archiveBaseName = "cookiemover-full"

        destinationDirectory = layout.buildDirectory.dir("full-distribution")
    }
}