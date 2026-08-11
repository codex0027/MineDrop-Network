// =============================================================================
// MDN-Auth — Authentication (Velocity)
// =============================================================================

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

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
    implementation(project(":mdn-core"))

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    compileOnly("org.slf4j:slf4j-api:2.0.13")  // Velocity provides SLF4J — do NOT bundle

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
    // Exclude bridge/core classes — they're loaded from their own JARs at runtime.
    exclude("net/minedrop/bridge/**")
    exclude("net/minedrop/core/**")
}

tasks.jar {
    archiveClassifier.set("original")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }

// ── Build-time signature.json generation ──
val generateSignature by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val hash = computeJarHash(shadowJarFile)
        val sigFile = layout.buildDirectory.file("generated/signature/signature.json").get().asFile
        sigFile.parentFile.mkdirs()
        val json = """{"plugin_id":"mdn-auth","version":"${project.version}","build_hash":"$hash","timestamp":${System.currentTimeMillis()},"gradle_build":"${project.name}"}"""
        sigFile.writeText(json)
        project.exec {
            commandLine("python3", "-c", """
import zipfile, os
sig = '${sigFile.absolutePath}'
jar = '${shadowJarFile.absolutePath}'
with zipfile.ZipFile(jar, 'a', zipfile.ZIP_STORED) as zf:
    zf.writestr('signature.json', open(sig).read())
""".trimIndent())
        }
        println("  [signature] mdn-auth: $hash")
    }
}
tasks.build { dependsOn(generateSignature) }
tasks.shadowJar { finalizedBy(generateSignature) }

fun computeJarHash(jar: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val entries = mutableListOf<Pair<String, ByteArray>>()
    ZipInputStream(jar.inputStream()).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (entry.isDirectory) continue
            if (entry.name == "signature.json") continue
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var len: Int
            while (zis.read(buf).also { len = it } > 0) bos.write(buf, 0, len)
            zis.closeEntry()
            entries.add(entry.name to bos.toByteArray())
        }
    }
    entries.sortBy { it.first }
    for ((name, data) in entries) {
        digest.update(name.toByteArray(Charsets.UTF_8))
        digest.update(data)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
