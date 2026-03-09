package org.termx.core.sys.job;


import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import org.termx.core.sys.job.logger.ImportNotificationService;
import org.termx.sys.ExecutionStatus;
import org.termx.sys.job.JobLog;
import org.termx.sys.job.JobLog.JobDefinition;
import org.termx.sys.job.JobLogQueryParams;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class JobLogService {
  private final JobLogRepository jobLogRepository;
  private final ImportNotificationService importNotificationService;

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
    
    JobLog finishedJob = get(id);
    if (finishedJob != null && isImportJob(finishedJob.getDefinition().getType())) {
      try {
        importNotificationService.sendImportCompletionNotification(finishedJob);
      } catch (Exception e) {
        log.error("Failed to send import notification for job " + id, e);
      }
    }
  }

  private boolean isImportJob(String jobType) {
    if (jobType == null) {
      return false;
    }
    String lowerJobType = jobType.toLowerCase();
    return lowerJobType.contains("import") || lowerJobType.contains("sync");
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
