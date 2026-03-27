plugins {
    groovy
    id("io.micronaut.minimal.library") apply false
    id("io.micronaut.minimal.application")
}

dependencies {
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    testImplementation(project(":termx-app"))
    testImplementation(project(":terminology"))
    testImplementation(project(":snomed"))

    testImplementation("io.micronaut.data:micronaut-data-spring-jdbc")
    testImplementation("io.micronaut.email:micronaut-email-javamail")
    testImplementation("org.testcontainers:spock")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.micronaut:micronaut-inject-groovy")
    testImplementation("io.micronaut:micronaut-jackson-databind")
    testImplementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")
    testImplementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")

    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

application {
    mainClass.set("org.termx.TermxTestApplication")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

micronaut {
    runtime("netty")
}
