package com.kodality.termserver.ts.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.association.AssociationTypeQueryParams;
import com.kodality.termserver.auth.auth.UserPermissionService;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class AssociationTypeService {
  private final AssociationTypeRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public AssociationType save(AssociationType associationType) {
    userPermissionService.checkPermitted(associationType.getCode(), "AssociationType", "edit");

    repository.save(associationType);
    return associationType;
  }

  public Optional<AssociationType> load(String code) {
    return Optional.ofNullable(repository.load(code));
  }

  public QueryResult<AssociationType> query(AssociationTypeQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void cancel(String code) {
    userPermissionService.checkPermitted(code, "AssociationType", "publish");
    repository.cancel(code);
  }
}
