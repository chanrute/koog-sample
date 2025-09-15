plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    // iOS framework configuration
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KoogChat"
            isStatic = true
        }
    }
    
    // JVM target for testing
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                // Koog dependencies for KMP
                implementation("ai.koog:prompt-executor-openai-client:0.4.1")
                implementation("ai.koog:prompt-llm:0.4.1")
                implementation("ai.koog:prompt-model:0.4.1")

                // Kotlinx serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                // Ktor client for HTTP requests
                implementation("io.ktor:ktor-client-core:3.0.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
            }
        }
        
        iosMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.0.2")
            }
        }
        
        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.2")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// iOS Framework configuration
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        if (this is org.jetbrains.kotlin.gradle.plugin.mpp.Framework) {
            baseName = "KoogChat"
            isStatic = true
        }
    }
}