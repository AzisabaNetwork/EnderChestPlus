plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.5.1"
}

group = "net.azisaba"
version = "1.3.1"
description = "EnderChest plugin for Azisaba LeonGunWar"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://raw.githubusercontent.com/Rayzr522/maven-repo/master/")
    maven("https://jitpack.io/")
    maven("https://repo.aikar.co/content/groups/aikar/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("me.rayzr522:jsonmessage:1.3.1")
    implementation("com.github.KevinPriv:MojangAPI:1.0")
    implementation("co.aikar:taskchain-bukkit:3.7.2")
    implementation("commons-lang:commons-lang:2.6")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveFileName.set("EnderChestPlus.jar")
        archiveClassifier.set("")
        relocate("co.aikar.taskchain", "jp.azisaba.lgw.ecplus.depends.taskchain")
        relocate("com.zaxxer.hikari", "jp.azisaba.lgw.ecplus.depends.hikari")
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
