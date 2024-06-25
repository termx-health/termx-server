package com.kodality.termx.terminology.terminology.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.association.AssociationTypeQueryParams;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class AssociationTypeService {
  private final AssociationTypeRepository repository;

  @Transactional
  public AssociationType save(AssociationType associationType) {
    repository.save(associationType);
    return associationType;
  }

  @Transactional
  public void createIfNotExist(List<AssociationType> associationTypes) {
    AssociationTypeQueryParams params = new AssociationTypeQueryParams()
        .setCode(associationTypes.stream().map(AssociationType::getCode).distinct().collect(Collectors.joining(",")))
        .limit(associationTypes.size());
    List<String> existingAssociations = query(params).getData().stream().map(AssociationType::getCode).toList();
    associationTypes = associationTypes.stream().filter(a -> !existingAssociations.contains(a.getCode())).toList();
    associationTypes.forEach(this::save);
  }

  public Optional<AssociationType> load(String code) {
    return Optional.ofNullable(repository.load(code));
  }

  public QueryResult<AssociationType> query(AssociationTypeQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void cancel(String code) {
    repository.cancel(code);
  }
}
