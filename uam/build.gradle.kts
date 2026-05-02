dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":termx-api"))
    implementation(project(":termx-core"))

    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-micronaut-pg:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")

    testRuntimeOnly("net.bytebuddy:byte-buddy:1.17.0")
    testRuntimeOnly("org.objenesis:objenesis:3.3")
}
