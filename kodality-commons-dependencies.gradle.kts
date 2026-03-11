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
        substitute(module("com.kodality.commons:commons-micronaut"))
            .using(project(":kodality-commons:commons-micronaut"))
        substitute(module("com.kodality.commons:commons-micronaut-pg"))
            .using(project(":kodality-commons:commons-micronaut-pg"))
        substitute(module("com.kodality.commons:commons-util-spring"))
            .using(project(":kodality-commons:commons-util-spring"))
        substitute(module("com.kodality.commons:commons-micronaut-drools"))
            .using(project(":kodality-commons:commons-micronaut-drools"))
        substitute(module("com.kodality.commons:commons-permcache"))
            .using(project(":kodality-commons:commons-permcache"))
        substitute(module("com.kodality.commons:commons-sequence"))
            .using(project(":kodality-commons:commons-sequence"))
        substitute(module("com.kodality.commons:commons-tenant"))
            .using(project(":kodality-commons:commons-tenant"))
    }
}
