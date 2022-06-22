package com.kodality.termserver.auth.privilege;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Privilege;
import com.kodality.termserver.auth.PrivilegeQueryParams;
import com.kodality.termserver.auth.privilegeresource.PrivilegeResourceService;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PrivilegeService {
  private final PrivilegeRepository repository;
  private final PrivilegeResourceService resourceService;

  @Transactional
  public void save(Privilege privilege) {
    repository.save(privilege);
    resourceService.save(privilege.getResources(), privilege.getId());
  }

  public Privilege load(Long id) {
    return decorate(repository.load(id));
  }

  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    QueryResult<Privilege> privileges = repository.query(params);
    privileges.getData().forEach(this::decorate);
    return privileges;
  }

  private Privilege decorate(Privilege privilege) {
    privilege.setResources(resourceService.load(privilege.getId()));
    return privilege;
  }
}
