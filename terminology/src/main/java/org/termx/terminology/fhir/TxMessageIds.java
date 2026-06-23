package org.termx.terminology.fhir;

/**
 * Resolves the HL7 {@code operationoutcome-message-id} for a terminology issue.
 *
 * <p>The org.hl7.fhir.core reference server tags every {@code OperationOutcome.issue} with the i18n
 * message-bundle key that produced its text (the {@code operationoutcome-message-id} extension). In the
 * tx-ecosystem fixtures this key is {@code $optional$: "!tx.fhir.org"} for most issues — optional for any server
 * that is not tx.fhir.org — but a handful of cases (notably the {@code overload} and {@code regex-bad} validate
 * suites) mark it REQUIRED for all servers, so termx must emit it to match.
 *
 * <p>The mapping is by the {@code tx-issue-type} (which termx already sets on each issue) plus, where one
 * issue type covers several keys, a discriminator on the rendered text. The keys here are the exact strings the
 * fixtures emit — they are NOT always the bare {@code Messages.properties} key (e.g. {@code not-in-vs} keeps the
 * ICU {@code _one} plural suffix, but {@code invalid-display} drops it). An unmapped issue yields {@code null},
 * leaving the extension absent — correct wherever it is optional. Only the keys termx can actually produce are
 * listed; broaden only with a fixture to confirm the exact string.
 */
public final class TxMessageIds {
  public static final String URL = "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id";

  private TxMessageIds() {
  }

  /** The message-id for an issue of the given tx-issue-type and rendered text, or {@code null} when unmapped. */
  public static String resolve(String txIssueType, String text) {
    if (txIssueType == null || text == null || text.isEmpty()) {
      return null;
    }
    switch (txIssueType) {
      case "not-in-vs":
        // A codeableConcept's "No valid coding was found …" wrapper has its own key; a single code's
        // "The provided code … was not found …" is the (ICU _one) provided-codes key.
        return text.startsWith("No valid coding was found")
            ? "TX_GENERAL_CC_ERROR_MESSAGE"
            : "None_of_the_provided_codes_are_in_the_value_set_one";
      case "this-code-not-in-vs":
        return "None_of_the_provided_codes_are_in_the_value_set_one";
      case "invalid-code":
        if (text.contains("labeled as a fragment")) {
          return "UNKNOWN_CODE_IN_FRAGMENT";
        }
        return text.contains(" version '") ? "Unknown_Code_in_Version" : "Unknown_Code_in";
      case "invalid-data":
        return text.startsWith("Coding has no system") ? "Coding_has_no_system__cannot_validate" : null;
      case "invalid-display":
        if (text.contains("There are no valid display names found for language")) {
          return "NO_VALID_DISPLAY_FOUND_NONE_FOR_LANG_ERR";
        }
        if (text.contains("which is a valid display for the default language")) {
          return "NO_VALID_DISPLAY_FOUND_NONE_FOR_LANG_OK";
        }
        if (!text.startsWith("Wrong Display Name")) {
          return null;
        }
        // A supplied display that differs from the valid one only by whitespace gets its own key.
        return whitespaceOnlyDisplayDifference(text)
            ? "Display_Name_WS_for__should_be_one_of__instead_of"
            : "Display_Name_for__should_be_one_of__instead_of";
      case "not-found":
        if (!text.startsWith("A definition for CodeSystem")) {
          return null;
        }
        return text.contains(" version '") ? "UNKNOWN_CODESYSTEM_VERSION" : "UNKNOWN_CODESYSTEM";
      case "code-comment":
        return text.contains("has a status of") ? "INACTIVE_CONCEPT_FOUND" : null;
      case "code-rule":
        return text.contains("is valid but is not active") ? "STATUS_CODE_WARNING_CODE" : null;
      default:
        return null;
    }
  }

  // "Wrong Display Name '<supplied>' for <system>#<code>. Valid display is '<valid>' (…)" — true when the supplied
  // and the single valid display differ only by whitespace (collapsed and trimmed). The reference reports those
  // with a distinct WS message key.
  private static final java.util.regex.Pattern WRONG_DISPLAY =
      java.util.regex.Pattern.compile("^Wrong Display Name '(.+?)' for .+?\\. Valid display is '(.+?)' \\(");

  private static boolean whitespaceOnlyDisplayDifference(String text) {
    java.util.regex.Matcher m = WRONG_DISPLAY.matcher(text);
    if (!m.find()) {
      return false;
    }
    String supplied = m.group(1).replaceAll("\\s+", " ").trim();
    String valid = m.group(2).replaceAll("\\s+", " ").trim();
    return supplied.equals(valid);
  }
}
