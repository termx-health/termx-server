// Dependency substitution for vendored Zmei modules
// Replaces Maven artifacts with local project dependencies

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.kodality.zmei:zmei-fhir"))
            .using(project(":zmei:zmei-fhir"))
        substitute(module("com.kodality.zmei:zmei-fhir-jackson"))
            .using(project(":zmei:zmei-fhir-jackson"))
        substitute(module("com.kodality.zmei:zmei-fhir-client"))
            .using(project(":zmei:zmei-fhir-client"))
        substitute(module("com.kodality.zmei:zmei-cds"))
            .using(project(":zmei:zmei-cds"))
    }
}
