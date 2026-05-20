package org.termx.core.sys.job.logger;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import org.termx.core.ApiError;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.job.JobLogService;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.sys.job.JobLog.JobDefinition;
import org.termx.sys.job.JobLogResponse;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ImportLogger {
  private final JobLogService jobLogService;

  public <T> JobLogResponse runJob(String type, T value, Function<T, ImportLog> function) {
    JobLogResponse job = createJob(type);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Job {} started", type);
        long start = System.currentTimeMillis();
        ImportLog importLog = function.apply(value);
        if (CollectionUtils.isNotEmpty(importLog.getErrors())) {
          log.info("Job {} finished with errors ({} seconds)", type, (System.currentTimeMillis() - start) / 1000);
        } else if (CollectionUtils.isNotEmpty(importLog.getWarnings())) {
          log.info("Job {} finished with warnings ({} seconds)", type, (System.currentTimeMillis() - start) / 1000);
        } else {
          log.info("Job {} finished ({} seconds)", type, (System.currentTimeMillis() - start) / 1000);
        }
        logImport(job.getJobId(), importLog);
      } catch (ApiClientException e) {
        log.error("Job {} resulted in ApiClientException: {}", type, e.getMessage(), e);
        logImport(job.getJobId(), e);
      } catch (Exception e) {
        // Pass the throwable as a logger argument so the stack trace lands in server logs —
        // the previous {@code e.getMessage()}-only form silently swallowed NPEs and other
        // exceptions whose message is null, leaving operators to debug "Exception: null".
        log.error("Job {} resulted in Exception: {}", type, e.getMessage(), e);
        // Preserve the message form previously written to the job log so existing UI/API
        // consumers keep working; the stack-trace remains in server logs only.
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        logImport(job.getJobId(), ApiError.TC200.toApiException(Map.of("type", type, "error", msg)));
      }
    }), VirtualThreadExecutor.get());
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
