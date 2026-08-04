// =============================================================================
// MDN-Bridge — Security Foundation (Velocity + Paper)
// =============================================================================

plugins {
    `java-library`
    id("com.gradleup.shadow")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation(project(":mdn-api"))

    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.fasterxml", "net.minedrop.libs.jackson")
    relocate("com.zaxxer.hikari", "net.minedrop.libs.hikari")
    relocate("redis.clients.jedis", "net.minedrop.libs.jedis")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("plugin.yml") {
        expand(
            "pluginName" to "MDN-Bridge",
            "mainClass" to "net.minedrop.bridge.paper.BridgePaperPlugin",
            "apiVersion" to "1.21",
            "version" to project.version,
            "dependsOn" to "",
            "softDependsOn" to "",
        )
    }
    filesMatching("velocity-plugin.json") {
        expand(
            "pluginName" to "mdn-bridge",
            "mainClass" to "net.minedrop.bridge.velocity.BridgeVelocityPlugin",
            "version" to project.version,
            "dependsOn" to "",
        )
    }
}
