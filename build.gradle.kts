plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "net.azisaba"
version = "1.3.1"
description = "EnderChest plugin for Azisaba LeonGunWar"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven("https://raw.githubusercontent.com/Rayzr522/maven-repo/master/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        isTransitive = false
    }

    implementation("me.rayzr522:jsonmessage:1.3.1")
    implementation("co.aikar:taskchain-bukkit:3.7.2")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                "project" to
                    mapOf(
                        "version" to project.version,
                        "description" to project.description,
                    ),
            )
        }
    }
}
