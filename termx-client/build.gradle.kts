dependencies {
    implementation(project(":termx-api"))
    implementation("com.kodality.commons:commons-http-client:${rootProject.extra["commonsVersion"]}")
    implementation("com.kodality.commons:commons-util:${rootProject.extra["commonsVersion"]}") { isChanging = true }
    implementation("com.kodality.commons:commons-cache:${rootProject.extra["commonsVersion"]}") { isChanging = true }
}
