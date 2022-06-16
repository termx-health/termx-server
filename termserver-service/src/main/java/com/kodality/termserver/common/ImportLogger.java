package com.kodality.termserver.common;

import com.kodality.termserver.job.JobLog.JobDefinition;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.JobLogService;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

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

  public void logImport(Long jobId, List<String> warnings, List<String> successes) {
    logImport(jobId, warnings, successes, null);
  }

  public void logImport(Long jobId, Throwable e) {
    logImport(jobId, null, null, e);
  }

  public void logImport(Long jobId, List<String> warnings, List<String> successes, Throwable e) {
    jobLogService.finish(jobId, CollectionUtils.isEmpty(warnings) ? null : warnings, CollectionUtils.isEmpty(successes) ? null : successes, e == null ? null :
        List.of(ExceptionUtils.getStackTrace(e)));
  }

}
