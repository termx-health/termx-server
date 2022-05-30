package com.kodality.termserver.ts.association;

import com.kodality.termserver.association.AssociationType;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class AssociationTypeService {
  private final AssociationTypeRepository repository;

  public Optional<AssociationType> load(String code) {
    return Optional.ofNullable(repository.load(code));
  }

  @Transactional
  public AssociationType save(AssociationType associationType) {
    repository.save(associationType);
    return associationType;
  }

}