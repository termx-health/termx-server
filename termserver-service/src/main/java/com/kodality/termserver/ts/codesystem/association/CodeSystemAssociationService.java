package com.kodality.termserver.ts.codesystem.association;

import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityType;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  public Optional<CodeSystemAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId) {
    if (associations == null) {
      associations = new ArrayList<>();
    }

    List<CodeSystemAssociation> existing = loadAll(codeSystemEntityVersionId);
    associations.stream()
        .filter(a -> a.getId() == null)
        .forEach(a -> existing.stream().filter(e -> isSame(a, e)).findAny().ifPresent(codeSystemAssociation -> a.setId(codeSystemAssociation.getId())));

    repository.retain(associations, codeSystemEntityVersionId);
    associations.forEach(association -> save(association, codeSystemEntityVersionId));
  }

  @Transactional
  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId) {
    association.setType(CodeSystemEntityType.association);
    codeSystemEntityService.save(association);
    repository.save(association, codeSystemEntityVersionId);
  }

  private boolean isSame(CodeSystemAssociation a, CodeSystemAssociation b) {
    return Objects.equals(a.getTargetId(), b.getTargetId())
        && Objects.equals(a.getAssociationType(), b.getAssociationType());
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(id);
  }

}
