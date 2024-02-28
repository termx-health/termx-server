package com.kodality.termx.uam.privilege;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.auth.PrivilegeQueryParams;
import com.kodality.termx.uam.privilegeresource.PrivilegeResourceService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PrivilegeService {
  private final PrivilegeRepository repository;
  private final PrivilegeResourceService resourceService;
  private final List<PrivilegeDataHandler> handlers;

  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return repository.query(params).map(this::decorate);
  }

  public Privilege load(Long id) {
    return decorate(repository.load(id));
  }

  private Privilege decorate(Privilege privilege) {
    return privilege.setResources(resourceService.load(privilege.getId()));
  }

  @Transactional
  public void save(Privilege privilege) {
    repository.save(privilege);
    resourceService.save(privilege.getResources(), privilege.getId());

    handlers.forEach(h -> h.afterPrivilegeSave(privilege));
  }

  @Transactional
  public void delete(Long id) {
    Privilege persisted = load(id);
    resourceService.delete(persisted.getId());
    repository.delete(persisted.getId());

    handlers.forEach(h -> h.afterPrivilegeDelete(persisted));
  }
}
