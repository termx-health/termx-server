package com.kodality.termserver.sys.space;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class SpaceService {
  private final SpaceRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public Space save(Space space) {
    userPermissionService.checkPermitted(space.getCode(), "Space", "edit");
    repository.save(space);
    return space;
  }

  public Space load(Long id) {
    return repository.load(id);
  }

  public Space load(String code) {
    return repository.load(code);
  }

  public QueryResult<Space> query(SpaceQueryParams params) {
    return repository.query(params);
  }

}
