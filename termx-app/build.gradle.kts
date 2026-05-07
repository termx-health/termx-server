plugins {
    id("io.micronaut.minimal.library") apply false
    id("io.micronaut.minimal.application")
    id("com.gradleup.shadow")
}

dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":termx-api"))
    implementation(project(":termx-core"))
    implementation(project(":terminology"))
    implementation(project(":snomed"))
    implementation(project(":ucum"))
    implementation(project(":observation-definition"))
    implementation(project(":wiki"))
    implementation(project(":task-taskforge"))
    implementation(project(":modeler"))
    implementation(project(":implementation-guide"))
    implementation(project(":uam"))
    implementation(project(":bob"))

    implementation(project(":edition-int"))
    implementation(project(":edition-est"))
    implementation(project(":edition-uzb"))

    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db-bean:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-sequence:${rootProject.extra["commonsVersion"]}")

    implementation("com.kodality.kefhir:fhir-rest:${rootProject.extra["kefhirVersion"]}") { isChanging = true }
    implementation("com.kodality.kefhir:validation-profile:${rootProject.extra["kefhirVersion"]}") { isChanging = true }
    implementation("com.kodality.kefhir:openapi:${rootProject.extra["kefhirVersion"]}") { isChanging = true }

    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.liquibase:micronaut-liquibase")
    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut.data:micronaut-data-spring-jdbc")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("io.micronaut.openapi:micronaut-openapi")
    implementation("io.micronaut.email:micronaut-email-javamail")

    implementation("com.fasterxml.jackson.core:jackson-core:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    implementation("io.projectreactor:reactor-core:3.6.7")
    implementation("io.swagger.core.v3:swagger-annotations")
    implementation("com.auth0:java-jwt:4.0.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    runtimeOnly("io.micronaut:micronaut-jackson-databind")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    testImplementation("io.micronaut.test:micronaut-test-spock:3.9.2")
    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0") {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:spock")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("commons-io:commons-io:2.16.1")
}

application {
    mainClass.set("org.termx.TermxApplication")
}

micronaut {
    runtime("netty")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    isZip64 = true
    mergeServiceFiles {
        exclude("META-INF/services/org.xmlpull.v1.XmlPullParserFactory")
    }
    
    // CRITICAL: Allow duplicate BeanProcessor.class files during build
    // They will be cleaned up in the removeDuplicateBeanProcessor task
    duplicatesStrategy = DuplicatesStrategy.WARN
    
    doLast {
        // Post-process: Remove duplicate BeanProcessor AND GenerousBeanProcessor, keep only vendored versions
        val jarFile = archiveFile.get().asFile
        val vendoredModule = project.project(":kodality-commons:commons-db")
        val vendoredClassDir = vendoredModule.layout.buildDirectory.dir("classes/java/main").get().asFile
        
        // Remove BOTH duplicates
        listOf("BeanProcessor.class", "GenerousBeanProcessor.class").forEach { className ->
            println("🔧 Removing duplicate ${className}...")
            val removeProcess = ProcessBuilder("zip", "-d", jarFile.absolutePath, "org/apache/commons/dbutils/${className}")
                .inheritIO()
                .start()
            if (removeProcess.waitFor() != 0) {
                throw GradleException("Failed to remove duplicate ${className}")
            }
            
            // Re-add ONLY the vendored version
            val vendoredClass = File(vendoredClassDir, "org/apache/commons/dbutils/${className}")
            if (vendoredClass.exists()) {
                val addProcess = ProcessBuilder("zip", "-u", jarFile.absolutePath, "org/apache/commons/dbutils/${className}")
                    .directory(vendoredClassDir)
                    .inheritIO()
                    .start()
                if (addProcess.waitFor() != 0) {
                    throw GradleException("Failed to add vendored ${className}")
                }
                println("✅ Vendored ${className} restored")
            } else {
                throw GradleException("Vendored ${className} not found at ${vendoredClass.absolutePath}")
            }
        }
        println("✅ All duplicate Apache dbutils classes resolved (using vendored versions with registerColumnHandler)")
    }
}

tasks.named<JavaExec>("run") {
    if (project.hasProperty("debug")) {
        jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=${project.property("debug")},server=y,suspend=n")
    }
    if (project.hasProperty("dev")) {
        jvmArgs = (jvmArgs ?: listOf()) + listOf("-Dauth.dev.allowed=true", "-Dmicronaut.environments=dev,local")
    }
    // QA / migration testing: override the yupi default session's privilege set.
    // Example: ./gradlew :termx-app:run -Pdev -PyupiPrivileges='*.*.view'
    // See YupiSessionProvider Javadoc for common preset values.
    if (project.hasProperty("yupiPrivileges")) {
        jvmArgs = (jvmArgs ?: listOf()) + listOf("-Dauth.dev.yupi.privileges=${project.property("yupiPrivileges")}")
    }
}
