// MDN-SAM — Steal a Mineling — Gameplay (Paper)
plugins { `java-library`; id("com.gradleup.shadow") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34"); annotationProcessor("org.projectlombok:lombok:1.18.34")
    implementation(project(":mdn-api")); implementation(project(":mdn-core"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.15"); compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}
tasks.shadowJar { archiveClassifier.set(""); relocate("com.fasterxml", "net.minedrop.libs.jackson") }
tasks.build { dependsOn(tasks.shadowJar) }
tasks.processResources { filesMatching("plugin.yml") { expand("mainClass" to "net.minedrop.sam.SAMPaperPlugin", "version" to project.version) } }
