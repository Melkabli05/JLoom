plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.6" apply false
}

group = "com.jloom"
version = "0.2.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.jline:jline:3.30.13")

    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.postgresql:postgresql:42.7.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
}

application {
    mainClass.set("com.jloom.Main")
    // JLine's FFM terminal provider needs native access; without this the JVM prints
    // an "illegal native access" warning to stderr on every single invocation.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters"))
}

// Module catalog resources under src/main/resources/modules/*/files include dotfiles
// (.gitignore) meant to be copied verbatim into generated projects. Ant's DirectoryScanner
// silently drops those from every Copy-family task (processResources included) unless removed
// here, before the file-tree snapshotter walks the resources.
val removeAntExcludes = tasks.register("removeAntExcludes") {
    org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitignore")
    org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitattributes")
}
tasks.named("processResources") { dependsOn(removeAntExcludes) }