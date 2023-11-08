package com.kodality.termx.implementationguide.ig.version;

import com.kodality.termx.implementationguide.ApiError;
import com.kodality.termx.ts.PublicationStatus;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideVersionService {
  private final ImplementationGuideVersionRepository repository;

  @Transactional
  public void save(ImplementationGuideVersion version) {
    Optional<ImplementationGuideVersion> existingVersion = load(version.getImplementationGuide(), version.getVersion());
    if (version.getId() == null) {
      version.setId(existingVersion.map(ImplementationGuideVersion::getId).orElse(null));
    }
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.IG101.toApiException();
    }
    if (existingVersion.isPresent() && !existingVersion.get().getId().equals(version.getId())) {
      throw ApiError.IG102.toApiException(Map.of("version", existingVersion.get().getVersion()));
    }
    repository.save(version);
  }

  public Optional<ImplementationGuideVersion> load(String ig, String version) {
    return Optional.ofNullable(repository.load(ig, version));
  }


}
