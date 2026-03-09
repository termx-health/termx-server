package org.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.model.QueryResult;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.codesystem.CodeSystemRepository;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityService;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.core.ts.CodeSystemExternalProvider;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntity;
import org.termx.ts.codesystem.CodeSystemEntityType;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.ConceptTransactionRequest;
import org.termx.ts.codesystem.EntityPropertyValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptService {
  private final ConceptRepository repository;
  private final CodeSystemRepository codeSystemRepository;
  private final CodeSystemEntityService codeSystemEntityService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final List<CodeSystemExternalProvider> codeSystemProviders;

  @Transactional
  public Concept save(Concept concept, String codeSystem) {
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
    Map<String, List<Concept>> existingConcepts = getExistingConcepts(codeSystem).stream().collect(Collectors.groupingBy(Concept::getCode));

    concepts.forEach(concept -> {
      concept.setType(CodeSystemEntityType.concept);
      concept.setCodeSystem(codeSystem);

      Optional<Concept> existingConcept = Optional.ofNullable(existingConcepts.getOrDefault(concept.getCode(), null)).flatMap(l -> l.stream().findFirst());
      existingConcept.ifPresent(value -> {
        concept.setId(value.getId());
        concept.setCodeSystem(value.getCodeSystem());
      });
    });
    codeSystemEntityService.batchSave(concepts.stream().map(c -> (CodeSystemEntity) c).toList(), codeSystem);
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
    entityVersion.setCodeSystemEntityId(concept.getId());
    if (request.getCodeSystemVersion() != null) {
      codeSystemVersionService.linkEntityVersions(request.getCodeSystem(), request.getCodeSystemVersion(), List.of(entityVersion.getId()));
    }
    return concept;
  }

  @Transactional
  public void cancelOrRetireRedundantConcepts(List<Concept> actualConcepts, CodeSystemVersion codeSystemVersion) {
    final String codeSystem = codeSystemVersion.getCodeSystem();
    final List<Concept> existingConcepts = getExistingConcepts(codeSystem);
    final List<Concept> redundantConcept = existingConcepts.stream()
            .filter(ex -> actualConcepts.stream()
                    .noneMatch(c -> c.getCode().equals(ex.getCode()))
            ).toList();
    redundantConcept.forEach(rc -> {
      decorate(rc, codeSystem, codeSystemVersion.getVersion());
      if (rc.getVersions().stream().allMatch(v -> PublicationStatus.draft.equals(v.getStatus()))) {
        log.info("Concept {} has only draft versions. Cancelling concept.", rc.getCode());
        repository.cancel(rc.getId(), codeSystem);
      } else {
        log.info("Concept {} has 'active' versions. Retiring active versions.", rc.getCode());
        rc.getVersions().forEach(v -> {
          if (PublicationStatus.draft.equals(v.getStatus())) {
            codeSystemEntityVersionService.cancel(v.getId());
          } else if (PublicationStatus.active.equals(v.getStatus())) {
            codeSystemEntityVersionService.retire(v.getId());
          }
        });
      }
    });
  }

  public QueryResult<Concept> query(ConceptQueryParams params) {
    for (CodeSystemExternalProvider provider : codeSystemProviders) {
      QueryResult<Concept> result = provider.searchConcepts(params.getCodeSystem(), params);
      if (CollectionUtils.isNotEmpty(result.getData())) {
        return result;
      }
    }

    String codeSystem = params.getCodeSystem();
    prepareParams(params);
    QueryResult<Concept> concepts = repository.query(params);
    concepts.setData(decorate(concepts.getData(), codeSystem, params));
    return concepts;
  }

  public Optional<Concept> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(c -> decorate(c, null, null));
  }

  public Optional<Concept> load(String codeSystem, String code) {
    List<String> codeSystems = codeSystemRepository.closure(codeSystem);
    if (CollectionUtils.isEmpty(codeSystems)) {
      return Optional.empty();
    }
    return Optional.ofNullable(repository.load(codeSystems, code)).map(c -> decorate(c, codeSystem, null));
  }

  public Map<String, Concept> batchLoad(List<Pair<String, String>> codeSystemCodePairs) {
    if (CollectionUtils.isEmpty(codeSystemCodePairs)) {
      return Map.of();
    }
    
    Map<String, List<String>> codeSystemToCodes = codeSystemCodePairs.stream()
        .collect(Collectors.groupingBy(
            Pair::getLeft,
            Collectors.mapping(Pair::getRight, Collectors.toList())
        ));
    
    List<Concept> concepts = repository.batchLoad(codeSystemToCodes);
    
    if (CollectionUtils.isEmpty(concepts)) {
      return Map.of();
    }
    
    List<Concept> decorated = decorate(concepts, null, new ConceptQueryParams());
    
    return decorated.stream()
        .collect(Collectors.toMap(
            c -> c.getCodeSystem() + "|" + c.getCode(),
            c -> c,
            (c1, c2) -> c1
        ));
  }

  public Optional<Concept> loadByUri(String codeSystemUri, String code) {
    Optional<String> codeSystem = codeSystemRepository.query(new CodeSystemQueryParams().setUri(codeSystemUri).limit(1)).findFirst().map(CodeSystem::getId);
    return codeSystem.flatMap(cs -> load(cs, code));
  }

  public Optional<Concept> load(String codeSystem, String codeSystemVersion, String code) {
    return query(new ConceptQueryParams()
        .setCodeSystem(codeSystem)
        .setCodeSystemVersion(codeSystemVersion)
        .setCodeEq(code)).findFirst().map(c -> decorate(c, codeSystem, codeSystemVersion));
  }

  private List<Concept> getExistingConcepts(String codeSystem) {
    ConceptQueryParams params = new ConceptQueryParams().setCodeSystem(codeSystem).all();
    prepareParams(params);
    return repository.query(params).getData();
  }

  private Concept decorate(Concept concept, String codeSystem, String codeSystemVersion) {
    List<CodeSystemEntityVersion> versions = codeSystemEntityVersionService.query(new CodeSystemEntityVersionQueryParams()
        .setCodeSystemEntityId(concept.getId())
        .setCodeSystemVersion(codeSystemVersion)
        .setCodeSystem(codeSystem)).getData();
    concept.setVersions(versions);
    return concept;
  }

  private List<Concept> decorate(List<Concept> concepts, String codeSystem, ConceptQueryParams p) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystemVersion(p.getCodeSystemVersion());
    params.setCodeSystemVersionId(p.getCodeSystemVersionId());
    params.setCodeSystemVersions(p.getCodeSystemVersions());
    params.setCodeSystem(codeSystem);
    params.all();

    List<String> csEntityIds = concepts.stream().map(CodeSystemEntity::getId).map(String::valueOf).toList();

    if (CollectionUtils.isNotEmpty(csEntityIds)) {
      IntStream.range(0, (csEntityIds.size() + 1000 - 1) / 1000)
          .mapToObj(i -> csEntityIds.subList(i * 1000, Math.min(csEntityIds.size(), (i + 1) * 1000))).forEach(batch -> {
            params.setCodeSystemEntityIds(String.join(",", batch));
            Map<String, List<CodeSystemEntityVersion>> versions = codeSystemEntityVersionService.query(params).getData().stream().collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode));
            concepts.forEach(c -> c.setVersions(versions.getOrDefault(c.getCode(), c.getVersions())));
          });
    }
    return concepts;
  }

  private void prepareParams(ConceptQueryParams params) {
    if (params.getCodeSystem() != null) {
      params.setCodeSystemVersionCodeSystem(params.getCodeSystem());
      String[] codeSystems = params.getCodeSystem().split(",");
      params.setCodeSystem(Arrays.stream(codeSystems).map(cs -> String.join(",", codeSystemRepository.closure(cs))).collect(Collectors.joining(",")));
      if (StringUtils.isEmpty(params.getCodeSystem())) {
        params.setCodeSystem(String.join(",", codeSystems));
      }
    }
  }

  @Transactional
  public void cancel(String code, String codeSystem) {
    Concept concept = load(codeSystem, code).orElseThrow();

    boolean conceptHasNonDraftVersion = Optional.ofNullable(concept.getVersions()).orElse(List.of()).stream().anyMatch(v -> !PublicationStatus.draft.equals(v.getStatus()));
    if (conceptHasNonDraftVersion) {
      throw ApiError.TE114.toApiException();
    }

    boolean conceptIsLinkedToNonDraftVersion = Optional.ofNullable(concept.getVersions()).orElse(List.of()).stream().flatMap(v -> Optional.ofNullable(v.getVersions()).orElse(List.of()).stream()).anyMatch(v -> !PublicationStatus.draft.equals(v.getStatus()));
    if (conceptIsLinkedToNonDraftVersion) {
      throw ApiError.TE115.toApiException();
    }

    repository.cancel(concept.getId(), codeSystem);
  }

  @Transactional
  public void propagateProperties(String code, List<Long> targetConceptIds, String codeSystem) {
    List<EntityPropertyValue> propertyValues = load(codeSystem, code).orElseThrow()
        .getLastVersion().orElseThrow()
        .getPropertyValues().stream().peek(pv -> pv.setId(null)).toList();

    ConceptQueryParams params = new ConceptQueryParams();
    params.setId(String.join(",", targetConceptIds.stream().map(String::valueOf).toList()));
    params.setLimit(targetConceptIds.size());

    Map<Long, List<CodeSystemEntityVersion>> targetVersions = query(params).getData().stream()
        .map(c -> {
          CodeSystemEntityVersion version = new CodeSystemEntityVersion().setCodeSystem(codeSystem).setStatus(PublicationStatus.draft);
          if (c.getLastDraftVersion().isPresent()) {
            version = c.getLastDraftVersion().get();
          } else if (c.getLastVersion().isPresent()) {
            version = c.getLastVersion().get();
            version.setId(null);
            version.setStatus(PublicationStatus.draft);
            version.setCreated(null);
            version.setDesignations(version.getDesignations().stream().peek(d -> d.setId(null)).toList());
            version.setAssociations(version.getAssociations().stream().peek(a -> a.setId(null)).toList());
          }
          version.setPropertyValues(propertyValues);
          return Pair.of(c.getId(), version);
        }).collect(Collectors.toMap(Pair::getKey, p -> List.of(p.getValue())));

    codeSystemEntityVersionService.batchSave(targetVersions, codeSystem);
  }
}
