package com.kodality.termserver.ts.codesystem.association;

import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import java.util.List;
import java.util.Objects;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemAssociationService {
  private final CodeSystemAssociationRepository repository;
  private final CodeSystemEntityService codeSystemEntityService;

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  @Transactional
  public void save(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId) {
    List<CodeSystemAssociation> existing = loadAll(codeSystemEntityVersionId);

    associations.stream().filter(a -> a.getId() == null)
        .forEach(a -> existing.stream().filter(e -> isSame(a, e)).findAny().ifPresent(codeSystemAssociation -> a.setId(codeSystemAssociation.getId())));

    repository.retain(associations, codeSystemEntityVersionId);
    associations.forEach(association -> {
      association.setType(CodeSystemEntityType.association);
      if (association.getId() == null) {
        codeSystemEntityService.save(association);
      }
    });
    repository.batchUpsert(associations, codeSystemEntityVersionId);
  }

  private boolean isSame(CodeSystemAssociation a, CodeSystemAssociation b) {
    return Objects.equals(a.getTargetId(), b.getTargetId())
        && Objects.equals(a.getAssociationType(), b.getAssociationType());
  }

}
