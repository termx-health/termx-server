package com.kodality.termx.core.sys.email;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.email.Email;
import io.micronaut.email.EmailSender;
import io.micronaut.email.MultipartBody;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@SuppressWarnings({"rawtypes", "unchecked"})
public class EmailService {
  
  @Property(name = "micronaut.email.enabled")
  private Optional<Boolean> emailEnabled;
  
  @Property(name = "javamail.properties.mail.smtp.host")
  private Optional<String> smtpHost;
  
  @Property(name = "javamail.authentication.username")
  private Optional<String> smtpUsername;
  
  @Property(name = "javamail.authentication.password")
  private Optional<String> smtpPassword;
  
  @Property(name = "micronaut.email.from.email")
  private Optional<String> smtpFrom;
  
  @Property(name = "javamail.properties.mail.smtp.port")
  private Optional<Integer> smtpPort;

  @Property(name = "javamail.properties.mail.smtp.auth")
  private Optional<Boolean> smtpAuth;

  @Property(name = "javamail.properties.mail.smtp.starttls.enable")
  private Optional<Boolean> smtpStartTls;

  @Property(name = "micronaut.email.import.recipients")
  private Optional<String> importRecipients;

  private final Optional<EmailSender> emailSender;

  public EmailService(Optional<EmailSender> emailSender) {
    this.emailSender = emailSender;
  }

  public void sendEmail(String to, String subject, String body) {
    sendEmail(to, subject, body, false);
  }

  public void sendEmail(String to, String subject, String body, boolean html) {
    sendEmail(List.of(to), subject, body, html);
  }

  public void sendToMultiple(List<String> recipients, String subject, String body, boolean html) {
    sendEmail(recipients, subject, body, html);
  }

  private void sendEmail(List<String> recipients, String subject, String body, boolean html) {
    if (recipients == null || recipients.isEmpty()) {
      log.debug("No recipients specified, skipping email");
      return;
    }

    if (!isConfigured()) {
      log.warn("SMTP not configured, skipping email to: {}", recipients);
      return;
    }

    if (!emailEnabled.orElse(false)) {
      log.info("Email sending is disabled, skipping email to: {}", recipients);
      return;
    }

    try {
      java.util.Properties props = new java.util.Properties();
      props.put("mail.smtp.host", smtpHost.orElse("localhost"));
      props.put("mail.smtp.port", smtpPort.orElse(25).toString());
      props.put("mail.smtp.auth", smtpAuth.orElse(false).toString());
      props.put("mail.smtp.connectiontimeout", "30000");
      props.put("mail.smtp.timeout", "30000");
      props.put("mail.smtp.writetimeout", "30000");
      
      // Use proper FQDN for EHLO instead of "localhost" - required by Google and other SMTP relays
      props.put("mail.smtp.localhost", smtpFrom.map(f -> {
        String[] parts = f.split("@");
        return parts.length > 1 ? parts[1] : "termx.org";
      }).orElse("termx.org"));
      
      // STARTTLS is independent of authentication
      if (smtpStartTls.orElse(false)) {
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.ssl.trust", smtpHost.orElse("*"));
        log.debug("STARTTLS enabled for host: {}", smtpHost.orElse("localhost"));
      }
      
      log.info("SMTP Connection - host: {}, port: {}, auth: {}, starttls: {}", 
          smtpHost.orElse("localhost"), smtpPort.orElse(25), smtpAuth.orElse(false), smtpStartTls.orElse(false));
      
      jakarta.mail.Session session;
      if (smtpAuth.orElse(false) && smtpUsername.isPresent() && smtpPassword.isPresent()) {
        session = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
          protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
            return new jakarta.mail.PasswordAuthentication(smtpUsername.get(), smtpPassword.get());
          }
        });
      } else {
        session = jakarta.mail.Session.getInstance(props);
      }
      
      jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(session);
      message.setFrom(new jakarta.mail.internet.InternetAddress(smtpFrom.orElse("noreply@termx.org")));
      
      String recipientsStr = String.join(",", recipients);
      message.setRecipients(jakarta.mail.Message.RecipientType.TO, 
          jakarta.mail.internet.InternetAddress.parse(recipientsStr));
      message.setSubject(subject);
      
      if (html) {
        message.setContent(body, "text/html; charset=utf-8");
      } else {
        message.setText(body, "utf-8");
      }
      
      log.info("Attempting to send email to {} recipient(s)...", recipients.size());
      jakarta.mail.Transport transport = session.getTransport("smtp");
      try {
        transport.connect();
        transport.sendMessage(message, message.getAllRecipients());
        log.info("Email sent successfully to: {}", recipientsStr);
      } finally {
        transport.close();
      }
    } catch (org.eclipse.angus.mail.smtp.SMTPSendFailedException e) {
      String errorMessage = parseSmtpError(e);
      log.error("SMTP Error: {}", errorMessage);
      throw new RuntimeException("Failed to send email: " + errorMessage);
    } catch (jakarta.mail.MessagingException e) {
      String errorMessage = parseMessagingError(e);
      log.error("Email Error: {}", errorMessage);
      throw new RuntimeException("Failed to send email: " + errorMessage);
    } catch (Exception e) {
      log.error("Failed to send email to {}: {}", recipients, e.getMessage());
      throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
    }
  }

  private String parseSmtpError(org.eclipse.angus.mail.smtp.SMTPSendFailedException e) {
    String message = e.getMessage();
    if (message == null) {
      return "Unknown SMTP error";
    }
    
    // Extract first line of SMTP error for cleaner logging
    String[] lines = message.split("\n");
    String firstLine = lines[0].trim();
    
    // Check for common patterns
    if (message.contains("Invalid credentials for relay")) {
      return "SMTP Relay Authentication Failed - FROM domain not authorized for your IP. " +
             "Update SMTP_FROM to use a domain registered in your SMTP relay service. " +
             "Error: " + firstLine;
    } else if (message.contains("Authentication failed") || message.contains("535")) {
      return "SMTP Authentication Failed - Invalid username/password. Error: " + firstLine;
    } else if (message.contains("Relay access denied") || message.contains("554")) {
      return "SMTP Relay Denied - Configure relay permissions or enable authentication. Error: " + firstLine;
    } else {
      return firstLine;
    }
  }

  private String parseMessagingError(jakarta.mail.MessagingException e) {
    String message = e.getMessage();
    if (message == null) {
      return "Unknown messaging error";
    }
    
    if (message.contains("[EOF]")) {
      return "SMTP Connection Error (EOF) - Check host/port configuration and STARTTLS settings";
    } else if (message.contains("Connection refused")) {
      return "SMTP Connection Refused - Verify host and port are correct and accessible";
    } else if (message.contains("Connection timed out")) {
      return "SMTP Connection Timeout - Host unreachable or port blocked by firewall";
    } else {
      return message;
    }
  }

  public boolean isConfigured() {
    if (!emailEnabled.orElse(false)) {
      return false;
    }
    
    if (smtpHost.filter(StringUtils::isNotBlank).isEmpty()) {
      return false;
    }
    
    if (emailSender.isEmpty()) {
      return false;
    }
    
    // Only check authentication if SMTP auth is enabled
    if (smtpAuth.orElse(true)) {
      return smtpUsername.filter(StringUtils::isNotBlank).isPresent() &&
          smtpPassword.filter(StringUtils::isNotBlank).isPresent();
    }
    
    return true;
  }

  public List<String> getMissingConfiguration() {
    List<String> missing = new ArrayList<>();
    
    if (!emailEnabled.orElse(false)) {
      missing.add("SMTP_ENABLED");
      return missing;
    }
    
    if (smtpHost.filter(StringUtils::isNotBlank).isEmpty()) {
      missing.add("SMTP_HOST");
    }
    
    // Only check authentication if SMTP auth is enabled
    if (smtpAuth.orElse(true)) {
      if (smtpUsername.filter(StringUtils::isNotBlank).isEmpty()) {
        missing.add("SMTP_USERNAME");
      }
      if (smtpPassword.filter(StringUtils::isNotBlank).isEmpty()) {
        missing.add("SMTP_PASSWORD");
      }
    }
    
    if (emailSender.isEmpty()) {
      missing.add("EmailSender bean (check javamail.properties.* configuration)");
    }
    
    return missing;
  }

  public String getFrom() {
    return smtpFrom.orElse("noreply@termx.org");
  }

  public Optional<String> getSmtpHost() {
    return smtpHost;
  }

  public Optional<Integer> getSmtpPort() {
    return smtpPort;
  }

  public boolean isEnabled() {
    return emailEnabled.orElse(false);
  }

  public List<String> getImportRecipients() {
    return importRecipients
        .filter(StringUtils::isNotBlank)
        .map(s -> List.of(s.split(",")))
        .orElse(List.of())
        .stream()
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .toList();
  }

  public boolean hasImportRecipients() {
    return !getImportRecipients().isEmpty();
  }
}
