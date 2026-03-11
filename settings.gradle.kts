//includeBuild("../taskflow-service")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "termx-server"

include("edition-int")
include("edition-est")
include("edition-uzb")

include("termx-api")
include("termx-client")
include("termx-app")
include("termx-core")
include("termx-integtest")

//include("gatling")

include("task")
include("task-taskforge")
include("wiki")
include("observation-definition")
include("terminology")
include("modeler")
include("bob")
include("implementation-guide")
include("uam")

include("snomed")
include("ucum")

// Vendored Kodality Commons modules
include("kodality-commons:commons-model")
include("kodality-commons:commons-util")
include("kodality-commons:commons-db-core")
include("kodality-commons:commons-db")  // Note: commons-db-bean merged into commons-db
include("kodality-commons:commons-http-client")
include("kodality-commons:commons-csv")
include("kodality-commons:commons-zmei")
include("kodality-commons:commons-cache")
include("kodality-commons:commons-micronaut")
include("kodality-commons:commons-micronaut-pg")
include("kodality-commons:commons-micronaut-drools")
include("kodality-commons:commons-util-spring")
include("kodality-commons:commons-permcache")
include("kodality-commons:commons-sequence")
include("kodality-commons:commons-tenant")

// Vendored Zmei modules
include("zmei:zmei-fhir")
include("zmei:zmei-fhir-jackson")
include("zmei:zmei-fhir-client")
include("zmei:zmei-cds")
