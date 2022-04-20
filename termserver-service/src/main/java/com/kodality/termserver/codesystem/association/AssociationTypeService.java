package com.kodality.termserver.codesystem.association;

import com.kodality.termserver.association.AssociationType;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class AssociationTypeService {
  private final AssociationTypeRepository repository;

  public AssociationType load(String code) {
    return repository.load(code);
  }

  @Transactional
  public AssociationType save(AssociationType associationType) {
    repository.save(associationType);
    return associationType;
  }

}
