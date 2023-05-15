package com.kodality.termserver.job.logger;

import com.kodality.commons.exception.ApiException;
import com.kodality.termserver.job.JobLog.JobDefinition;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.JobLogService;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringSubstitutor;

@Singleton
@RequiredArgsConstructor
public class ImportLogger {
  private final JobLogService jobLogService;

  public JobLogResponse createJob(String type) {
    return createJob(null, type);
  }

  public JobLogResponse createJob(String source, String type) {
    JobDefinition jobDefinition = new JobDefinition();
    jobDefinition.setType(type);
    jobDefinition.setSource(source);
    Long id = jobLogService.create(jobDefinition);
    return new JobLogResponse(id);
  }

  public void logImport(Long jobId) {
    jobLogService.finish(jobId);
  }

  public void logImport(Long jobId, List<String> successes, List<String> warnings) {
    logImport(jobId, successes, warnings, null);
  }

  public void logImport(Long jobId, ApiException e) {
    logImport(jobId, null, null, e);
  }

  public void logImport(Long jobId, List<String> successes, List<String> warnings, ApiException e) {
    successes = CollectionUtils.isEmpty(successes) ? null : successes;
    warnings = CollectionUtils.isEmpty(warnings) ? null : warnings;
    if (e != null && CollectionUtils.isNotEmpty(e.getIssues())) {
      e.getIssues().forEach(issue -> issue.setMessage(StringSubstitutor.replace(issue.getMessage(), issue.getParams(), "{{", "}}")));
    }
    List<String> errors = e == null ? null : e.getIssues().stream().map(i -> ExceptionUtils.getMessage(new ApiException(e.getHttpStatus(), i))).toList();
    jobLogService.finish(jobId, successes, warnings, errors);
  }

}
