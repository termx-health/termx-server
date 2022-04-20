package com.kodality.termserver.job;


import com.kodality.termserver.commons.model.exception.NotFoundException;
import com.kodality.termserver.commons.model.model.QueryResult;
import com.kodality.termserver.job.JobLog.JobDefinition;
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
    JobLog jobLog = get(id);
    validate(id);
    if (jobLog == null) {
      jobLogRepository.finish(id);
      return;
    }
    jobLog.setId(id);
    String status = resolveStatus(jobLog);
    jobLogRepository.finish(jobLog, status);
  }

  private void validate(Long id) {
    JobLog jobLog = get(id);
    if (jobLog == null) {
      throw new NotFoundException("Job log not found " + id);
    }
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
