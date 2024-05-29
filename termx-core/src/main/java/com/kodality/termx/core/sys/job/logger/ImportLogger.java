package com.kodality.termx.core.sys.job.logger;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.job.JobLog.JobDefinition;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.core.sys.job.JobLogService;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@RequiredArgsConstructor
public class ImportLogger {
  private final JobLogService jobLogService;

  public <T> JobLogResponse runJob(String type, T value, Function<T, ImportLog> function) {
    JobLogResponse job = createJob(type);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ImportLog log = function.apply(value);
        logImport(job.getJobId(), log);
      } catch (ApiClientException e) {
        logImport(job.getJobId(), e);
      } catch (Exception e) {
        logImport(job.getJobId(), ApiError.TC200.toApiException(Map.of("type", type, "error", e.getMessage())));
      }
    }));
    return job;
  }

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

  public void logImport(Long jobId, ImportLog log) {
    logImport(jobId, log.getSuccesses(), log.getWarnings(), log.getErrors());
  }

  public void logImport(Long jobId, List<String> successes, List<String> warnings) {
    logImport(jobId, successes, warnings, List.of());
  }

  public void logImport(Long jobId, List<String> successes, List<String> warnings, List<String> errors) {
    successes = CollectionUtils.isEmpty(successes) ? null : successes;
    warnings = CollectionUtils.isEmpty(warnings) ? null : warnings;
    errors = CollectionUtils.isEmpty(errors) ? null : errors;
    jobLogService.finish(jobId, successes, warnings, errors);
  }

  public void logImport(Long jobId, ApiException e) {
    logImport(jobId, null, null, e);
  }

  public void logImport(Long jobId, List<String> successes, List<String> warnings, ApiException e) {
    if (e != null && CollectionUtils.isNotEmpty(e.getIssues())) {
      e.getIssues().forEach(issue -> issue.setMessage(issue.formattedMessage()));
    }
    List<String> errors = e == null ? null : e.getIssues().stream().map(i -> ExceptionUtils.getMessage(new ApiException(e.getHttpStatus(), i))).toList();

    logImport(jobId, successes, warnings, errors);
  }

}
