package com.kodality.termx.terminology.codesystem.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityService;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemEntityType;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import java.util.ArrayList;
import java.util.Collection;
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

  private final UserPermissionService userPermissionService;

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  public Optional<CodeSystemAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    repository.retain(associations, codeSystemEntityVersionId);
    if (associations != null) {
      associations.forEach(association -> save(association, codeSystemEntityVersionId, codeSystem));
    }
  }

  @Transactional
  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    validate(association, codeSystemEntityVersionId);

    association.setCodeSystem(codeSystem);
    association.setType(CodeSystemEntityType.association);
    codeSystemEntityService.save(association);
    repository.save(association, codeSystemEntityVersionId);
    conceptRefreshViewJob.refreshView();
  }

  @Transactional
  public void batchUpsert(Map<Long, List<CodeSystemAssociation>> associations, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    List<Entry<Long, List<CodeSystemAssociation>>> entries = associations.entrySet().stream().toList();
    validate(entries);
    repository.retain(entries);
    codeSystemEntityService.batchSave(associations.values().stream().flatMap(Collection::stream).map(a -> a.setType(CodeSystemEntityType.association)).toList(), codeSystem);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList(), codeSystem);
  }


  @Transactional
  public void delete(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");
    repository.delete(id);
    conceptRefreshViewJob.refreshView();
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

    List<String> codes = new ArrayList<>();
    IntStream.range(0, (entityVersionIds.size() + 1000 - 1) / 1000)
        .mapToObj(i -> entityVersionIds.subList(i * 1000, Math.min(entityVersionIds.size(), (i + 1) * 1000))).forEach(batch -> {
          codes.addAll(codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams()
              .setIds(batch.stream().map(String::valueOf).collect(Collectors.joining(","))).limit(1000))
              .getData().stream().map(CodeSystemEntityVersion::getCode).toList());
        });
    boolean sameConcept = entityVersionIds.size() > codes.stream().distinct().toList().size();
    if (sameConcept) {
      throw ApiError.TE805.toApiException();
    }
  }
}
