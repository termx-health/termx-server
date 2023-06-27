package com.kodality.termserver;

import io.micronaut.openapi.annotation.OpenAPIInclude;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
    info = @Info(
        title = "TermX",
        description = "${openapi.description}",
        contact = @Contact(url = "https://kodality.com", name = "Kodality support", email = "support@kodality.com")
    ),
    externalDocs = @ExternalDocumentation(description = "Read more about TermX", url = "https://wiki.kodality.dev/en/terminology-server")
)
@OpenAPIInclude(
    tags = @Tag(name = "Terminology"),
    classes = {
        com.kodality.termserver.terminology.codesystem.CodeSystemController.class,
        com.kodality.termserver.terminology.codesystem.concept.ConceptController.class,
        com.kodality.termserver.terminology.codesystem.designation.DesignationController.class,
        com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionController.class,
        com.kodality.termserver.terminology.valueset.ValueSetController.class,
        com.kodality.termserver.terminology.valueset.ValueSetVersionController.class,
        com.kodality.termserver.terminology.mapset.MapSetController.class,
        com.kodality.termserver.terminology.mapset.entity.MapSetEntityVersionController.class,
        com.kodality.termserver.terminology.mapset.association.MapSetAssociationController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Thesaurus"),
    classes = {
        com.kodality.termserver.thesaurus.tag.TagController.class,
        com.kodality.termserver.thesaurus.structuredefinition.StructureDefinitionController.class,
        com.kodality.termserver.thesaurus.template.TemplateController.class,
        com.kodality.termserver.thesaurus.page.PageController.class,
        com.kodality.termserver.thesaurus.pagecontent.PageContentController.class,
        com.kodality.termserver.thesaurus.pagerelation.PageRelationController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Observation definition"),
    classes = {com.kodality.termserver.observationdefinition.ObservationDefinitionController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "SNOMED"),
    classes = {com.kodality.termserver.snomed.snomed.SnomedController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "UCUM"),
    classes = {com.kodality.termserver.measurementunit.MeasurementUnitController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Editions"),
    classes = {
        com.kodality.termserver.atc.AtcController.class,
        com.kodality.termserver.icd10.Icd10Controller.class,
        com.kodality.termserver.loinc.LoincController.class,
        com.kodality.termserver.orphanet.OrphanetController.class,
        com.kodality.termserver.atcest.AtcEstController.class,
        com.kodality.termserver.icd10est.Icd10EstController.class,
        com.kodality.termserver.ichiuz.IchiUzController.class
    }
)
@SecurityScheme(name = "openid",
    type = SecuritySchemeType.OAUTH2,
    scheme = "bearer",
    bearerFormat = "jwt",
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "${oauth.url}/protocol/openid-connect/auth",
            tokenUrl = "${oauth.url}/protocol/openid-connect/token",
            scopes = @OAuthScope(name = "openid", description = "OpenID scope")
        )
    )
)
@SecurityRequirement(name = "openid")
public class TerminologyServerApplication {

  public static void main(String[] args) {
    Micronaut.run(TerminologyServerApplication.class, args);
  }
}

