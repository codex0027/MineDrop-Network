// =============================================================================
// MDN-Core — Network Heartbeat (Velocity + Paper)
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
    implementation(project(":mdn-bridge"))

    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("redis.clients:jedis:5.1.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

tasks.shadowJar {
    dependsOn(":mdn-bridge:shadowJar")
    archiveClassifier.set("")
    relocate("com.fasterxml", "net.minedrop.libs.jackson")
    relocate("com.zaxxer.hikari", "net.minedrop.libs.hikari")
    relocate("redis.clients.jedis", "net.minedrop.libs.jedis")
    relocate("org.slf4j", "net.minedrop.libs.slf4j")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "pluginName" to "MDN-Core",
            "mainClass" to "net.minedrop.core.paper.CorePaperPlugin",
            "apiVersion" to "1.21",
            "version" to project.version,
            "dependsOn" to "MDN-Bridge",
            "softDependsOn" to "Vault, PlaceholderAPI, LuckPerms",
        )
    }
    filesMatching("velocity-plugin.json") {
        expand(
            "pluginName" to "mdn-core",
            "mainClass" to "net.minedrop.core.velocity.CoreVelocityPlugin",
            "version" to project.version,
            "dependsOn" to "mdn-bridge",
        )
    }
}
