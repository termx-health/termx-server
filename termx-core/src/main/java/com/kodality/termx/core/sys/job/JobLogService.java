package com.kodality.termx.core.sys.job;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.sys.ExecutionStatus;
import com.kodality.termx.sys.job.JobLog;
import com.kodality.termx.sys.job.JobLog.JobDefinition;
import com.kodality.termx.sys.job.JobLogQueryParams;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class JobLogService {
  private final JobLogRepository jobLogRepository;

  @Transactional
  public Long create(JobDefinition definition) {
    return jobLogRepository.create(definition);
  }

  public QueryResult<JobLog> query(JobLogQueryParams params) {
    return jobLogRepository.query(params);
  }

  public JobLog get(Long id) {
    return jobLogRepository.load(id);
  }

  @Transactional
  public void finish(Long id) {
    finish(id, null, null, null);
  }

  @Transactional
  public void finish(Long id, List<String> successes, List<String> warnings, List<String> errors) {
    JobLog jobLog = get(id);
    if (jobLog == null) {
      throw new NotFoundException("Job log not found " + id);
    }
    jobLog.setId(id);
    jobLog.setSuccesses(successes);
    jobLog.setWarnings(warnings);
    jobLog.setErrors(errors);
    String status = resolveStatus(jobLog);
    jobLogRepository.finish(jobLog, status);
  }

  private String resolveStatus(JobLog jobLog) {
    if (jobLog.getErrors() != null && !jobLog.getErrors().isEmpty()) {
      return ExecutionStatus.FAILED;
    }
    if (jobLog.getWarnings() != null && !jobLog.getWarnings().isEmpty()) {
      return ExecutionStatus.WARNINGS;
    }
    return ExecutionStatus.COMPLETED;
  }
}
