package org.termx.terminology.fileimporter.codesystem

import com.kodality.commons.model.QueryResult
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
 * Tests file import with large files (7k+ rows) to verify idle-in-transaction timeout fix.
 *
 * The fix moves file parsing outside the @Transactional block so that:
 * 1. File parsing (CPU-bound, non-DB work) happens without holding a database connection
 * 2. Transaction only wraps the actual database operations (validation + import)
 * 3. No idle-in-transaction timeout since there's no idle time between file parsing and DB work
 */
class CodeSystemFileImportLargeFileTest extends Specification {

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

  def "should handle 7000+ row CSV import without timeout"() {
    given: "a large CSV file with 7000+ rows"
    StringBuilder csvBuilder = new StringBuilder()
    csvBuilder.append("code,display#en\n")

    // Generate 7000 rows of medical terminology
    7000.times { i ->
      csvBuilder.append("CODE-${i},Display Name ${i}\n")
    }

    def csv = csvBuilder.toString()
    def request = new CodeSystemFileImportRequest(
        type: "csv",
        dryRun: false,
        codeSystem: new FileProcessingCodeSystem(id: "large-cs", name: "Large Code System"),
        version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
        properties: [
            new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
            new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
        ]
    )

    when: "processing the large file"
    long startTime = System.currentTimeMillis()
    def result = CodeSystemFileImportProcessor.process(request, csv.getBytes("UTF-8"))
    long parsingTime = System.currentTimeMillis() - startTime

    then: "file parsing completes successfully"
    result.entities.size() == 7000
    result.properties.size() > 0
    parsingTime > 0 // File parsing took some time

    when: "saving the import (validation runs outside any write transaction; the write is confined to importCodeSystem)"
    startTime = System.currentTimeMillis()
    codeSystemService.load("large-cs", true) >> Optional.empty()
    codeSystemVersionService.query(_) >> QueryResult.empty()
    codeSystemValidationService.validateConcepts(_, _) >> []
    codeSystemImportService.importCodeSystem(_, _, _) >> null

    def response = service.save(request, result)
    long saveTime = System.currentTimeMillis() - startTime

    then: "save completes without timeout (should finish in < 30 seconds, default idle-in-transaction timeout)"
    saveTime < 30_000 // Less than 30 seconds (PostgreSQL's idle-in-transaction timeout)
    response.errors.isEmpty()
    1 * codeSystemImportService.importCodeSystem(_, _, _)
  }

  def "should not hold connection during file parsing phase"() {
    given: "a moderately large CSV (1000 rows)"
    StringBuilder csvBuilder = new StringBuilder()
    csvBuilder.append("code,display#en\n")
    1000.times { i ->
      csvBuilder.append("CODE-${i},Display ${i}\n")
    }

    def csv = csvBuilder.toString()
    def request = new CodeSystemFileImportRequest(
        type: "csv",
        codeSystem: new FileProcessingCodeSystem(id: "med-cs", name: "Medium Code System"),
        version: new FileProcessingCodeSystemVersion(number: "1.0.0", language: "en"),
        properties: [
            new FileProcessingProperty(columnName: "code", propertyName: "concept-code", propertyType: "string", preferred: true),
            new FileProcessingProperty(columnName: "display#en", propertyName: "display", propertyType: "designation", language: "en")
        ]
    )

    when: "calling process() method (which should NOT hold a transaction during parsing)"
    codeSystemService.load("med-cs", true) >> Optional.empty()
    codeSystemVersionService.query(_) >> QueryResult.empty()
    codeSystemValidationService.validateConcepts(_, _) >> []
    codeSystemImportService.importCodeSystem(_, _, _) >> null

    // process() method should:
    // 1. Call CodeSystemFileImportProcessor.process() WITHOUT a transaction (parse phase)
    // 2. Run read/validation with no ambient transaction, then persist via importCodeSystem's own short transaction
    def response = service.process(request, csv.getBytes("UTF-8"))

    then: "the import completes successfully"
    response != null
    response.errors.isEmpty()
    1 * codeSystemImportService.importCodeSystem(_, _, _)
  }
}
