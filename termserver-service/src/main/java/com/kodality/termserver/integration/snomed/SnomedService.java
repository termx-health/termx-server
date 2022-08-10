package com.kodality.termserver.integration.snomed;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.client.SnowstormClient;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.termserver.snomed.SnomedImportRequest;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.refset.SnomedRefsetResponse.SnomedRefsetItem;
import com.kodality.termserver.snomed.refset.SnomedRefsetSearchParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionService;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SnomedService {
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemImportService codeSystemImportService;
  private final SnowstormClient snowstormClient;
  private final SnomedMapper mapper;

  public void importConcepts(SnomedImportRequest request) {
    if (request.getRefsetId() != null) {
      List<SnomedConcept> concepts = loadRefsetConcepts(request.getRefsetId());
      importConcepts(concepts);
    }
  }

  @Transactional
  public void importConcepts(List<SnomedConcept> snomedConcepts) {
    List<Concept> concepts = snomedConcepts.stream().map(mapper::toConcept).toList();
    List<SnomedConcept> descriptionType = snomedConcepts.stream().filter(c -> c.getDescriptions() != null)
        .flatMap(c -> c.getDescriptions().stream().map(d -> snowstormClient.loadConcept(d.getTypeId()).join())).toList();
    List<EntityProperty> properties = mapper.toProperties(descriptionType);

    CodeSystemVersion version = codeSystemVersionService.loadLastVersion("snomed-ct", PublicationStatus.draft);
    if (version == null) {
      throw ApiError.TE108.toApiException(Map.of("codeSystem", "snomed-ct"));
    }

    properties = codeSystemImportService.saveProperties(properties, "snomed-ct");
    codeSystemImportService.saveConcepts(concepts, version, properties);
  }

  private List<SnomedConcept> loadRefsetConcepts(String refsetId) {
    SnomedRefsetSearchParams params = new SnomedRefsetSearchParams();
    params.setReferenceSet(refsetId);
    params.setLimit(1);

    //params.setLimit(snowstormClient.findRefsetMembers(params).join().getTotal().intValue());
    params.setLimit(100); //TODO: fix after snowstorm version update

    List<String> conceptIds = snowstormClient.findRefsetMembers(params).join().getItems().stream()
        .map(SnomedRefsetItem::getReferencedComponent)
        .map(SnomedConcept::getConceptId).toList();

    SnomedConceptSearchParams conceptParams = new SnomedConceptSearchParams();
    conceptParams.setConceptIds(conceptIds);
    conceptParams.setLimit(conceptIds.size());
    return snowstormClient.queryConcepts(conceptParams).join().getItems();
  }
}
