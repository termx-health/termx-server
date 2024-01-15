package com.kodality.termx.core.sys.release.resource;

import com.kodality.termx.sys.release.ReleaseResource;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ReleaseResourceService {
  private final ReleaseResourceRepository repository;
  @Transactional
  public void save(Long releaseId, ReleaseResource resource) {
     repository.save(releaseId, resource);
  }

  @Transactional
  public void cancel(Long releaseId, Long resourceId) {
    repository.cancel(releaseId, resourceId);
  }

  public List<ReleaseResource> loadAll(Long releaseId) {
    return repository.loadAll(releaseId);
  }

}
