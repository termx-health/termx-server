package com.kodality.termx.core.sys.release.resource;

import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.release.ReleaseResource;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ReleaseResourceService {
  private final ReleaseResourceRepository repository;
  @Transactional
  public void save(Long releaseId, ReleaseResource resource) {
    Optional<ReleaseResource> exitingResource = load(releaseId, resource.getResourceType(), resource.getResourceId(), resource.getResourceVersion());
    if (exitingResource.isPresent() && !exitingResource.get().getId().equals(resource.getId())) {
      throw ApiError.TC115.toApiException();
    }
    repository.save(releaseId, resource);
  }

  @Transactional
  public void cancel(Long releaseId, Long resourceId) {
    repository.cancel(releaseId, resourceId);
  }

  public List<ReleaseResource> loadAll(Long releaseId) {
    return repository.loadAll(releaseId);
  }

  public Optional<ReleaseResource> load(Long releaseId, String resourceType, String resourceId, String resourceVersion) {
    return Optional.ofNullable(repository.load(releaseId, resourceType, resourceId, resourceVersion));
  }

}
