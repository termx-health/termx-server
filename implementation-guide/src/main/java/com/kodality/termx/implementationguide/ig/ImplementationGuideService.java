package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.implementationguide.ApiError;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideService {
  private final ImplementationGuideRepository repository;
  private final ImplementationGuideVersionService versionService;

  public Optional<ImplementationGuide> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<ImplementationGuide> query(ImplementationGuideQueryParams params) {
    return repository.query(params);
  }

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

  @Transactional
  public void cancel(String ig) {
    repository.cancel(ig);
  }

  @Transactional
  public void changeId(String currentId, String newId) {
    validateId(newId);
    repository.changeId(currentId, newId);
  }

  private void validateId(String id) {
    if (id.contains(BaseFhirMapper.SEPARATOR)) {
      throw ApiError.IG103.toApiException(Map.of("symbols", BaseFhirMapper.SEPARATOR));
    }
  }
}
