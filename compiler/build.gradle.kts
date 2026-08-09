import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    application
}

version = "0.1.0"

repositories {
    mavenCentral()
}

fun detectNativePlatform(): String {
    val operatingSystem = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()

    val platform = when {
        operatingSystem.contains("mac") -> "macosx"
        operatingSystem.contains("linux") -> "linux"
        operatingSystem.contains("windows") -> "windows"

        else -> error("Unsupported host operating system: $operatingSystem")
    }

    val platformArchitecture = when (architecture) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64" -> "x86_64"

        else -> error("Unsupported host architecture: $architecture")
    }

    return "$platform-$platformArchitecture"
}

val nativePlatform = providers.gradleProperty("solNativePlatform").orElse(detectNativePlatform()).get()

tasks.named<Zip>("distZip") {
    archiveClassifier.set(nativePlatform)
}

tasks.named<Tar>("distTar") {
    archiveClassifier.set(nativePlatform)
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

dependencies {
    implementation(libs.llvm)

    runtimeOnly(variantOf(libs.llvm) { classifier(nativePlatform) })
    runtimeOnly(variantOf(libs.javacpp) { classifier(nativePlatform) })

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "io.github.stardragonstudios.sol.Sol"
    applicationName = "sol"

    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

val solcStartScripts = tasks.register<CreateStartScripts>("solcStartScripts") {
    group = "application"
    description = "Generates the Sol compiler start scripts."

    mainClass.set("io.github.stardragonstudios.sol.SolCompiler")
    applicationName = "solc"

    outputDir = layout.buildDirectory
        .dir("solc-start-scripts")
        .get()
        .asFile

    classpath = files(
        tasks.named("jar"),
        configurations.runtimeClasspath
    )

    defaultJvmOpts = application.applicationDefaultJvmArgs
}

application {
    applicationDistribution.into("bin") {
        from(solcStartScripts) {
            eachFile {
                if (name == "solc") {
                    permissions {
                        unix("rwxr-xr-x")
                    }
                }
            }
        }
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "Sol",
            "Implementation-Version" to project.version
        )
    }
}

distributions {
    main {
        distributionBaseName = "sol"
    }
}

application {
    applicationDistribution.from(
        file("../README.md"),
        file("../LICENSE")
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")

    filter {
        excludeTestsMatching("io.github.stardragonstudios.sol.SolDistributionSmokeTest")
    }
}

val distributionSmokeTest = tasks.register<Test>("distributionSmokeTest") {
    group = "verification"
    description = "Smoke-tests the installed Sol distribution."

    dependsOn("installDist")
    useJUnitPlatform()

    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")

    systemProperty("sol.distributionDir", layout.buildDirectory.dir("install/sol").get().asFile.absolutePath)
    systemProperty("sol.expectedVersion", project.version.toString())

    filter {
        includeTestsMatching("io.github.stardragonstudios.sol.SolDistributionSmokeTest")
    }
}
