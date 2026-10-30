plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }

// Pure JVM smoke check that runs without Android emulator and verifies the JSON fixture.
tasks.register<JavaExec>("smokeTest") {
    dependsOn(tasks.named("testClasses"))
    mainClass.set("dev.matchday.core.SmokeTestKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
    args("feeds/example.json")
}
