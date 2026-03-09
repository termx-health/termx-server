package org.termx.snomed.integration;

import org.termx.core.sys.email.EmailService;
import org.termx.snomed.client.SnowstormClient;
import org.termx.snomed.rf2.SnomedImportJob;
import org.termx.snomed.rf2.SnomedImportTracking;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SnomedImportPollingService {
  private final SnomedImportTrackingRepository trackingRepository;
  private final SnowstormClient snowstormClient;
  private final EmailService emailService;
  
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED");

  @Scheduled(fixedDelay = "30s", initialDelay = "1m")
  @Transactional
  public void pollPendingImports() {
    if (!emailService.hasImportRecipients()) {
      return;
    }

    try {
      List<SnomedImportTracking> pendingJobs = trackingRepository.loadPending();
      
      if (CollectionUtils.isEmpty(pendingJobs)) {
        return;
      }
      
      log.debug("Polling {} pending SNOMED import jobs", pendingJobs.size());
      
      for (SnomedImportTracking tracking : pendingJobs) {
        try {
          checkAndNotify(tracking);
        } catch (Exception e) {
          log.error("Error checking SNOMED import job " + tracking.getSnowstormJobId(), e);
        }
      }
    } catch (Exception e) {
      log.error("Error polling SNOMED import jobs", e);
    }
  }

  private void checkAndNotify(SnomedImportTracking tracking) {
    try {
      SnomedImportJob snowstormJob = snowstormClient.loadImportJob(tracking.getSnowstormJobId()).join();
      
      if (snowstormJob == null) {
        log.warn("SNOMED import job {} not found in Snowstorm", tracking.getSnowstormJobId());
        return;
      }
      
      String currentStatus = snowstormJob.getStatus();
      
      if (TERMINAL_STATUSES.contains(currentStatus) && !tracking.isNotified()) {
        tracking.setStatus(currentStatus);
        tracking.setFinished(OffsetDateTime.now());
        tracking.setErrorMessage(snowstormJob.getErrorMessage());
        tracking.setNotified(true);
        
        trackingRepository.save(tracking);
        
        long start = System.currentTimeMillis();
        List<String> recipients = emailService.getImportRecipients();
        log.info("Sending SNOMED import notification ({}) to {} recipient(s)", currentStatus, recipients.size());
        
        sendNotification(tracking, snowstormJob);
        
        log.info("SNOMED import notification sent ({} sec)", (System.currentTimeMillis() - start) / 1000);
      }
    } catch (Exception e) {
      log.error("Failed to check SNOMED job status for " + tracking.getSnowstormJobId(), e);
    }
  }

  private void sendNotification(SnomedImportTracking tracking, SnomedImportJob snowstormJob) {
    try {
      String subject = buildSubject(tracking, snowstormJob);
      String htmlBody = buildHtmlBody(tracking, snowstormJob);
      
      List<String> recipients = emailService.getImportRecipients();
      emailService.sendToMultiple(recipients, subject, htmlBody, true);
    } catch (Exception e) {
      log.error("Failed to send SNOMED import notification", e);
    }
  }

  private String buildSubject(SnomedImportTracking tracking, SnomedImportJob snowstormJob) {
    String statusText = "COMPLETED".equals(tracking.getStatus()) ? "Completed" : "Failed";
    String branchPath = tracking.getBranchPath() != null ? " (" + tracking.getBranchPath() + ")" : "";
    return String.format("[TermX] SNOMED Import%s - %s", branchPath, statusText);
  }

  private String buildHtmlBody(SnomedImportTracking tracking, SnomedImportJob snowstormJob) {
    Duration duration = tracking.getFinished() != null 
        ? Duration.between(tracking.getStarted(), tracking.getFinished())
        : Duration.ZERO;
    
    String statusBadge = getStatusBadge(tracking.getStatus());
    String summaryTable = buildSummaryTable(tracking, snowstormJob, duration);
    String detailsSection = buildDetailsSection(tracking, snowstormJob);
    
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 800px; margin: 0 auto; padding: 20px; }
            .header { background-color: #2c3e50; color: white; padding: 20px; border-radius: 5px 5px 0 0; }
            .header h1 { margin: 0; font-size: 24px; }
            .content { background-color: #f8f9fa; padding: 20px; border: 1px solid #dee2e6; border-top: none; }
            .status-badge { display: inline-block; padding: 5px 15px; border-radius: 20px; font-weight: bold; font-size: 14px; }
            .status-completed { background-color: #28a745; color: white; }
            .status-failed { background-color: #dc3545; color: white; }
            .summary-table { width: 100%%; border-collapse: collapse; margin: 20px 0; background-color: white; }
            .summary-table th { background-color: #e9ecef; padding: 12px; text-align: left; border: 1px solid #dee2e6; }
            .summary-table td { padding: 12px; border: 1px solid #dee2e6; }
            .details { margin-top: 20px; }
            .details-section { background-color: white; padding: 15px; margin: 10px 0; border-left: 4px solid #007bff; border-radius: 3px; }
            .details-section h3 { margin-top: 0; color: #007bff; font-size: 16px; }
            .error-message { color: #dc3545; padding: 10px; background-color: #f8d7da; border-radius: 3px; }
            .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #dee2e6; color: #6c757d; font-size: 12px; }
          </style>
        </head>
        <body>
          <div class="header">
            <h1>TermX SNOMED Import Notification</h1>
          </div>
          <div class="content">
            %s
            %s
            %s
          </div>
          <div class="footer">
            <p>This is an automated notification from TermX Terminology Server</p>
          </div>
        </body>
        </html>
        """.formatted(statusBadge, summaryTable, detailsSection);
  }

  private String getStatusBadge(String status) {
    String badgeClass = "COMPLETED".equals(status) ? "status-completed" : "status-failed";
    String statusText = "COMPLETED".equals(status) ? "✓ Completed" : "✗ Failed";
    
    return "<div style='margin-bottom: 20px;'><span class='status-badge " + badgeClass + "'>" + statusText + "</span></div>";
  }

  private String buildSummaryTable(SnomedImportTracking tracking, SnomedImportJob snowstormJob, Duration duration) {
    String snowstormJobId = escapeFormatString(tracking.getSnowstormJobId());
    String branchPath = escapeFormatString(tracking.getBranchPath() != null ? tracking.getBranchPath() : "N/A");
    String importType = escapeFormatString(tracking.getType() != null ? tracking.getType() : "N/A");
    String startedStr = escapeFormatString(tracking.getStarted().format(DATE_FORMATTER));
    String finishedStr = escapeFormatString(tracking.getFinished() != null ? tracking.getFinished().format(DATE_FORMATTER) : "N/A");
    String durationStr = escapeFormatString(formatDuration(duration));
    String moduleIds = escapeFormatString(CollectionUtils.isNotEmpty(snowstormJob.getModuleIds()) 
        ? String.join(", ", snowstormJob.getModuleIds())
        : "N/A");
    int moduleCount = CollectionUtils.isEmpty(snowstormJob.getModuleIds()) ? 0 : snowstormJob.getModuleIds().size();
    
    return """
        <table class="summary-table">
          <tr><th>Job Type</th><td>SNOMED Import</td></tr>
          <tr><th>Snowstorm Job ID</th><td>%s</td></tr>
          <tr><th>Branch Path</th><td>%s</td></tr>
          <tr><th>Import Type</th><td>%s</td></tr>
          <tr><th>Started</th><td>%s</td></tr>
          <tr><th>Finished</th><td>%s</td></tr>
          <tr><th>Duration</th><td>%s</td></tr>
          <tr><th>Modules Imported</th><td>%d</td></tr>
          <tr><th>Module IDs</th><td>%s</td></tr>
        </table>
        """.formatted(
            snowstormJobId,
            branchPath,
            importType,
            startedStr,
            finishedStr,
            durationStr,
            moduleCount,
            moduleIds
        );
  }

  private String buildDetailsSection(SnomedImportTracking tracking, SnomedImportJob snowstormJob) {
    if (tracking.getErrorMessage() != null || snowstormJob.getErrorMessage() != null) {
      String errorMsg = tracking.getErrorMessage() != null ? tracking.getErrorMessage() : snowstormJob.getErrorMessage();
      return """
          <div class="details">
            <div class="details-section">
              <h3>Error Details</h3>
              <div class="error-message">%s</div>
            </div>
          </div>
          """.formatted(escapeHtml(errorMsg));
    }
    return "";
  }

  private String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    if (seconds < 60) {
      return seconds + " seconds";
    } else if (seconds < 3600) {
      long minutes = seconds / 60;
      long remainingSeconds = seconds % 60;
      return String.format("%d min %d sec", minutes, remainingSeconds);
    } else {
      long hours = seconds / 3600;
      long minutes = (seconds % 3600) / 60;
      return String.format("%d hr %d min", hours, minutes);
    }
  }

  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private String escapeFormatString(String text) {
    if (text == null) {
      return "";
    }
    // Escape % signs for String.format() by doubling them
    return text.replace("%", "%%");
  }
}
