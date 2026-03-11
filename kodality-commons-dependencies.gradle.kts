// Helper script to replace Maven commons dependencies with project dependencies
// Apply this in modules that need commons dependencies

configurations.all {
    resolutionStrategy.dependencySubstitution {
        // Replace Maven artifacts with local project dependencies
        substitute(module("com.kodality.commons:commons-model"))
            .using(project(":kodality-commons:commons-model"))
        substitute(module("com.kodality.commons:commons-util"))
            .using(project(":kodality-commons:commons-util"))
        substitute(module("com.kodality.commons:commons-db-core"))
            .using(project(":kodality-commons:commons-db-core"))
        substitute(module("com.kodality.commons:commons-db-bean"))
            .using(project(":kodality-commons:commons-db"))  // commons-db-bean merged into commons-db
        substitute(module("com.kodality.commons:commons-db"))
            .using(project(":kodality-commons:commons-db"))
        substitute(module("com.kodality.commons:commons-http-client"))
            .using(project(":kodality-commons:commons-http-client"))
        substitute(module("com.kodality.commons:commons-csv"))
            .using(project(":kodality-commons:commons-csv"))
        substitute(module("com.kodality.commons:commons-zmei"))
            .using(project(":kodality-commons:commons-zmei"))
        substitute(module("com.kodality.commons:commons-cache"))
            .using(project(":kodality-commons:commons-cache"))
    }
}
