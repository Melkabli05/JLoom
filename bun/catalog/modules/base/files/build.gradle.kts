plugins {
    id("java")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "{{package}}"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Not spring-boot-starter-test — Boot 4's modularization moved MockMvc/@AutoConfigureMockMvc
    // and the new RestTestClient out of the generic test starter into this web-flavored one
    // (which still transitively includes the generic starter, so nothing is lost). Pairs with
    // spring-boot-starter-webmvc above the same way the modularization pairs every web starter
    // with its own -test counterpart.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // ArchUnit — design §8: the base module ships layering-rules tests so hand-edits don't silently
    // break assumptions that future `jloom add` modules rely on (e.g. JPA annotations staying in
    // infrastructure, controllers not bypassing services to hit repositories directly).
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

tasks.test {
    useJUnitPlatform()
}