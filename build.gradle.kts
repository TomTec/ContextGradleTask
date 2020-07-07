plugins {
    id("org.jetbrains.intellij").version("0.4.21")
}

group = "de.tomtec.idea.plugin"
version = "1.2"

repositories {
    mavenCentral()
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij(Action {
    version = "LATEST-EAP-SNAPSHOT"
    setPlugins("gradle")
    updateSinceUntilBuild = false //set to false here, as also updates the until build to a specific version
})

tasks.patchPluginXml.configure {
    setChangeNotes("""Set since version to 2020.2 to be able to use new gradle APIs.""".trimIndent())

    setSinceBuild("202.0")
}
