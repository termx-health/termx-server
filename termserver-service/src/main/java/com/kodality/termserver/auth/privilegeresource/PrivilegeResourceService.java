package com.kodality.termserver.auth.privilegeresource;

import com.kodality.termserver.auth.PrivilegeResource;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PrivilegeResourceService {
  private final PrivilegeResourceRepository repository;

  public List<PrivilegeResource> load(Long privilegeId) {
    return repository.load(privilegeId);
  }

  @Transactional
  public void save(List<PrivilegeResource> resources, Long privilegeId) {
    repository.retain(resources, privilegeId);
    if (CollectionUtils.isEmpty(resources)) {
      resources.forEach(r -> repository.save(r, privilegeId));
    }
  }
}
