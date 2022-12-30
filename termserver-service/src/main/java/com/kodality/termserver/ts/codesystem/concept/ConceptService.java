package com.kodality.termserver.ts.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystemEntity;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemRepository;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.valueset.ValueSetVersionRepository;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptRepository;
import com.kodality.termserver.valueset.ValueSetVersion;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
  private final ValueSetVersionRepository valueSetVersionRepository;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ValueSetVersionConceptRepository valueSetVersionConceptRepository;

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
  public Concept saveWithVersions(Concept concept, String codeSystem) {
    save(concept, codeSystem);
    if (CollectionUtils.isNotEmpty(concept.getVersions())) {
      concept.getVersions().stream().filter(v -> PublicationStatus.draft.equals(v.getStatus())).forEach(version -> {
        version.setCodeSystem(codeSystem);
        codeSystemEntityVersionService.save(version, concept.getId());
      });
    }
    return concept;
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    prepareParams(params);
    QueryResult<Concept> concepts = repository.query(params);
    concepts.setData(decorate(concepts.getData(), params.getCodeSystem(), params.getCodeSystemVersion()));
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
    params.setCodeSystemEntityIds(String.join(",", csEntityIds));
    params.setCodeSystemVersion(codeSystemVersion);
    params.setCodeSystem(codeSystem);
    params.setLimit(csEntityIds.size());
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(params).getData();
    concepts.forEach(c -> c.setVersions(versions.stream().filter(v -> v.getCode().equals(c.getCode())).collect(Collectors.toList())));
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
      params.setValueSetExpandResultIds(valueSetVersionConceptRepository.expand(params.getValueSetVersionId()).stream()
          .map(c -> String.valueOf(c.getConcept().getId())).collect(Collectors.joining(",")));
    }
  }

  @Transactional
  public void cancel(Long conceptId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    codeSystemEntityService.cancel(conceptId);
    repository.cancel(conceptId);
  }
}
