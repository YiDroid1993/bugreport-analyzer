plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.yidroid.buganalyzer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("commons-io:commons-io:2.13.0")
    implementation("org.apache.commons:commons-compress:1.24.0")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2023.2.5") // Target Android Studio Iguana / Jellyfish base
    type.set("IC") // IntelliJ Community
    plugins.set(listOf("com.intellij.java"))
    updateSinceUntilBuild.set(false) // 关键点：禁用自动更新 since/until 属性
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        // 显式设为 null 并禁用自动生成的逻辑
        untilBuild.set(null as String?)
    }

    // 强制干预属性
    withType<org.jetbrains.intellij.tasks.PatchPluginXmlTask> {
        untilBuild.set(null as String?)
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
