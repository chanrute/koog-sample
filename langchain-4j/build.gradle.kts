plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }
}

dependencies {
    // LangChain4j Core - v1.3.0 (Latest Stable)
    implementation("dev.langchain4j:langchain4j-core:1.3.0")
    implementation("dev.langchain4j:langchain4j:1.3.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.3.0")
    implementation("dev.langchain4j:langchain4j-embeddings:1.3.0-beta9")
    
    // LangGraph4j for workflow management
    implementation("org.bsc.langgraph4j:langgraph4j-langchain4j:1.6.0-rc4")
    implementation("org.bsc.langgraph4j:langgraph4j-core:1.6.0-rc4")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // HTTP Client for PDF download
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    
    // PDF Processing
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    
    // Environment variables
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    
    // SLF4J Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass.set("org.example.MainKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}