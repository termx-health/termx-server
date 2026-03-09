plugins {
    `maven-publish`
    id("org.owasp.dependencycheck") version "8.4.2"
    id("io.micronaut.minimal.library") version "4.6.2"
    id("io.micronaut.minimal.application") version "4.6.2"
    id("com.gradleup.shadow") version "9.3.2" apply false
    id("com.github.spotbugs") version "6.4.8" apply false
    pmd
}

group = "org.termx"

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "groovy")
    apply(plugin = "org.owasp.dependencycheck")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "pmd")

    // SpotBugs: run only via manual task spotbugsCheck (not part of check) to speed up CI/Docker builds
    extensions.findByType<com.github.spotbugs.snom.SpotBugsExtension>()?.runOnCheck?.set(false)

    version = rootProject.version
    group = rootProject.group

    repositories {
        mavenCentral()
        maven { url = uri("https://kexus.kodality.com/repository/maven-public/") }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")
        
        implementation(platform("org.apache.groovy:groovy-bom:5.0.3"))
        testImplementation(platform("org.apache.groovy:groovy-bom:5.0.3"))
        testImplementation(platform("org.spockframework:spock-bom:2.4-groovy-5.0"))
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

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports.create("html") {
            required.set(true)
        }
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
        excludeFilter.set(file("${rootProject.projectDir}/config/spotbugs/exclude.xml"))
        ignoreFailures = false
        extraArgs = listOf("-nested:false", "-auxclasspath", configurations.getByName("compileClasspath").asPath)
    }

    tasks.withType<Pmd>().configureEach {
        ruleSetFiles = files("${rootProject.projectDir}/config/pmd/ruleset.xml")
        ruleSets = listOf()
        ignoreFailures = true
        reports {
            xml.required.set(false)
            html.required.set(true)
        }
    }
}

// Manual SpotBugs verification: ./gradlew spotbugsCheck (not run during check/CI)
tasks.register("spotbugsCheck") {
    group = "verification"
    description = "Runs SpotBugs on all projects. Use manually; not part of check or CI."
}
allprojects {
    afterEvaluate {
        rootProject.tasks.named("spotbugsCheck").configure {
            dependsOn(tasks.withType<com.github.spotbugs.snom.SpotBugsTask>())
        }
    }
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "io.micronaut.minimal.library")

    configure<io.micronaut.gradle.MicronautExtension> {
        testRuntime("spock2")
        processing {
            incremental(true)
            annotations("org.termx.*")
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
