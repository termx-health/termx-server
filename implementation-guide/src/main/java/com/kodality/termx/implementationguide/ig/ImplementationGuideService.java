package com.kodality.termx.implementationguide.ig;

import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideService {
  private final ImplementationGuideRepository repository;
  private final ImplementationGuideVersionService versionService;

  @Transactional
  public void save(ImplementationGuide ig) {
    repository.save(ig);
  }

  @Transactional
  public void save(ImplementationGuideTransactionRequest request) {
    ImplementationGuide ig = request.getImplementationGuide();
    repository.save(ig);

    ImplementationGuideVersion version = request.getVersion();
    if (version != null) {
      version.setImplementationGuide(ig.getId());
      versionService.save(version);
    }
  }
}
