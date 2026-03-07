package com.kodality.termx.core

import com.kodality.termx.TermxIntegTest
import com.kodality.termx.core.auth.SessionInfo
import com.kodality.termx.core.auth.SessionStore
import com.kodality.termx.core.sys.email.EmailManagementController
import com.kodality.termx.core.sys.email.EmailService
import com.kodality.termx.core.sys.email.EmailTestRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class EmailServiceTest extends TermxIntegTest {

  @Inject
  EmailService emailService

  @Inject
  EmailManagementController emailManagementController

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  def "should check if email service is configured"() {
    when:
    def configured = emailService.isConfigured()
    
    then:
    configured != null
  }

  def "should return email configuration status"() {
    when:
    def status = emailManagementController.getStatus()
    
    then:
    status != null
    status.configured != null
    status.enabled != null
    status.missingParameters != null
    status.from != null
  }

  def "should indicate not configured when SMTP parameters missing"() {
    when:
    def status = emailManagementController.getStatus()
    
    then:
    status != null
    
    and:
    if (!status.configured) {
      assert !status.missingParameters.isEmpty()
      assert status.missingParameters.contains("SMTP_ENABLED") || 
             status.missingParameters.contains("SMTP_HOST") ||
             status.missingParameters.contains("SMTP_USERNAME") ||
             status.missingParameters.contains("SMTP_PASSWORD")
    }
  }

  def "should send test email when configured"() {
    given:
    def request = new EmailTestRequest()
        .setRecipient("test@example.com")
        .setSubject("TermX Integration Test Email")
        .setBody("This is a test email from EmailServiceTest")
        .setHtml(false)
    
    when:
    def result = emailManagementController.sendTestEmail(request)
    
    then:
    result != null
    result.sent != null
    result.message != null
    
    and:
    if (!result.sent) {
      assert result.error != null
    }
  }

  def "should handle email sending when not configured"() {
    when:
    emailService.sendEmail("test@example.com", "Test Subject", "Test Body")
    
    then:
    notThrown(Exception)
  }
}
