package com.kodality.termserver.auth.privilegeresource;

import com.kodality.termserver.auth.PrivilegeResource;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PrivilegeResourceService {
  private final PrivilegeResourceRepository repository;

  @Transactional
  public void save(List<PrivilegeResource> resources, Long privilegeId) {
    repository.retain(resources, privilegeId);
    if (CollectionUtils.isNotEmpty(resources)) {
      resources.forEach(r -> repository.save(r, privilegeId));
    }
  }

  public List<PrivilegeResource> load(Long privilegeId) {
    return repository.load(privilegeId);
  }

  @Transactional
  public void delete(Long privilegeId) {
    repository.retain(new ArrayList<>(), privilegeId);
  }
}
