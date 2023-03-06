package com.kodality.termserver.ts.codesystem.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.ts.codesystem.concept.ConceptRefreshViewJob;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
    repository.retain(entries);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList());
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

}
