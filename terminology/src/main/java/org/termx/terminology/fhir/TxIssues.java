package org.termx.terminology.fhir;

import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.OperationOutcome;
import com.kodality.zmei.fhir.resource.other.OperationOutcome.OperationOutcomeIssue;
import java.util.Arrays;
import java.util.List;

/**
 * Builders for the {@code issues} OperationOutcome that FHIR terminology operations attach to their
 * responses. The HL7 tx-ecosystem expects {@code $validate-code}/{@code $expand}/{@code $lookup} to report
 * problems (unknown system/version/code, code-not-in-value-set, deprecation, …) as a 200 response carrying a
 * structured OperationOutcome — each issue with a severity, an FHIR issue code, a
 * {@code http://hl7.org/fhir/tools/CodeSystem/tx-issue-type} coding, a human text, and the location it
 * applies to — rather than a flat message or a 4xx. This centralises that shape.
 */
public final class TxIssues {
  /** The tx-ecosystem issue-type code system used in OperationOutcome {@code details.coding}. */
  public static final String TX_ISSUE_TYPE = "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type";

  private TxIssues() {
  }

  /** A single OperationOutcome issue: FHIR {@code severity}/{@code code}, a tx-issue-type detail coding + text, and the location(s) it applies to. */
  public static OperationOutcomeIssue issue(String severity, String code, String txIssueType, String text, String... location) {
    OperationOutcomeIssue issue = new OperationOutcomeIssue()
        .setSeverity(severity)
        .setCode(code)
        .setDetails(new CodeableConcept(new Coding(TX_ISSUE_TYPE, txIssueType)).setText(text));
    if (location != null && location.length > 0) {
      issue.setLocation(Arrays.asList(location));
      issue.setExpression(Arrays.asList(location));
    }
    return issue;
  }

  /** Wraps issues into an OperationOutcome (for the {@code issues} response parameter). */
  public static OperationOutcome outcome(OperationOutcomeIssue... issues) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.setIssue(List.of(issues));
    return outcome;
  }

  /**
   * A {@code not-found} {@link com.kodality.kefhir.core.exception.FhirException} whose OperationOutcome issue
   * carries the {@code tx-issue-type}/{@code not-found} detail coding plus the message text — the shape the
   * tx-ecosystem expects for an unresolvable system/version/value-set (e.g. an unknown {@code valueSetVersion}),
   * rather than the bare {@code details.text} a plain {@code FhirException(status, IssueType, text)} produces.
   */
  public static com.kodality.kefhir.core.exception.FhirException notFoundException(int status, String text) {
    org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent issue =
        new org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent();
    issue.setSeverity(org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR);
    issue.setCode(org.hl7.fhir.r5.model.OperationOutcome.IssueType.NOTFOUND);
    issue.setDetails(new org.hl7.fhir.r5.model.CodeableConcept()
        .addCoding(new org.hl7.fhir.r5.model.Coding(TX_ISSUE_TYPE, "not-found", null))
        .setText(text));
    return new com.kodality.kefhir.core.exception.FhirException(status, issue);
  }
}
