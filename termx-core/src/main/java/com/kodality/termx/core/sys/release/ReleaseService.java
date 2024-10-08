package com.kodality.termx.core.sys.release;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.sys.checklist.ChecklistService;
import com.kodality.termx.core.sys.checklist.validaton.ChecklistValidationService;
import com.kodality.termx.core.sys.release.notes.ReleaseNotesService;
import com.kodality.termx.core.sys.release.resource.ReleaseResourceService;
import com.kodality.termx.core.sys.release.resource.providers.ReleaseResourceProvider;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import com.kodality.termx.sys.checklist.ChecklistValidationRequest;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseQueryParams;
import com.kodality.termx.sys.release.ReleaseResource;
import com.kodality.termx.ts.PublicationStatus;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ReleaseService {
  private final ReleaseRepository repository;
  private final ReleaseResourceService resourceService;
  private final ChecklistValidationService validationService;
  private final ChecklistService checklistService;
  private final List<ReleaseResourceProvider> resourceProviders;
  private final ReleaseNotesService releaseNotesService;

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

    if (PublicationStatus.active.equals(status)) {
      validateChecklist(id);
      currentRelease.getResources().forEach(r -> resourceProviders.forEach(provider -> provider.activate(r.getResourceType(), r.getResourceId(), r.getResourceVersion())));
      releaseNotesService.generateNotes(currentRelease);
    }

    if (status.equals(currentRelease.getStatus())) {
      log.warn("Release '{}' is already activated, skipping activation process.", currentRelease.getCode());
      return;
    }
    repository.changeStatus(id, status);
  }

  private void validateChecklist(Long releaseId) {
    List<ReleaseResource> resources = resourceService.loadAll(releaseId);
    resources.forEach(r -> validationService.runChecks(new ChecklistValidationRequest()
        .setResourceType(r.getResourceType())
        .setResourceId(r.getResourceId())
        .setResourceVersion(r.getResourceVersion())));
    List<Checklist> unaccomplishedChecks = resources.stream().flatMap(r -> checklistService.query(new ChecklistQueryParams()
            .setAssertionsDecorated(true)
            .setResourceId(r.getResourceId())
            .setResourceType(r.getResourceType())
            .setResourceVersion(r.getResourceVersion()).all()).getData().stream())
        .filter(checklist -> CollectionUtils.isEmpty(checklist.getAssertions()) || !checklist.getAssertions().get(0).isPassed()).toList();
    if (CollectionUtils.isNotEmpty(unaccomplishedChecks)) {
      throw ApiError.TC114.toApiException();
    }
  }
}
