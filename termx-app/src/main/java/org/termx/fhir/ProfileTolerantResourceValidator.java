package org.termx.fhir;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import com.kodality.kefhir.core.api.resource.OperationInterceptor;
import com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.exception.FhirServerException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.service.conformance.HapiContextHolder;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import com.kodality.kefhir.validation.ResourceProfileValidator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r5.model.Resource;

import static java.util.stream.Collectors.toList;

/**
 * Replaces kefhir's {@link ResourceProfileValidator} with a profile-tolerant variant.
 *
 * <p>kefhir runs the HAPI instance validator on every write and rejects (400) on any ERROR/FATAL message.
 * That includes "Profile reference '…' has not been checked because it is unknown" — emitted whenever a
 * resource declares a {@code meta.profile} whose StructureDefinition TermX does not host (e.g. the HL7
 * Shareable/Publishable profiles, which TermX recognizes by url but does not load SDs to enforce). A
 * terminology server should not refuse an otherwise-valid resource merely because it claims conformance to
 * a profile the server cannot check — so those messages are downgraded from blocking errors here, while
 * every other base-spec validation error is still enforced exactly as before.
 *
 * <p>Toggled by the same {@code kefhir.validation-profile.enabled} flag, so setting it false disables all
 * instance validation (used by dedicated conformance instances).
 */
@Slf4j
@Singleton
@Replaces(ResourceProfileValidator.class)
@Requires(property = "kefhir.validation-profile.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class ProfileTolerantResourceValidator extends ResourceBeforeSaveInterceptor implements OperationInterceptor {
  // The org.hl7.fhir validator message emitted when a declared profile's StructureDefinition is absent.
  // Such a message means "could not check this profile", not "the resource is invalid" — so it is tolerated.
  private static final String UNCHECKED_PROFILE = "has not been checked because it is unknown";

  // HL7-jurisdiction best-practice ("SHOULD"/"SHOULD NOT") advisories the org.hl7.fhir validator escalates to
  // ERROR. A terminology server is handed arbitrary CodeSystems/ValueSets as operation INPUTS (tx-resource on
  // $expand/$validate-code) — it must not 400 a structurally-valid resource over an HL7 publishing best
  // practice it does not itself assert (e.g. a CodeSystem supplement that carries caseSensitive). These are
  // SHOULD-level only; the matching SHALL invariants (…_SUPPL_MISSING/…_SUPPL_WRONG) are left enforced.
  private static final java.util.Set<String> TOLERATED_BEST_PRACTICE_MESSAGE_IDS = java.util.Set.of(
      "CODESYSTEM_CS_HL7_PRESENT_ELEMENT_SUPPL",
      "CODESYSTEM_CS_NO_VS_SUPPLEMENT1",
      "CODESYSTEM_CS_NO_VS_SUPPLEMENT2");

  @Inject
  private ResourceFormatService resourceFormatService;
  @Inject
  private HapiContextHolder hapiContextHolder;

  public ProfileTolerantResourceValidator() {
    super(ResourceBeforeSaveInterceptor.INPUT_VALIDATION);
  }

  @Override
  public void handle(String level, String operation, ResourceContent parameters) {
    if (StringUtils.isEmpty(parameters.getValue())) {
      return;
    }
    validateProfile(parameters);
  }

  @Override
  public void handle(ResourceId id, ResourceContent content, String interaction) {
    Resource resource = validateParse(content);
    validateType(id.getResourceType(), resource.getResourceType().name());
    validateProfile(content);
  }

  private Resource validateParse(ResourceContent content) {
    try {
      return resourceFormatService.parse(content);
    } catch (Exception e) {
      throw new FhirException(400, IssueType.STRUCTURE, "error during resource parse: " + e.getMessage());
    }
  }

  private void validateType(String expectedType, String resourceType) {
    if (!resourceType.equals(expectedType)) {
      throw new FhirException(400, IssueType.INVALID, "was expecting " + expectedType + " but found " + resourceType);
    }
  }

  private void validateProfile(ResourceContent content) {
    if (hapiContextHolder.getHapiContext() == null) {
      throw new FhirServerException(500, "fhir context initialization error");
    }
    try {
      List<SingleValidationMessage> errors = hapiContextHolder.getValidator().validateWithResult(content.getValue()).getMessages().stream()
          .filter(m -> isError(m.getSeverity()))
          .filter(m -> !isUncheckedProfile(m))
          .filter(m -> !TOLERATED_BEST_PRACTICE_MESSAGE_IDS.contains(m.getMessageId()))
          .collect(toList());
      if (!errors.isEmpty()) {
        throw new FhirException(400, errors.stream().map(msg -> {
          OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
          issue.setCode(IssueType.INVALID);
          issue.setSeverity(IssueSeverity.fromCode(msg.getSeverity().getCode()));
          issue.setDetails(new CodeableConcept().setText(msg.getMessage()));
          issue.addLocation(msg.getLocationString());
          return issue;
        }).collect(toList()));
      }
    } catch (Exception e) {
      if (e instanceof FHIRException) {
        throw new FhirException(500, IssueType.INVALID, e.getMessage());
      }
      throw new RuntimeException("exception during profile validation: " + e.getMessage(), e);
    }
  }

  /** A declared profile TermX cannot resolve yields an "unchecked profile" message — tolerated, not fatal. */
  private static boolean isUncheckedProfile(SingleValidationMessage m) {
    return m.getMessage() != null && m.getMessage().contains(UNCHECKED_PROFILE);
  }

  private static boolean isError(ResultSeverityEnum level) {
    return level == ResultSeverityEnum.ERROR || level == ResultSeverityEnum.FATAL;
  }
}
