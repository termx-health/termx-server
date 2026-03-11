dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    api(project(":termx-api"))
    api(project(":termx-core"))

    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db-bean:${rootProject.extra["commonsVersion"]}")

    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")

    implementation("io.minio:minio:8.5.10")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
    implementation("com.squareup.okio:okio:3.6.0")
}
