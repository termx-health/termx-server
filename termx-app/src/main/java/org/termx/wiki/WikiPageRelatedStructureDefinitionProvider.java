package org.termx.wiki;

import org.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import org.termx.wiki.WikiPageRelatedResourceProvider;
import org.termx.modeler.structuredefinition.StructureDefinitionService;
import org.termx.terminology.fhir.FhirFshConverter;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class WikiPageRelatedStructureDefinitionProvider implements WikiPageRelatedResourceProvider {
  private final StructureDefinitionService structureDefinitionService;
  private final FhirFshConverter fshConverter;

  public String getRelationType() {
    return "def";
  }

  public String gerResourceName() {
    return "structure-definition";
  }

  @Override
  public Optional<String> getContent(String code) {
    return structureDefinitionService.query(new StructureDefinitionQueryParams().setCode(code).limit(1))
        .findFirst()
        .map(sd -> {
          if ("fsh".equals(sd.getContentFormat())) {
            return fshConverter.toFhir(sd.getContent()).join();
          }
          return sd.getContent();
        });
  }
}
