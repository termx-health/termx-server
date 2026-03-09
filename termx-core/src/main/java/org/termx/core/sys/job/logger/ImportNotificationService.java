package org.termx.core.sys.job.logger;

import org.termx.core.sys.email.EmailService;
import org.termx.sys.ExecutionStatus;
import org.termx.sys.job.JobLog;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ImportNotificationService {
  private final EmailService emailService;
  
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public void sendImportCompletionNotification(JobLog jobLog) {
    if (!emailService.hasImportRecipients()) {
      log.debug("No import recipients configured, skipping notification for job {}", jobLog.getId());
      return;
    }

    if (!emailService.isConfigured() || !emailService.isEnabled()) {
      log.debug("Email not configured or disabled, skipping notification for job {}", jobLog.getId());
      return;
    }

    long start = System.currentTimeMillis();
    List<String> recipients = emailService.getImportRecipients();
    String jobType = jobLog.getDefinition().getType();
    String status = jobLog.getExecution().getStatus();
    
    log.info("Sending import notification for {} ({}) to {} recipient(s)", jobType, status, recipients.size());
    
    try {
      String subject = buildSubject(jobLog);
      String htmlBody = buildHtmlBody(jobLog);
      
      emailService.sendToMultiple(recipients, subject, htmlBody, true);
      
      log.info("Import notification sent ({} sec)", (System.currentTimeMillis() - start) / 1000);
    } catch (Exception e) {
      log.error("Failed to send import notification for job " + jobLog.getId(), e);
    }
  }

  private String buildSubject(JobLog jobLog) {
    String jobType = jobLog.getDefinition().getType();
    String status = jobLog.getExecution().getStatus();
    
    String statusText = switch (status) {
      case ExecutionStatus.COMPLETED -> "Completed";
      case ExecutionStatus.FAILED -> "Failed";
      case ExecutionStatus.WARNINGS -> "Completed with Warnings";
      default -> status;
    };
    
    String source = jobLog.getDefinition().getSource() != null 
        ? " (" + jobLog.getDefinition().getSource() + ")" 
        : "";
    
    return String.format("[TermX] %s%s - %s", jobType, source, statusText);
  }

  private String buildHtmlBody(JobLog jobLog) {
    String jobType = jobLog.getDefinition().getType();
    String source = jobLog.getDefinition().getSource();
    String status = jobLog.getExecution().getStatus();
    
    Duration duration = Duration.between(
        jobLog.getExecution().getStarted(), 
        jobLog.getExecution().getFinished()
    );
    
    String statusBadge = getStatusBadge(status);
    String summaryTable = buildSummaryTable(jobLog, duration);
    String detailsSection = buildDetailsSection(jobLog);
    
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
            .status-warnings { background-color: #ffc107; color: #000; }
            .summary-table { width: 100%%; border-collapse: collapse; margin: 20px 0; background-color: white; }
            .summary-table th { background-color: #e9ecef; padding: 12px; text-align: left; border: 1px solid #dee2e6; }
            .summary-table td { padding: 12px; border: 1px solid #dee2e6; }
            .details { margin-top: 20px; }
            .details-section { background-color: white; padding: 15px; margin: 10px 0; border-left: 4px solid #007bff; border-radius: 3px; }
            .details-section h3 { margin-top: 0; color: #007bff; font-size: 16px; }
            .message-list { margin: 0; padding-left: 20px; }
            .message-list li { margin: 5px 0; }
            .error-message { color: #dc3545; }
            .warning-message { color: #ff8c00; }
            .success-message { color: #28a745; }
            .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #dee2e6; color: #6c757d; font-size: 12px; }
          </style>
        </head>
        <body>
          <div class="header">
            <h1>TermX Import Notification</h1>
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
    String badgeClass = switch (status) {
      case ExecutionStatus.COMPLETED -> "status-completed";
      case ExecutionStatus.FAILED -> "status-failed";
      case ExecutionStatus.WARNINGS -> "status-warnings";
      default -> "status-completed";
    };
    
    String statusText = switch (status) {
      case ExecutionStatus.COMPLETED -> "✓ Completed";
      case ExecutionStatus.FAILED -> "✗ Failed";
      case ExecutionStatus.WARNINGS -> "⚠ Completed with Warnings";
      default -> status;
    };
    
    return "<div style='margin-bottom: 20px;'><span class='status-badge " + badgeClass + "'>" + statusText + "</span></div>";
  }

  private String buildSummaryTable(JobLog jobLog, Duration duration) {
    String jobType = escapeFormatString(jobLog.getDefinition().getType());
    String source = escapeFormatString(jobLog.getDefinition().getSource() != null ? jobLog.getDefinition().getSource() : "N/A");
    String jobId = escapeFormatString(String.valueOf(jobLog.getId()));
    String startedStr = escapeFormatString(jobLog.getExecution().getStarted().format(DATE_FORMATTER));
    String finishedStr = escapeFormatString(jobLog.getExecution().getFinished().format(DATE_FORMATTER));
    String durationStr = escapeFormatString(formatDuration(duration));
    
    int successCount = jobLog.getSuccesses() != null ? jobLog.getSuccesses().size() : 0;
    int warningCount = jobLog.getWarnings() != null ? jobLog.getWarnings().size() : 0;
    int errorCount = jobLog.getErrors() != null ? jobLog.getErrors().size() : 0;
    
    return """
        <table class="summary-table">
          <tr><th>Job Type</th><td>%s</td></tr>
          <tr><th>Source</th><td>%s</td></tr>
          <tr><th>Job ID</th><td>%s</td></tr>
          <tr><th>Started</th><td>%s</td></tr>
          <tr><th>Finished</th><td>%s</td></tr>
          <tr><th>Duration</th><td>%s</td></tr>
          <tr><th>Successes</th><td>%d</td></tr>
          <tr><th>Warnings</th><td>%d</td></tr>
          <tr><th>Errors</th><td>%d</td></tr>
        </table>
        """.formatted(jobType, source, jobId, startedStr, finishedStr, durationStr, 
                      successCount, warningCount, errorCount);
  }

  private String buildDetailsSection(JobLog jobLog) {
    StringBuilder details = new StringBuilder("<div class='details'>");
    
    if (CollectionUtils.isNotEmpty(jobLog.getErrors())) {
      details.append("""
          <div class="details-section">
            <h3>Errors</h3>
            <ul class="message-list">
          """);
      for (String error : jobLog.getErrors()) {
        details.append("<li class='error-message'>").append(escapeHtml(error)).append("</li>");
      }
      details.append("</ul></div>");
    }
    
    if (CollectionUtils.isNotEmpty(jobLog.getWarnings())) {
      details.append("""
          <div class="details-section">
            <h3>Warnings</h3>
            <ul class="message-list">
          """);
      for (String warning : jobLog.getWarnings()) {
        details.append("<li class='warning-message'>").append(escapeHtml(warning)).append("</li>");
      }
      details.append("</ul></div>");
    }
    
    if (CollectionUtils.isNotEmpty(jobLog.getSuccesses())) {
      details.append("""
          <div class="details-section">
            <h3>Success Messages</h3>
            <ul class="message-list">
          """);
      int count = 0;
      for (String success : jobLog.getSuccesses()) {
        if (count++ < 10) {
          details.append("<li class='success-message'>").append(escapeHtml(success)).append("</li>");
        } else {
          details.append("<li class='success-message'>... and ").append(jobLog.getSuccesses().size() - 10).append(" more</li>");
          break;
        }
      }
      details.append("</ul></div>");
    }
    
    details.append("</div>");
    return details.toString();
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
