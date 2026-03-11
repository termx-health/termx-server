dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":termx-api"))

    implementation("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db-bean:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-micronaut-pg:${rootProject.extra["commonsMicronautVersion"]}")

    implementation("com.kodality.commons:commons-util-spring:${rootProject.extra["commonsMicronautVersion"]}")

    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-http-server")
    implementation("io.micronaut.email:micronaut-email-javamail")
    implementation("org.eclipse.angus:angus-mail:2.0.3")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("com.univocity:univocity-parsers:2.9.1")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    implementation("commons-io:commons-io:2.16.1")
    implementation("io.github.furstenheim:copy_down:1.1")
    implementation("org.commonmark:commonmark:0.22.0")

    implementation("com.kodality.kefhir:kefhir-core:${rootProject.extra["kefhirVersion"]}") { isChanging = true }
    compileOnly("com.kodality.kefhir:fhir-rest:${rootProject.extra["kefhirVersion"]}") { isChanging = true }
    implementation("com.kodality.zmei:zmei-fhir:${rootProject.extra["zmeiVersion"]}") { isChanging = true }
    implementation("com.kodality.zmei:zmei-fhir-client:${rootProject.extra["zmeiVersion"]}") { isChanging = true }
    implementation("com.kodality.zmei:zmei-fhir-jackson:${rootProject.extra["zmeiVersion"]}") { isChanging = true }

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:${rootProject.extra["jacksonVersion"]}")

    testImplementation("io.micronaut.test:micronaut-test-spock")
    testImplementation("org.spockframework:spock-core") {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
}
