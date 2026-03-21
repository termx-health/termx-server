package org.termx.terminology.fileimporter.codesystem

import com.kodality.commons.model.QueryResult
import org.termx.terminology.ApiError
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportProcessor
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.validator.CodeSystemValidationService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import spock.lang.Specification

import java.util.Optional

import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystem
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingCodeSystemVersion
import static org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty

/**
 * Tests "validate data only" (dry run): no persistent {@link org.termx.terminology.terminology.codesystem.CodeSystemImportService#importCodeSystem}
 * on the outer transaction; diff comes from {@link CodeSystemFileImportDryRunService}.
 */
class CodeSystemFileImportServiceDryRunTest extends Specification {

  def dryRunService = Mock(CodeSystemFileImportDryRunService)
  def codeSystemFhirImportService = Mock(CodeSystemFhirImportService)
  def codeSystemImportService = Mock(CodeSystemImportService)
  def codeSystemService = Mock(CodeSystemService)
  def codeSystemValidationService = Mock(CodeSystemValidationService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def conceptService = Mock(ConceptService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)

  CodeSystemFileImportService service = new CodeSystemFileImportService(
      dryRunService,
      codeSystemFhirImportService,
      codeSystemImportService,
      codeSystemService,
      codeSystemValidationService,
      codeSystemVersionService,
      conceptService,
      valueSetVersionConceptService,
      Optional.empty()
  )

  def "dry run should set diff from dry run service and not call persistent importCodeSystem"() {
    given:
    def csv = """code,display#en
c1,Display One"""
    def request = new CodeSystemFileImportRequest(
        type: "csv",
        dryRun: true,
        codeSystem: new FileProcessingCodeSystem(id: "dry-cs", name: "Dry CS"),
        version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
        properties: [
            new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
            new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
        ]
    )
    def result = CodeSystemFileImportProcessor.process(request, csv.getBytes("UTF-8"))

    when:
    def response = service.save(request, result)

    then:
    1 * codeSystemService.load("dry-cs", true) >> Optional.empty()
    1 * codeSystemVersionService.query(_) >> QueryResult.empty()
    1 * codeSystemValidationService.validateConcepts(_, _) >> []
    1 * dryRunService.dryRunImportCompareAndRollback(_, _, _, "dry-cs", _) >> new CodeSystemFileImportDryRunService.DryRunResult(
        "##### Created ######\n * c1", Optional.empty()
    )
    0 * codeSystemImportService.importCodeSystem(_, _, _)

    response.diff == "##### Created ######\n * c1"
    response.errors.isEmpty()
  }

  def "dry run should add TE716 when shadow import path reports failure"() {
    given:
    def csv = """code,display#en
c1,Display One"""
    def request = new CodeSystemFileImportRequest(
        type: "csv",
        dryRun: true,
        codeSystem: new FileProcessingCodeSystem(id: "dry-cs", name: "Dry CS"),
        version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
        properties: [
            new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
            new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
        ]
    )
    def result = CodeSystemFileImportProcessor.process(request, csv.getBytes("UTF-8"))
    def issue = ApiError.TE716.toIssue(Map.of("exception", "simulated import failure"))

    when:
    def response = service.save(request, result)

    then:
    1 * codeSystemService.load("dry-cs", true) >> Optional.empty()
    1 * codeSystemVersionService.query(_) >> QueryResult.empty()
    1 * codeSystemValidationService.validateConcepts(_, _) >> []
    1 * dryRunService.dryRunImportCompareAndRollback(_, _, _, "dry-cs", _) >> new CodeSystemFileImportDryRunService.DryRunResult(null, Optional.of(issue))
    0 * codeSystemImportService.importCodeSystem(_, _, _)

    response.diff == null
    response.errors.size() == 1
    response.errors[0].code == "TE716"
  }
}
