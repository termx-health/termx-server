package org.termx;

import org.termx.observationdefinition.observationdefinition.ObservationDefinitionController;
import org.termx.ucum.measurementunit.MeasurementUnitController;
import org.termx.ucum.controller.UcumController;
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
        contact = @Contact(url = "https://termx.org", name = "TermX support", email = "support@termx.org")
    ),
    externalDocs = @ExternalDocumentation(description = "Read more about TermX", url = "https://tutorial.termx.org/en/about")
)
@OpenAPIInclude(
    tags = @Tag(name = "Terminology"),
    classes = {
        org.termx.terminology.terminology.association.AssociationTypeController.class,
        org.termx.terminology.terminology.codesystem.CodeSystemController.class,
        org.termx.terminology.terminology.codesystem.validator.CodeSystemValidatorController.class,
        org.termx.terminology.terminology.codesystem.entitypropertysummary.CodeSystemEntityPropertySummaryController.class,
        org.termx.terminology.terminology.codesystem.compare.CodeSystemCompareController.class,
        org.termx.terminology.terminology.definedproperty.DefinedPropertyController.class,
        org.termx.terminology.terminology.mapset.MapSetController.class,
        org.termx.terminology.terminology.namingsystem.NamingSystemController.class,
        org.termx.terminology.terminology.relatedartifacts.RelatedArtifactController.class,
        org.termx.terminology.terminology.valueset.ValueSetController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Wiki"),
    classes = {
        org.termx.wiki.tag.TagController.class,
        org.termx.wiki.space.WikiSpaceController.class,
        org.termx.wiki.template.TemplateController.class,
        org.termx.wiki.page.PageController.class,
        org.termx.wiki.pagecontent.PageContentController.class,
        org.termx.wiki.pagerelation.PageRelationController.class,
        org.termx.wiki.pagecomment.PageCommentController.class,
        org.termx.wiki.pagelink.PageLinkController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Observation definition"),
    classes = {ObservationDefinitionController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Modeler"),
    classes = {
        org.termx.modeler.structuredefinition.StructureDefinitionController.class,
        org.termx.modeler.transformationdefinition.TransformationDefinitionController.class,
        org.termx.implementationguide.ig.ImplementationGuideController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Space"),
    classes = {
        org.termx.core.sys.space.SpaceController.class,
        org.termx.core.sys.space.SpaceGithubController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "SNOMED"),
    classes = {org.termx.snomed.integration.SnomedController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "MeasurementUnits"),
    classes = {MeasurementUnitController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "UCUM"),
    classes = {UcumController.class}
)
@OpenAPIInclude(
    tags = @Tag(name = "Editions"),
    classes = {
        org.termx.editionint.atc.AtcController.class,
        org.termx.editionint.icd10.Icd10Controller.class,
        org.termx.editionint.loinc.LoincController.class,
        org.termx.editionint.orphanet.OrphanetController.class,
        org.termx.editionest.atcest.AtcEstController.class,
        org.termx.editionest.icd10est.Icd10EstController.class,
        org.termx.editionuzb.ichiuz.IchiUzController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "File importer"),
    classes = {
        org.termx.terminology.fileimporter.codesystem.CodeSystemFileImportController.class,
        org.termx.terminology.fileimporter.valueset.ValueSetFileImportController.class,
        org.termx.terminology.fileimporter.mapset.MapSetFileImportController.class,
        org.termx.terminology.fileimporter.association.AssociationFileImportController.class,
        org.termx.terminology.fileimporter.analyze.FileAnalysisController.class
    }
)
@OpenAPIInclude(
    tags = @Tag(name = "Task management"),
    classes = {
        org.termx.task.TaskController.class
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

