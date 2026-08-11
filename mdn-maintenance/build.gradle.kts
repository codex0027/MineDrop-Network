// MDN-Maintenance — Whitelist, Restarts, Emergency (Velocity + Paper)
plugins { `java-library`; id("com.gradleup.shadow") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34"); annotationProcessor("org.projectlombok:lombok:1.18.34")
    implementation(project(":mdn-api")); implementation(project(":mdn-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT"); annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
}
tasks.shadowJar { archiveClassifier.set(""); relocate("com.fasterxml", "net.minedrop.libs.jackson") }
tasks.build { dependsOn(tasks.shadowJar) }
tasks.processResources {
    filesMatching("plugin.yml") { expand("mainClass" to "net.minedrop.maintenance.MaintenancePaperPlugin", "version" to project.version) }
}
