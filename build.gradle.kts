plugins {
    id("java")
    id("org.jetbrains.intellij").version("1.0")
}

group = "de.tomtec.idea.plugin"
version = "1.3"

repositories {
    mavenCentral()
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("LATEST-EAP-SNAPSHOT")
    plugins.add("gradle")
    updateSinceUntilBuild.set(false) //set to false here, as also updates the until build to a specific version
}

tasks.patchPluginXml.configure {
    changeNotes.set("""Set since version to 2021.2 due to usage of new APIs instead of deprecated ones.""".trimIndent())
    sinceBuild.set("212.0")
}
