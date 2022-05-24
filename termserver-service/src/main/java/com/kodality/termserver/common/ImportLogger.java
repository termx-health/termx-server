package com.kodality.termserver.common;

import com.kodality.commons.util.MapUtil;
import com.kodality.termserver.job.JobLog.JobDefinition;
import com.kodality.termserver.job.JobLogResponse;
import com.kodality.termserver.job.JobLogService;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
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

  public void logImport(Long jobId, List<String> warnings) {
    logImport(jobId, warnings, null);
  }

  public void logImport(Long jobId, Throwable e) {
    logImport(jobId, null, e);
  }

  public void logImport(Long jobId, List<String> warnings, Throwable e) {
    jobLogService.finish(jobId, makeWarnings(warnings), makeErrors(e));
  }

  private Map<String, Object> makeWarnings(List<String> warnings) {
    if (CollectionUtils.isEmpty(warnings)) {
      return null;
    }
    return MapUtil.toMap("warnings", warnings);
  }

  private Map<String, Object> makeErrors(Throwable e) {
    if (e == null) {
      return null;
    }
    return MapUtil.toMap("errors", ExceptionUtils.getStackTrace(e));
  }

}
