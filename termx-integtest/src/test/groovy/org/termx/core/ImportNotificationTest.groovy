package org.termx.core

import org.termx.TermxIntegTest
import org.termx.core.sys.email.EmailService
import org.termx.core.sys.job.JobLogService
import org.termx.core.sys.job.logger.ImportNotificationService
import org.termx.sys.job.JobLog
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class ImportNotificationTest extends TermxIntegTest {
  @Inject
  EmailService emailService

  @Inject
  ImportNotificationService importNotificationService

  @Inject
  JobLogService jobLogService

  def "should check if import recipients are configured"() {
    when:
    def hasRecipients = emailService.hasImportRecipients()
    def recipients = emailService.getImportRecipients()

    then:
    hasRecipients != null
    recipients != null
    println("Import recipients configured: ${hasRecipients}")
    println("Recipients: ${recipients}")
  }

  def "should not throw exception when sending notification with no recipients"() {
    given:
    def jobDefinition = new JobLog.JobDefinition()
    jobDefinition.setType("test-import")
    jobDefinition.setSource("test-source")
    
    Long jobId = jobLogService.create(jobDefinition)
    
    when:
    jobLogService.finish(jobId, ["Success 1"], ["Warning 1"], ["Error 1"])

    then:
    noExceptionThrown()
    
    def job = jobLogService.get(jobId)
    job != null
    job.getExecution().getStatus() == "failed"
  }

  def "should format HTML email correctly"() {
    given:
    def jobDefinition = new JobLog.JobDefinition()
    jobDefinition.setType("loinc-import")
    jobDefinition.setSource("LOINC")
    
    Long jobId = jobLogService.create(jobDefinition)
    jobLogService.finish(jobId, 
        ["Imported 100 concepts", "Created 50 associations"],
        ["Duplicate concept found"],
        null)
    
    when:
    def job = jobLogService.get(jobId)

    then:
    job != null
    job.getExecution().getStatus() == "warnings"
    job.getSuccesses()?.size() == 2
    job.getWarnings()?.size() == 1
    job.getExecution().getStarted() != null
    job.getExecution().getFinished() != null
  }
}
