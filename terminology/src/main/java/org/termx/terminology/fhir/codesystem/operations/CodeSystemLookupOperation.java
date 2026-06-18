package org.termx.terminology.fhir.codesystem.operations;

import com.kodality.commons.util.DateUtil;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import org.termx.ts.codesystem.CodeSystemAssociationQueryParams;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemQueryParams;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptSnapshot;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
public class CodeSystemLookupOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";
  private static final String DEFINITION = "definition";
  private static final String NOT_SELECTABLE = "notSelectable";

  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemAssociationService associationService;
  private final LookupDefaultPropertyMode defaultPropertyMode;

  public CodeSystemLookupOperation(ConceptService conceptService, CodeSystemService codeSystemService,
                                   CodeSystemAssociationService associationService,
                                   @Value("${termx.fhir.codesystem.lookup.default-property-mode:ALL}") LookupDefaultPropertyMode defaultPropertyMode) {
    this.conceptService = conceptService;
    this.codeSystemService = codeSystemService;
    this.associationService = associationService;
    this.defaultPropertyMode = defaultPropertyMode;
  }

  CodeSystemLookupOperation(ConceptService conceptService, CodeSystemService codeSystemService) {
    this(conceptService, codeSystemService, null, LookupDefaultPropertyMode.ALL);
  }

  CodeSystemLookupOperation(ConceptService conceptService, CodeSystemService codeSystemService, LookupDefaultPropertyMode defaultPropertyMode) {
    this(conceptService, codeSystemService, null, defaultPropertyMode);
  }

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "lookup";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    Parameters resp = run(csId, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent c) {
    Parameters req = FhirMapper.fromJson(c.getValue(), Parameters.class);
    String system = req.findParameter("system")
        .map(p -> p.getValueUrl() != null ? p.getValueUrl() :
            p.getValueUri() != null ? p.getValueUri() :
                p.getValueCanonical() != null ? p.getValueCanonical() :
                    p.getValueString())
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "system parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    String csId = codeSystemService.query(new CodeSystemQueryParams().setUri(system).limit(1)).findFirst().map(CodeSystem::getId).orElse(null);
    if (csId == null && UCUM_URI.equals(system)) {
      csId = UCUM;
    }
    if (csId == null) {
      throw new FhirException(404, IssueType.NOTFOUND, "CodeSystem not found");
    }
    Parameters resp = run(csId, version, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String csId, String version, Parameters req) {
    String code = req.findParameter("code").map(ParametersParameter::getValueString)
        .or(() -> req.findParameter("code").map(ParametersParameter::getValueCode))
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    LocalDate date = req.findParameter("date").map(pp -> LocalDateTime.parse(pp.getValueString()).toLocalDate()).orElse(null);
    String displayLanguage = req.findParameter("displayLanguage")
        .map(p -> StringUtils.firstNonBlank(p.getValueCode(), p.getValueString()))
        .orElse(null);

    ConceptQueryParams cQueryParams = new ConceptQueryParams()
        .setCodeSystem(csId)
        .setCodeEq(code)
        .setCodeSystemVersion(version)
        .setCodeSystemVersionReleaseDateGe(date)
        .setCodeSystemVersionExpirationDateLe(date)
        .setIncludeSupplement(true)
        .setDisplayLanguage(displayLanguage)
        .setUseSupplement(extractUseSupplement(req))
        .limit(1);
    Concept c = conceptService.query(cQueryParams).findFirst()
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept not found"));

    CodeSystem cs = codeSystemService.load(csId, true).orElse(null);
    List<Designation> designations = new ArrayList<>(c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getDesignations).orElse(List.of()));

    Optional<CodeSystemVersionReference> csVersion = c.getVersions().stream().findFirst().flatMap(v -> Optional.ofNullable(v.getVersions()).orElse(List.of()).stream().findFirst());
    // displayLanguage may be a comma-separated list — prefer the FIRST requested language for the display.
    String firstDisplayLanguage = StringUtils.isBlank(displayLanguage) ? null : displayLanguage.split(",")[0].trim();
    String preferredLanguage = StringUtils.firstNonBlank(firstDisplayLanguage, csVersion.map(CodeSystemVersionReference::getPreferredLanguage).orElse(null));
    Designation display = ConceptUtil.getDisplay(designations, preferredLanguage, List.of());
    Designation definition = designations.stream()
        .filter(d -> DEFINITION.equals(d.getDesignationType()) && (preferredLanguage == null || preferredLanguage.equals(d.getLanguage())))
        .findFirst().or(() -> designations.stream().filter(d -> DEFINITION.equals(d.getDesignationType())).findFirst()).orElse(null);

    Parameters resp = new Parameters();
    // name is a display name for the code system (the resource name), not its id.
    resp.addParameter(new ParametersParameter().setName("name").setValueString(cs != null ? cs.getName() : c.getCodeSystem()));
    resp.addParameter(new ParametersParameter().setName("code").setValueCode(code));
    if (cs != null && StringUtils.isNotEmpty(cs.getUri())) {
      resp.addParameter(new ParametersParameter().setName("system").setValueUri(cs.getUri()));
    }
    csVersion.ifPresent(v -> resp.addParameter(new ParametersParameter().setName("version").setValueString(v.getVersion())));
    resp.addParameter(new ParametersParameter().setName("display").setValueString(display != null ? display.getName() : null));
    if (definition != null && StringUtils.isNotEmpty(definition.getName())) {
      resp.addParameter(new ParametersParameter().setName("definition").setValueString(definition.getName()));
    }
    resp.addParameter(new ParametersParameter().setName("abstract").setValueBoolean(isAbstract(c)));

    designations.stream()
        .filter(d -> d != display && d != definition)
        .filter(d -> languageMatches(d.getLanguage(), displayLanguage))
        .forEach(d -> resp.addParameter(new ParametersParameter("designation")
            .addPart(new ParametersParameter("use").setValueCoding(new Coding(d.getDesignationType())))
            .addPart(new ParametersParameter("value").setValueString(d.getName()))
            .addPart(new ParametersParameter("language").setValueString(d.getLanguage()))));

    Map<String, String> propertyDescriptions = cs == null || cs.getProperties() == null ? Map.of() :
        cs.getProperties().stream().filter(p -> p.getName() != null && p.getDescription() != null && !p.getDescription().isEmpty())
            .collect(Collectors.toMap(EntityProperty::getName, p -> p.getDescription().values().stream().findFirst().orElse(null), (a, b) -> a));

    List<String> properties = req.getParameter().stream()
        .filter(p -> "property".equals(p.getName()))
        .map(p -> StringUtils.firstNonBlank(p.getValueCode(), p.getValueString()))
        .toList();
    List<EntityPropertyValue> propertyValues = c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getPropertyValues).orElse(List.of());
    propertyValues.stream().filter(pv -> shouldReturnProperty(properties, pv)).forEach(pv -> {
      ParametersParameter property = new ParametersParameter("property")
          .addPart(new ParametersParameter("code").setValueCode(pv.getEntityProperty()))
          .addPart(toParameter(pv.getEntityPropertyType(), pv.getValue(), c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getSnapshot).orElse(null), displayLanguage));
      String description = propertyDescriptions.get(pv.getEntityProperty());
      if (StringUtils.isNotEmpty(description)) {
        property.addPart(new ParametersParameter("description").setValueString(description));
      }
      resp.addParameter(property);
    });

    // Standard computed properties FHIR $lookup exposes beyond the code system's own defined properties:
    // inactive (the code's active state) and the parent/child hierarchy (from is-a associations).
    boolean allProps = properties.isEmpty() && defaultPropertyMode == LookupDefaultPropertyMode.ALL;
    if (allProps || properties.contains("inactive")) {
      resp.addParameter(new ParametersParameter("property")
          .addPart(new ParametersParameter("code").setValueCode("inactive"))
          .addPart(new ParametersParameter("value").setValueBoolean(isInactive(c))));
    }
    Long versionId = c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getId).orElse(null);
    if (associationService != null && versionId != null) {
      if (allProps || properties.contains("parent")) {
        associationService.query(new CodeSystemAssociationQueryParams()
            .setSourceEntityVersionId(String.valueOf(versionId)).setAssociationType("is-a").limit(-1)).getData()
            .forEach(a -> addHierarchyProperty(resp, "parent", a.getTargetCode()));
      }
      if (allProps || properties.contains("child")) {
        associationService.query(new CodeSystemAssociationQueryParams()
            .setTargetEntityVersionId(String.valueOf(versionId)).setAssociationType("is-a").limit(-1)).getData()
            .forEach(a -> addHierarchyProperty(resp, "child", a.getSourceCode()));
      }
    }
    return resp;
  }

  private static void addHierarchyProperty(Parameters resp, String code, String value) {
    if (StringUtils.isNotEmpty(value)) {
      resp.addParameter(new ParametersParameter("property")
          .addPart(new ParametersParameter("code").setValueCode(code))
          .addPart(new ParametersParameter("value").setValueCode(value)));
    }
  }

  /** A concept is inactive when it carries {@code inactive=true} or a {@code status} of retired/deprecated. */
  private static boolean isInactive(Concept c) {
    return c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getPropertyValues).orElse(List.of()).stream()
        .anyMatch(pv -> ("inactive".equals(pv.getEntityProperty())
            && (Boolean.TRUE.equals(pv.getValue()) || "true".equalsIgnoreCase(String.valueOf(pv.getValue()))))
            || ("status".equals(pv.getEntityProperty()) && List.of("retired", "deprecated").contains(String.valueOf(pv.getValue()))));
  }

  /** A concept is abstract (not for direct use) when it carries a {@code notSelectable=true} property. */
  private static boolean isAbstract(Concept c) {
    return c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getPropertyValues).orElse(List.of()).stream()
        .filter(pv -> NOT_SELECTABLE.equals(pv.getEntityProperty()))
        .anyMatch(pv -> Boolean.TRUE.equals(pv.getValue()) || "true".equalsIgnoreCase(String.valueOf(pv.getValue())));
  }

  private boolean shouldReturnProperty(List<String> requestedProperties, EntityPropertyValue propertyValue) {
    if (!requestedProperties.isEmpty()) {
      return requestedProperties.contains(propertyValue.getEntityProperty());
    }
    return defaultPropertyMode == LookupDefaultPropertyMode.ALL;
  }

  private static String extractUseSupplement(Parameters req) {
    return req.getParameter().stream()
        .filter(p -> "useSupplement".equals(p.getName()))
        .map(p -> p.getValueCanonical() != null ? p.getValueCanonical() :
            p.getValueUri() != null ? p.getValueUri() :
                p.getValueUrl() != null ? p.getValueUrl() :
                    p.getValueString())
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(java.util.stream.Collectors.joining(","));
  }

  private static boolean languageMatches(String language, String displayLanguage) {
    // displayLanguage may be a comma-separated list (e.g. "en,et"); a designation matches when
    // its language equals (or is a regional variant of) ANY of the requested languages.
    if (StringUtils.isBlank(displayLanguage)) {
      return true;
    }
    if (language == null) {
      return false;
    }
    return Arrays.stream(displayLanguage.split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .anyMatch(dl -> language.equals(dl) || language.startsWith(dl + "-"));
  }

  private static ParametersParameter toParameter(String type, Object value, ConceptSnapshot snapshot, String language) {
    ParametersParameter result = new ParametersParameter("value");
    switch (type) {
      case EntityPropertyType.code -> result.setValueCode((String) value);
      case EntityPropertyType.string -> result.setValueString((String) value);
      case EntityPropertyType.bool -> result.setValueBoolean((Boolean) value);
      case EntityPropertyType.decimal -> result.setValueDecimal(new BigDecimal(String.valueOf(value)));
      case EntityPropertyType.integer -> result.setValueInteger(Integer.valueOf(String.valueOf(value)));
      case EntityPropertyType.coding -> {
        Concept concept = JsonUtil.getObjectMapper().convertValue(value, Concept.class);
        Coding coding = new Coding(concept.getCodeSystem(), concept.getCode());
        findSnapshotCoding(snapshot, concept).ifPresent(snapshotCoding -> {
          coding.setDisplay(resolveDisplay(snapshotCoding.display(), language));
          coding.setVersion(snapshotCoding.version());
        });
        result.setValueCoding(coding);
      }
      case EntityPropertyType.dateTime -> {
        if (value instanceof OffsetDateTime) {
          result.setValueDateTime((OffsetDateTime) value);
        } else {
          result.setValueDateTime(DateUtil.parseOffsetDateTime((String) value));
        }
      }
    }
    return result;
  }

  private static Optional<ConceptSnapshot.SnapshotCoding> findSnapshotCoding(ConceptSnapshot snapshot, Concept concept) {
    return Optional.ofNullable(snapshot)
        .map(ConceptSnapshot::getProperties).stream()
        .flatMap(List::stream)
        .filter(Objects::nonNull)
        .map(ConceptSnapshot.SnapshotProperty::valueCoding)
        .filter(Objects::nonNull)
        .filter(propertyCoding -> concept.getCode().equals(propertyCoding.code())
            && concept.getCodeSystem().equals(propertyCoding.system()))
        .findFirst();
  }

  private static String resolveDisplay(String display, String language) {
    if (display == null) {
      return null;
    }
    try {
      List<LocalName> jsonList = JsonUtil.getObjectMapper().readValue(display, JsonUtil.getListType(LocalName.class));
      if (jsonList.isEmpty()) {
        return null;
      }
      if (language == null) {
        return jsonList.getFirst().name();
      }
      return jsonList.stream()
          .filter(localName -> language.equals(localName.language()))
          .map(LocalName::name)
          .findFirst()
          .orElseGet(() -> jsonList.getFirst().name());
    } catch (Exception e) {
      return display;
    }
  }

  private record LocalName(String language, String name) {}
}
