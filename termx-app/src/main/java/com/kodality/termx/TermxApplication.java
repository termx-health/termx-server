package com.kodality.termx;

import com.kodality.termx.observationdefinition.observationdefinition.ObservationDefinitionController;
import com.kodality.termx.ucum.measurementunit.MeasurementUnitController;
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
        com.kodality.termx.terminology.terminology.codesystem.CodeSystemController.class,
        com.kodality.termx.terminology.terminology.codesystem.concept.ConceptController.class,
        com.kodality.termx.terminology.terminology.codesystem.designation.DesignationController.class,
        com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionController.class,
        com.kodality.termx.terminology.terminology.valueset.ValueSetController.class,
        com.kodality.termx.terminology.terminology.valueset.ValueSetVersionController.class,
        com.kodality.termx.terminology.terminology.mapset.MapSetController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Wiki"),
    classes = {
        com.kodality.termx.wiki.tag.TagController.class,
        com.kodality.termx.wiki.template.TemplateController.class,
        com.kodality.termx.wiki.page.PageController.class,
        com.kodality.termx.wiki.pagecontent.PageContentController.class,
        com.kodality.termx.wiki.pagerelation.PageRelationController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Observation definition"),
    classes = {ObservationDefinitionController.class}
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
    classes = {MeasurementUnitController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Editions"),
    classes = {
        com.kodality.termx.editionint.atc.AtcController.class,
        com.kodality.termx.editionint.icd10.Icd10Controller.class,
        com.kodality.termx.editionint.loinc.LoincController.class,
        com.kodality.termx.editionint.orphanet.OrphanetController.class,
        com.kodality.termx.editionest.atcest.AtcEstController.class,
        com.kodality.termx.editionest.icd10est.Icd10EstController.class,
        com.kodality.termx.editionuzb.ichiuz.IchiUzController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "File importer"),
    classes = {
        com.kodality.termx.terminology.fileimporter.codesystem.CodeSystemFileImportController.class,
        com.kodality.termx.terminology.fileimporter.valueset.ValueSetFileImportController.class,
        com.kodality.termx.terminology.fileimporter.mapset.MapSetFileImportController.class,
        com.kodality.termx.terminology.fileimporter.association.AssociationFileImportController.class,
        com.kodality.termx.terminology.fileimporter.analyze.FileAnalysisController.class
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

