package com.kodality.termserver.integration.fhir.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.concept.ConceptService;
import com.kodality.termserver.integration.common.BinaryHttpClient;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetService;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionService;
import com.kodality.zmei.fhir.FhirMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirValueSetImportService {

  private final ConceptService conceptService;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final BinaryHttpClient client = new BinaryHttpClient();


  @Transactional
  public void importValueSet(String url) {
    String resource = getResource(url);
    com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.ValueSet.class);

    ValueSetVersion version = prepareValueSetAndVersion(FhirValueSetMapper.mapValueSet(valueSet));
    valueSetVersionService.saveConcepts(version.getId(), findConcepts(valueSet));

    log.info("Activating version '{}' in value set '{}'", version.getValueSet(), version.getVersion());
    valueSetVersionService.activate(version.getValueSet(), version.getVersion());
  }

  private String getResource(String url) {
    log.info("Loading fhir value set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

  private ValueSetVersion prepareValueSetAndVersion(ValueSet valueSet) {
    log.info("Checking, the value set and version exists");
    Optional<ValueSet> existingValueSet = valueSetService.get(valueSet.getId());
    if (existingValueSet.isEmpty()) {
      log.info("Value set {} does not exist, creating new", valueSet.getId());
      valueSetService.create(valueSet);
    }

    ValueSetVersion version = valueSet.getVersions().get(0);
    Optional<ValueSetVersion> existingVersion = valueSetVersionService.getVersion(valueSet.getId(), version.getVersion());
    if (existingVersion.isPresent() && existingVersion.get().getStatus().equals(PublicationStatus.active)) {
      throw ApiError.TE105.toApiException(Map.of("version", version.getVersion()));
    }
    log.info("Saving value set version {}", version.getVersion());
    valueSetVersionService.save(version);
    return version;
  }

  private List<Concept> findConcepts(com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet) {
    if (valueSet.getCompose() == null) {
      return new ArrayList<>();
    }
    List<Concept> concepts = new ArrayList<>();
    valueSet.getCompose().getInclude().forEach(i -> {
      ConceptQueryParams params = new ConceptQueryParams();
      params.setCodeSystemUri(i.getSystem());
      params.setCodeSystemVersion(i.getVersion());
      params.setCodeEq(i.getConcept() == null ? null : i.getConcept().stream().map(c -> c.getCode()).collect(Collectors.joining(",")));
      concepts.addAll(conceptService.query(params).getData());
    });
    return concepts;
  }
}
