dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":termx-api"))
    implementation(project(":termx-core"))
    implementation(project(":terminology"))

    implementation("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")

    implementation("com.kodality.kefhir:fhir-rest:${rootProject.extra["kefhirVersion"]}") { isChanging = true }
    implementation("com.kodality.kefhir:validation-profile:${rootProject.extra["kefhirVersion"]}") { isChanging = true }

    implementation("commons-io:commons-io:2.16.1")
    implementation("org.ogce:xpp3:1.1.6") {
        exclude(group = "junit")
    }

    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-http-server")

    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.validation:6.0.1")
    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.convertors:6.0.1")
    implementation("ca.uhn.hapi.fhir:org.hl7.fhir.r5:6.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
}
