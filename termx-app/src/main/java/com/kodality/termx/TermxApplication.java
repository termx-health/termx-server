package com.kodality.termx;

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
        com.kodality.termx.terminology.codesystem.CodeSystemController.class,
        com.kodality.termx.terminology.codesystem.concept.ConceptController.class,
        com.kodality.termx.terminology.codesystem.designation.DesignationController.class,
        com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionController.class,
        com.kodality.termx.terminology.valueset.ValueSetController.class,
        com.kodality.termx.terminology.valueset.ValueSetVersionController.class,
        com.kodality.termx.terminology.mapset.MapSetController.class,
        com.kodality.termx.terminology.mapset.entity.MapSetEntityVersionController.class,
        com.kodality.termx.terminology.mapset.association.MapSetAssociationController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Thesaurus"),
    classes = {
        com.kodality.termx.thesaurus.tag.TagController.class,
        com.kodality.termx.thesaurus.template.TemplateController.class,
        com.kodality.termx.thesaurus.page.PageController.class,
        com.kodality.termx.thesaurus.pagecontent.PageContentController.class,
        com.kodality.termx.thesaurus.pagerelation.PageRelationController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Observation definition"),
    classes = {com.kodality.termx.observationdefinition.ObservationDefinitionController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Modeler"),
    classes = {
        com.kodality.termx.modeler.structuredefinition.StructureDefinitionController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "SNOMED"),
    classes = {com.kodality.termx.snomed.snomed.SnomedController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "UCUM"),
    classes = {com.kodality.termx.measurementunit.MeasurementUnitController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Editions"),
    classes = {
        com.kodality.termx.atc.AtcController.class,
        com.kodality.termx.icd10.Icd10Controller.class,
        com.kodality.termx.loinc.LoincController.class,
        com.kodality.termx.orphanet.OrphanetController.class,
        com.kodality.termx.atcest.AtcEstController.class,
        com.kodality.termx.icd10est.Icd10EstController.class,
        com.kodality.termx.ichiuz.IchiUzController.class
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
public class TermxApplication {

  public static void main(String[] args) {
    Micronaut.run(TermxApplication.class, args);
  }
}

