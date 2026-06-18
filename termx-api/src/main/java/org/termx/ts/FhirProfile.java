package org.termx.ts;

import java.util.Set;

/**
 * The official HL7 FHIR R5 conformance profiles for the terminology resources, by canonical url.
 * Sources: <a href="https://hl7.org/fhir/codesystem-profiles.html">CodeSystem</a>,
 * <a href="https://hl7.org/fhir/valueset-profiles.html">ValueSet</a>,
 * <a href="https://hl7.org/fhir/conceptmap-profiles.html">ConceptMap</a>.
 *
 * <p>These are the profiles TermX recognizes when a resource declares one via {@code meta.profile}.
 * A declared profile that is <em>not</em> in this set is treated as unknown: TermX records it but does
 * not reject the resource for it (see the profile-tolerant resource validator). The default — no declared
 * profile — means plain base-spec validation.
 */
public final class FhirProfile {
  private FhirProfile() {}

  public static final String BASE = "http://hl7.org/fhir/StructureDefinition/";

  // CodeSystem — https://hl7.org/fhir/codesystem-profiles.html
  public static final String SHAREABLE_CODE_SYSTEM = BASE + "ShareableCodeSystem";
  public static final String PUBLISHABLE_CODE_SYSTEM = BASE + "PublishableCodeSystem";

  // ValueSet — https://hl7.org/fhir/valueset-profiles.html
  public static final String SHAREABLE_VALUE_SET = BASE + "ShareableValueSet";
  public static final String COMPUTABLE_VALUE_SET = BASE + "ComputableValueSet";
  public static final String EXECUTABLE_VALUE_SET = BASE + "ExecutableValueSet";
  public static final String PUBLISHABLE_VALUE_SET = BASE + "PublishableValueSet";

  // ConceptMap — https://hl7.org/fhir/conceptmap-profiles.html
  public static final String SHAREABLE_CONCEPT_MAP = BASE + "ShareableConceptMap";
  public static final String PUBLISHABLE_CONCEPT_MAP = BASE + "PublishableConceptMap";

  public static final Set<String> CODE_SYSTEM = Set.of(SHAREABLE_CODE_SYSTEM, PUBLISHABLE_CODE_SYSTEM);
  public static final Set<String> VALUE_SET = Set.of(SHAREABLE_VALUE_SET, COMPUTABLE_VALUE_SET, EXECUTABLE_VALUE_SET, PUBLISHABLE_VALUE_SET);
  public static final Set<String> CONCEPT_MAP = Set.of(SHAREABLE_CONCEPT_MAP, PUBLISHABLE_CONCEPT_MAP);

  /** Every recognized terminology-resource profile. */
  public static final Set<String> ALL = Set.of(
      SHAREABLE_CODE_SYSTEM, PUBLISHABLE_CODE_SYSTEM,
      SHAREABLE_VALUE_SET, COMPUTABLE_VALUE_SET, EXECUTABLE_VALUE_SET, PUBLISHABLE_VALUE_SET,
      SHAREABLE_CONCEPT_MAP, PUBLISHABLE_CONCEPT_MAP);

  /** True when the canonical url is an HL7-defined terminology profile TermX recognizes. */
  public static boolean isRecognized(String profileUrl) {
    return ALL.contains(profileUrl);
  }
}
