plugins {
    id("net.neoforged.gradle.userdev") version "7.0.171"
}

val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val mod_group_id: String by project
val neo_version: String by project
val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version_range: String by project
val loader_version_range: String by project

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

runs {
    configureEach {
        systemProperty("forge.logging.markers", "REGISTRIES")
        systemProperty("forge.logging.console.level", "debug")
        modSource(project.sourceSets.main.get())
    }
    create("client") {}
    create("server") {}
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.neoforged:neoforge:${neo_version}")
    // Cobblemon - compile-only (présent dans le modpack au runtime)
    compileOnly(files("/mnt/c/Users/natto/curseforge/minecraft/Instances/create x neoforge/mods/Cobblemon-neoforge-1.8.4+1.21.1.jar"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "minecraft_version"       to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version"             to neo_version,
        "neo_version_range"       to neo_version_range,
        "loader_version_range"    to loader_version_range,
        "mod_id"                  to mod_id,
        "mod_name"                to mod_name,
        "mod_version"             to mod_version,
        "mod_group_id"            to mod_group_id,
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
