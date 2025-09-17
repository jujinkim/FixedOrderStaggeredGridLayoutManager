plugins {
    // versions defined in gradle.properties
}

allprojects {
    group = "kim.jujin"
    version = property("VERSION_NAME") as String
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

