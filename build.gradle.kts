plugins {
    `maven-publish`
    id("org.owasp.dependencycheck") version "8.4.2"
    id("io.micronaut.minimal.library") version "4.6.2"
    id("io.micronaut.minimal.application") version "4.6.2"
    id("com.gradleup.shadow") version "9.3.2" apply false
}

group = "org.termx"

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "groovy")
    apply(plugin = "org.owasp.dependencycheck")

    version = rootProject.version
    group = rootProject.group

    repositories {
        mavenCentral()
        maven { url = uri("https://kexus.kodality.com/repository/maven-public/") }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        withSourcesJar()
    }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "io.micronaut.minimal.library")

    configure<io.micronaut.gradle.MicronautExtension> {
        testRuntime("spock2")
        processing {
            incremental(true)
            annotations("com.kodality.termx.*", "org.termx.*")
        }
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = project.name
                from(components["java"])
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/termx-health/termx-server")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
