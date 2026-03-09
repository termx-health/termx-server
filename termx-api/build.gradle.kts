dependencies {
    api("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}") { isChanging = true }
    api("com.kodality.commons:commons-model:${rootProject.extra["commonsVersion"]}") { isChanging = true }
    api("io.micronaut:micronaut-core:${rootProject.extra["micronautVersion"]}")
    api("io.micronaut:micronaut-context:${rootProject.extra["micronautVersion"]}")
}
