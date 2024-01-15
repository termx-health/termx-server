package com.kodality.termx.core.sys.release;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseQueryParams;
import com.kodality.termx.ts.PublicationStatus;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ReleaseService {
  private final ReleaseRepository repository;

  @Transactional
  public Release save(Release release) {
    validate(release);
    prepare(release);
    repository.save(release);
    return release;
  }

  private void validate(Release release) {
    Release currentRelease = repository.load(release.getCode());
    if (currentRelease != null && !currentRelease.getId().equals(release.getId())) {
      throw ApiError.TC110.toApiException(Map.of("code", release.getCode()));
    }
  }

  private void prepare(Release release) {
    if (StringUtils.isEmpty(release.getStatus())) {
      release.setStatus(PublicationStatus.draft);
    }
  }
  public Release load(Long id) {
    return repository.load(id);
  }
  public Release load(String code) {
    return repository.load(code);
  }

  public QueryResult<Release> query(ReleaseQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void changeStatus(Long id, String status) {
    Release currentRelease = load(id);
    if (status.equals(currentRelease.getStatus())) {
      log.warn("Release '{}' is already activated, skipping activation process.", currentRelease.getCode());
      return;
    }
    repository.changeStatus(id, status);
  }
}
