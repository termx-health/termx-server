package com.kodality.termx.terminology.terminology.codesystem.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemEntityType;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemAssociationService {
  private final CodeSystemAssociationRepository repository;
  private final CodeSystemEntityService codeSystemEntityService;
  private final ConceptRefreshViewJob conceptRefreshViewJob;
  private final CodeSystemEntityVersionRepository codeSystemEntityVersionRepository;

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId, Long baseEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId, baseEntityVersionId);
  }

  public List<CodeSystemAssociation> loadReferences(String codeSystem, Long targetVersionId) {
    return repository.loadReferences(codeSystem, targetVersionId);
  }

  public Optional<CodeSystemAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId, String codeSystem) {
    repository.retain(associations, codeSystemEntityVersionId);
    if (associations != null) {
      associations.stream().filter(a -> !a.isSupplement()).forEach(association -> save(association, codeSystemEntityVersionId, codeSystem));
    }
  }

  @Transactional
  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId, String codeSystem) {
    validate(association, codeSystemEntityVersionId);

    association.setCodeSystem(codeSystem);
    association.setType(CodeSystemEntityType.association);
    codeSystemEntityService.save(association);
    repository.save(association, codeSystemEntityVersionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void cancel(Long codeSystemEntityVersionId) {
    repository.cancel(codeSystemEntityVersionId);
  }


  @Transactional
  public void batchUpsert(Map<Long, List<CodeSystemAssociation>> associations, String codeSystem) {
    List<Entry<Long, List<CodeSystemAssociation>>> entries = associations.entrySet().stream().toList();
    validate(entries);
    repository.retain(entries);
    codeSystemEntityService.batchSave(associations.values().stream().flatMap(Collection::stream).map(a -> a.setType(CodeSystemEntityType.association)).toList(), codeSystem);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList(), codeSystem);
  }

  public QueryResult<CodeSystemAssociation> query(CodeSystemAssociationQueryParams params) {
    return repository.query(params);
  }

  private void validate(CodeSystemAssociation association, Long sourceId) {
    if (association.getTargetId() == null) {
      throw ApiError.TE804.toApiException();
    }

    boolean sameConcept = codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams()
        .setIds(Stream.of(association.getTargetId(), sourceId).map(String::valueOf).collect(Collectors.joining(",")))
        .limit(2))
        .getData().stream().collect(Collectors.groupingBy(CodeSystemEntityVersion::getCode)).keySet().size() == 1;
    if (sameConcept) {
      throw ApiError.TE805.toApiException();
    }
  }

  private void validate(List<Entry<Long, List<CodeSystemAssociation>>> entries) {
    entries.forEach(e -> e.getValue().forEach(a -> {
      if (a.getTargetId() == null) {
        throw ApiError.TE804.toApiException();
      }
    }));

    List<Long> sourceIds = entries.stream().map(Entry::getKey).collect(Collectors.toList());
    sourceIds.addAll(entries.stream().flatMap(e -> e.getValue().stream().map(CodeSystemAssociation::getTargetId)).toList());

    List<Long> entityVersionIds = sourceIds.stream().distinct().toList();

    Map<Long, String> versionCodes = new HashMap<>();
    IntStream.range(0, (entityVersionIds.size() + 1000 - 1) / 1000)
        .mapToObj(i -> entityVersionIds.subList(i * 1000, Math.min(entityVersionIds.size(), (i + 1) * 1000))).forEach(batch -> {
          codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams()
              .setIds(batch.stream().map(String::valueOf).collect(Collectors.joining(","))).limit(1000))
              .getData().forEach(v -> versionCodes.put(v.getId(), v.getCode()));
        });

    boolean sameConcept = entries.stream().anyMatch(s -> s.getValue().stream().anyMatch(t -> versionCodes.get(s.getKey()).equals(versionCodes.get(t.getTargetId()))));
    if (sameConcept) {
      throw ApiError.TE805.toApiException();
    }
  }
}
