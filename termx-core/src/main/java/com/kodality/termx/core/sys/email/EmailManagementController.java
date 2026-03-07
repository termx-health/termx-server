package com.kodality.termx.core.sys.email;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/management/email")
@RequiredArgsConstructor
public class EmailManagementController {
  private final EmailService emailService;

  @Get("/status")
  public EmailConfigStatus getStatus() {
    EmailConfigStatus status = new EmailConfigStatus();
    status.setConfigured(emailService.isConfigured());
    status.setEnabled(emailService.isEnabled());
    status.setMissingParameters(emailService.getMissingConfiguration());
    status.setFrom(emailService.getFrom());
    emailService.getSmtpHost().ifPresent(status::setSmtpHost);
    emailService.getSmtpPort().ifPresent(status::setSmtpPort);
    return status;
  }

  @Post("/test")
  @Requires(property = "auth.dev.allowed", value = StringUtils.TRUE)
  public EmailTestResult sendTestEmail(@Valid @Body EmailTestRequest request) {
    EmailTestResult result = new EmailTestResult();
    
    if (!emailService.isConfigured()) {
      result.setSent(false);
      result.setError("SMTP not configured. Missing parameters: " + String.join(", ", emailService.getMissingConfiguration()));
      result.setMessage("Email configuration incomplete");
      return result;
    }

    try {
      emailService.sendEmail(request.getRecipient(), request.getSubject(), request.getBody(), request.isHtml());
      result.setSent(true);
      result.setMessage("Test email sent successfully to " + request.getRecipient());
    } catch (Exception e) {
      result.setSent(false);
      result.setError(e.getMessage());
      result.setMessage("Failed to send test email");
    }

    return result;
  }
}
