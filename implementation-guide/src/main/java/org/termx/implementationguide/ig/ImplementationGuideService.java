package org.termx.implementationguide.ig;

import com.kodality.commons.model.QueryResult;
import org.termx.core.fhir.BaseFhirMapper;
import org.termx.implementationguide.ApiError;
import org.termx.implementationguide.ig.version.ImplementationGuideVersion;
import org.termx.implementationguide.ig.version.ImplementationGuideVersionQueryParams;
import org.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideService {
  private static final Pattern ID_REGEX = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

  private final ImplementationGuideRepository repository;
  private final ImplementationGuideVersionService versionService;

  public Optional<ImplementationGuide> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<ImplementationGuide> query(ImplementationGuideQueryParams params) {
    QueryResult<ImplementationGuide> result = repository.query(params);
    if (params.isDecorated()) {
      result.getData().forEach(this::decorate);
    }
    return result;
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
    if (!ID_REGEX.matcher(id).matches()) {
      throw ApiError.IG105.toApiException();
    }
  }

  private void decorate(ImplementationGuide ig) {
    ig.setVersions(versionService.query(new ImplementationGuideVersionQueryParams().setImplementationGuideIds(ig.getId()).all()).getData());
  }
}
