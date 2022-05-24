package com.kodality.termserver.job;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.job.JobLog.JobDefinition;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class JobLogService {
  private final JobLogRepository jobLogRepository;

  public QueryResult<JobLog> search(JobLogQueryParams params) {
    return jobLogRepository.search(params);
  }

  public JobLog get(Long id) {
    return jobLogRepository.load(id);
  }

  @Transactional
  public Long create(JobDefinition definition) {
    return jobLogRepository.create(definition);
  }

  @Transactional
  public void finish(Long id) {
    finish(id, null, null);
  }
  @Transactional
  public void finish(Long id, Map<String, Object> warnings, Map<String, Object> errors) {
    JobLog jobLog = get(id);
    if (jobLog == null) {
      throw new NotFoundException("Job log not found " + id);
    }
    jobLog.setId(id);
    jobLog.setErrors(errors);
    jobLog.setWarnings(warnings);
    String status = resolveStatus(jobLog);
    jobLogRepository.finish(jobLog, status);
  }

  private String resolveStatus(JobLog jobLog) {
    if (jobLog.getErrors() != null && !jobLog.getErrors().isEmpty()) {
      return JobExecutionStatus.FAILED;
    }
    if (jobLog.getWarnings() != null && !jobLog.getWarnings().isEmpty()) {
      return JobExecutionStatus.WARNINGS;
    }
    return JobExecutionStatus.COMPLETED;
  }
}
