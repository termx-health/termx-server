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
    // Tag the issue with the org.hl7.fhir.core message-id the reference server emits for this text, when the text
    // is one termx maps. The reference renders each text from this key, so the mapping is deterministic; the
    // extension is required by some tx-ecosystem cases (overload/regex-bad validate) and optional elsewhere.
    String messageId = TxMessageIds.resolve(txIssueType, text);
    if (messageId != null) {
      issue.addExtension(new com.kodality.zmei.fhir.Extension(TxMessageIds.URL).setValueString(messageId));
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

  /**
   * A {@code check-system-version} violation: an {@code exception}-code OperationOutcome issue with the
   * {@code tx-issue-type}/{@code version-error} detail coding (tx-ecosystem {@code VALUESET_VERSION_CHECK}).
   */
  public static com.kodality.kefhir.core.exception.FhirException versionCheckException(int status, String text) {
    org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent issue =
        new org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent();
    issue.setSeverity(org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR);
    issue.setCode(org.hl7.fhir.r5.model.OperationOutcome.IssueType.EXCEPTION);
    issue.setDetails(new org.hl7.fhir.r5.model.CodeableConcept()
        .addCoding(new org.hl7.fhir.r5.model.Coding(TX_ISSUE_TYPE, "version-error", null))
        .setText(text));
    return new com.kodality.kefhir.core.exception.FhirException(status, issue);
  }

  /**
   * A structurally-invalid value set: an {@code invalid}-code OperationOutcome issue with the
   * {@code tx-issue-type}/{@code vs-invalid} detail coding (tx-ecosystem {@code VS_INVALID}), carrying the
   * offending element's location/expression. Used when the value set being operated on cannot be processed —
   * e.g. a {@code compose.include.filter} missing its {@code value} (1..1 in R5).
   */
  public static com.kodality.kefhir.core.exception.FhirException vsInvalidException(int status, String text, String location) {
    org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent issue =
        new org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent();
    issue.setSeverity(org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR);
    issue.setCode(org.hl7.fhir.r5.model.OperationOutcome.IssueType.INVALID);
    issue.setDetails(new org.hl7.fhir.r5.model.CodeableConcept()
        .addCoding(new org.hl7.fhir.r5.model.Coding(TX_ISSUE_TYPE, "vs-invalid", null))
        .setText(text));
    if (location != null) {
      issue.addLocation(location);
      issue.addExpression(location);
    }
    return new com.kodality.kefhir.core.exception.FhirException(status, issue);
  }

  /**
   * A malformed {@code displayLanguage} request parameter: a {@code processing}-code OperationOutcome issue with
   * the {@code tx-issue-type}/{@code invalid-display} detail coding (tx-ecosystem {@code validation-wrong-de-en-bad}).
   */
  public static com.kodality.kefhir.core.exception.FhirException invalidDisplayLanguageException(String displayLanguage) {
    org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent issue =
        new org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent();
    issue.setSeverity(org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR);
    issue.setCode(org.hl7.fhir.r5.model.OperationOutcome.IssueType.PROCESSING);
    issue.setDetails(new org.hl7.fhir.r5.model.CodeableConcept()
        .addCoding(new org.hl7.fhir.r5.model.Coding(TX_ISSUE_TYPE, "invalid-display", null))
        .setText("Invalid displayLanguage: '" + displayLanguage + "'"));
    return new com.kodality.kefhir.core.exception.FhirException(400, issue);
  }

  /** No code to validate was supplied (no coding/codeableConcept/code+system/code+inferSystem) — an error
   * OperationOutcome with the reference's exact wording (note: the reference omits the closing paren). */
  public static com.kodality.kefhir.core.exception.FhirException noCodeToValidateException() {
    org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent issue =
        new org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent();
    issue.setSeverity(org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR);
    issue.setCode(org.hl7.fhir.r5.model.OperationOutcome.IssueType.INVALID);
    issue.setDetails(new org.hl7.fhir.r5.model.CodeableConcept()
        .setText("Unable to find code to validate (looked for coding | codeableConcept | code+system | code+inferSystem in parameters"));
    return new com.kodality.kefhir.core.exception.FhirException(400, issue);
  }

  /** Formats a version list as the tx-ecosystem does: comma-separated with " or " before the last ("a, b or c"). */
  public static String presentVersionList(java.util.List<String> versions) {
    if (versions == null || versions.isEmpty()) {
      return "";
    }
    if (versions.size() == 1) {
      return versions.get(0);
    }
    return String.join(", ", versions.subList(0, versions.size() - 1)) + " or " + versions.get(versions.size() - 1);
  }
}
