package com.kodality.termserver.terminology.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.terminology.codesystem.CodeSystemRepository;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionRepository;
import com.kodality.termserver.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.CodeSystemExternalProvider;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemEntity;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityType;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.ConceptTransactionRequest;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptService {
  private final ConceptRepository repository;
  private final CodeSystemRepository codeSystemRepository;
  private final CodeSystemEntityService codeSystemEntityService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final ValueSetVersionConceptService valueSetVersionConceptService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final List<CodeSystemExternalProvider> codeSystemProviders;

  private final UserPermissionService userPermissionService;

  @Transactional
  public Concept save(Concept concept, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    concept.setType(CodeSystemEntityType.concept);
    concept.setCodeSystem(codeSystem);

    Optional<Concept> existingConcept = load(codeSystem, concept.getCode());
    existingConcept.ifPresent(value -> {
      concept.setId(value.getId());
      concept.setCodeSystem(value.getCodeSystem());
    });
    codeSystemEntityService.save(concept);
    repository.save(concept);
    return concept;
  }

  @Transactional
  public List<Concept> batchSave(List<Concept> concepts, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    List<Concept> existingConcepts = new ArrayList<>();

    IntStream.range(0, (concepts.size() + 1000 - 1) / 1000)
        .mapToObj(i -> concepts.subList(i * 1000, Math.min(concepts.size(), (i + 1) * 1000)))
        .forEach(batch -> {
          ConceptQueryParams params = new ConceptQueryParams();
          params.setLimit(batch.size());
          params.setCode(batch.stream().map(Concept::getCode).collect(Collectors.joining(",")));
          params.setCodeSystem(codeSystem);
          prepareParams(params);
          existingConcepts.addAll(repository.query(params).getData());
        });

    concepts.forEach(concept -> {
      concept.setType(CodeSystemEntityType.concept);
      concept.setCodeSystem(codeSystem);

      Optional<Concept> existingConcept = existingConcepts.stream().filter(ec -> ec.getCode().equals(concept.getCode())).findFirst();
      existingConcept.ifPresent(value -> {
        concept.setId(value.getId());
        concept.setCodeSystem(value.getCodeSystem());
      });
    });
    codeSystemEntityService.batchSave(concepts.stream().map(c -> (CodeSystemEntity) c).collect(Collectors.toList()), codeSystem);
    repository.batchUpsert(concepts);
    return concepts;
  }

  @Transactional
  public Concept save(ConceptTransactionRequest request) {
    Concept concept = request.getConcept();
    save(concept, request.getCodeSystem());

    if (request.getEntityVersion() == null) {
      return concept;
    }

    CodeSystemEntityVersion entityVersion = request.getEntityVersion();
    entityVersion.setCodeSystem(request.getCodeSystem());
    codeSystemEntityVersionService.save(entityVersion, concept.getId());
    if (request.getCodeSystemVersion() != null) {
      codeSystemVersionService.linkEntityVersions(request.getCodeSystem(), request.getCodeSystemVersion(), List.of(entityVersion.getId()));
    }
    return concept;
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    for (CodeSystemExternalProvider provider: codeSystemProviders) {
      QueryResult<Concept> result = provider.searchConcepts(params.getCodeSystem(), params);
      if (CollectionUtils.isNotEmpty(result.getData())) {
        return result;
      }
    }

    String codeSystem = params.getCodeSystem();
    prepareParams(params);
    QueryResult<Concept> concepts = repository.query(params);
    concepts.setData(decorate(concepts.getData(), codeSystem, params.getCodeSystemVersion()));
    return concepts;
  }

  public Optional<Concept> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(c -> decorate(c, null, null));
  }

  public Optional<Concept> load(String codeSystem, String code) {
    return Optional.ofNullable(repository.load(codeSystemRepository.closure(codeSystem), code)).map(c -> decorate(c, codeSystem, null));
  }

  public Optional<Concept> load(String codeSystem, String codeSystemVersion, String code) {
    return query(new ConceptQueryParams()
        .setCodeSystem(codeSystem)
        .setCodeSystemVersion(codeSystemVersion)
        .setCode(code)).findFirst().map(c -> decorate(c, codeSystem, codeSystemVersion));
  }

  private Concept decorate(Concept concept, String codeSystem, String codeSystemVersion) {
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemEntityId(concept.getId())
        .setCodeSystemVersion(codeSystemVersion)
        .setCodeSystem(codeSystem)).getData();
    concept.setVersions(versions);
    return concept;
  }

  private List<Concept> decorate(List<Concept> concepts, String codeSystem, String codeSystemVersion) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    List<String> csEntityIds = concepts.stream().map(CodeSystemEntity::getId).map(String::valueOf).toList();

    if (CollectionUtils.isNotEmpty(csEntityIds)) {
      params.setCodeSystemEntityIds(String.join(",", csEntityIds));
      params.setCodeSystemVersion(codeSystemVersion);
      params.setCodeSystem(codeSystem);
      params.all();
      List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(params).getData();
      concepts.forEach(c -> c.setVersions(versions.stream().filter(v -> v.getCode().equals(c.getCode())).collect(Collectors.toList())));
    }
    return concepts;
  }

  private void prepareParams(ConceptQueryParams params) {
    if (params.getCodeSystem() != null) {
      String[] codeSystems = params.getCodeSystem().split(",");
      params.setCodeSystem(Arrays.stream(codeSystems).map(cs -> String.join(",", codeSystemRepository.closure(cs))).collect(Collectors.joining(",")));
      if (StringUtils.isEmpty(params.getCodeSystem())) {
        params.setCodeSystem(String.join(",", codeSystems));
      }
    }
    if (params.getValueSet() != null && params.getValueSetVersion() == null) {
      ValueSetVersion valueSetVersion = valueSetVersionRepository.loadLastVersion(params.getValueSet());
      params.setValueSetVersionId(valueSetVersion == null ? null : valueSetVersion.getId());
    }
    if (params.getValueSet() != null && params.getValueSetVersion() != null) {
      params.setValueSetVersionId(valueSetVersionRepository.load(params.getValueSet(), params.getValueSetVersion()).getId());
    }
    if (params.getValueSetVersionId() != null) {
      params.setValueSetExpandResultIds(valueSetVersionConceptService.expand(params.getValueSetVersionId(), null).stream()
          .map(c -> c.getConcept().getId())
          .filter(Objects::nonNull)
          .map(String::valueOf).collect(Collectors.joining(",")));
    }
  }

  @Transactional
  public void cancel(Long conceptId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    codeSystemEntityService.cancel(conceptId);
    repository.cancel(conceptId);
  }
}
