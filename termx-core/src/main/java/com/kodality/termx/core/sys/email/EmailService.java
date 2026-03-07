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

  private final Optional<EmailSender> emailSender;

  public EmailService(Optional<EmailSender> emailSender) {
    this.emailSender = emailSender;
  }

  public void sendEmail(String to, String subject, String body) {
    sendEmail(to, subject, body, false);
  }

  public void sendEmail(String to, String subject, String body, boolean html) {
    if (!isConfigured()) {
      log.warn("SMTP not configured, skipping email to: {}", to);
      return;
    }

    if (!emailEnabled.orElse(false)) {
      log.info("Email sending is disabled, skipping email to: {}", to);
      return;
    }

    // Workaround for Micronaut AOP proxy issue with EmailSender.send(Builder)
    // Use direct Jakarta Mail API instead
    try {
      java.util.Properties props = new java.util.Properties();
      props.put("mail.smtp.host", smtpHost.orElse("localhost"));
      props.put("mail.smtp.port", smtpPort.orElse(25).toString());
      props.put("mail.smtp.auth", smtpAuth.orElse(false).toString());
      
      if (smtpAuth.orElse(false)) {
        props.put("mail.smtp.starttls.enable", "true");
      }
      
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
      message.setRecipients(jakarta.mail.Message.RecipientType.TO, 
          jakarta.mail.internet.InternetAddress.parse(to));
      message.setSubject(subject);
      
      if (html) {
        message.setContent(body, "text/html; charset=utf-8");
      } else {
        message.setText(body, "utf-8");
      }
      
      jakarta.mail.Transport.send(message);
      log.info("Email sent successfully to: {}", to);
    } catch (Exception e) {
      log.error("Failed to send email to: " + to, e);
      throw new RuntimeException("Failed to send email", e);
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
}
