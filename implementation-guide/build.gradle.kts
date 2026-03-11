dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":termx-api"))
    implementation(project(":termx-core"))

    implementation("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db-bean:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")

    implementation("commons-io:commons-io:2.16.1")

    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-http-server")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
}
