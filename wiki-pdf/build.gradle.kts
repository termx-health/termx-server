dependencies {
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.spring:micronaut-spring-annotation")

    implementation(project(":wiki"))
    implementation(project(":termx-api"))
    implementation(project(":termx-core"))
    implementation(project(":task"))
    implementation(project(":terminology"))

    implementation("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-db-bean:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-micronaut:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-micronaut-pg:${rootProject.extra["commonsMicronautVersion"]}")
    implementation("com.kodality.commons:commons-tenant:${rootProject.extra["commonsMicronautVersion"]}")

    implementation("commons-io:commons-io:2.16.1")

    implementation("io.micronaut.spring:micronaut-spring")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-http-server")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    
    implementation ("commons-validator:commons-validator:1.7")
    implementation("com.univocity:univocity-parsers:2.9.1")

    // Markdown to HTML parser
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")

    // pdf export — Flying Saucer + OpenPDF for HTML->PDF (LGPL/MPL), JTidy to
    // normalise the rendered HTML into the well-formed XHTML Flying Saucer needs.
    // Matches the emr commons-reportoria-service pipeline; replaces iText (AGPL).
    implementation("org.xhtmlrenderer:flying-saucer-pdf:9.4.1")
    implementation("net.sf.jtidy:jtidy:r938")

    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")

}
