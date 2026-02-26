package com.kodality.termx.terminology.fhir.codesystem.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptUtil;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.annotation.Factory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Factory
@RequiredArgsConstructor
public class CodeSystemValidateCodeOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private static final String UCUM = "ucum";
  private static final String UCUM_URI = "http://unitsofmeasure.org";

  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;

  public String getResourceType() {
    return ResourceType.CodeSystem.name();
  }

  public String getOperationName() {
    return "validate-code";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = CodeSystemFhirMapper.parseCompositeId(id.getResourceId());
    String csId = parts[0];
    String versionNumber = parts[1];
    CodeSystemVersion csv = codeSystemVersionService.load(csId, versionNumber)
        .orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "Concept version not found"));
    Parameters resp = run(csv.getCodeSystem(), csv.getId(), req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    Parameters resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(Parameters req) {
    String url = req.findParameter("url")
        .map(pp -> StringUtils.firstNonBlank(pp.getValueUrl(), pp.getValueCanonical(), pp.getValueUri(), pp.getValueString()))
        .or(() -> req.findParameter("system")
            .map(pp -> StringUtils.firstNonBlank(pp.getValueUrl(), pp.getValueCanonical(), pp.getValueUri(), pp.getValueString())))
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "url parameter required"));
    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    CodeSystemQueryParams csp = new CodeSystemQueryParams();
    csp.setUri(url);
    csp.setLimit(1);
    CodeSystem cs = codeSystemService.query(csp).findFirst().orElse(null);
    if (cs == null && UCUM_URI.equals(url)) {
      cs = codeSystemService.load(UCUM).orElse(null);
    }
    if (cs == null) {
      return error("CodeSystem not found by url " + url);
    }

    CodeSystemVersion csv = null;
    if (version != null) {
      CodeSystemVersionQueryParams csvp = new CodeSystemVersionQueryParams();
      csvp.setCodeSystem(cs.getId());
      csvp.setVersion(version);
      csvp.setStatus(PublicationStatus.active);
      csvp.setLimit(1);
      csv = codeSystemVersionService.query(csvp).findFirst().orElse(null);
      if (csv == null) {
        return error("CodeSystem active version not found");
      }
    }

    return run(cs.getId(), csv == null ? null : csv.getId(), req);
  }

  private Parameters run(String csId, Long versionId, Parameters req) {
    String code = req.findParameter("code").map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString()))
        .orElseThrow(() -> new FhirException(400, IssueType.INVALID, "code parameter required"));
    String display = req.findParameter("display").map(ParametersParameter::getValueString).orElse(null);
    String displayLanguage = req.findParameter("displayLanguage")
        .map(pp -> StringUtils.firstNonBlank(pp.getValueCode(), pp.getValueString()))
        .orElse(null);


    ConceptQueryParams cp = new ConceptQueryParams();
    cp.setCode(code);
    cp.setCodeSystem(csId);
    cp.setCodeSystemVersionId(versionId);
    cp.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));

    Concept concept = conceptService.query(cp).findFirst().orElse(null);
    if (concept == null) {
      return error("Code '" + code + "' is invalid");
    }

    Concept merged = mergeSupplements(csId, versionId, code, concept, req);
    Set<String> validDisplays = extractDisplays(merged, displayLanguage);
    String conceptDisplay = validDisplays.stream().findFirst().orElse(null);
    if (display != null && !validDisplays.contains(display)) {
      return new Parameters()
          .addParameter(new ParametersParameter("result").setValueBoolean(false))
          .addParameter(new ParametersParameter("display").setValueString(conceptDisplay))
          .addParameter(new ParametersParameter("message").setValueString("The display '" + display + "' is incorrect"));
    }

    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(true));
  }

  private static Parameters error(String message) {
    return new Parameters()
        .addParameter(new ParametersParameter("result").setValueBoolean(false))
        .addParameter(new ParametersParameter("message").setValueString(message));
  }

  private Concept mergeSupplements(String csId, Long versionId, String code, Concept concept, Parameters req) {
    List<Concept> supplements = loadSupplementConcepts(csId, versionId, code, req);
    if (CollectionUtils.isEmpty(supplements)) {
      return concept;
    }
    List<Designation> designations = new ArrayList<>(CollectionUtils.isEmpty(concept.getVersions()) ? List.of() :
        Optional.ofNullable(concept.getVersions().get(0).getDesignations()).orElse(List.of()));
    supplements.stream().filter(c -> CollectionUtils.isNotEmpty(c.getVersions())).forEach(c ->
        designations.addAll(Optional.ofNullable(c.getVersions().get(0).getDesignations()).orElse(List.of())));
    concept.getVersions().get(0).setDesignations(designations.stream()
        .collect(java.util.stream.Collectors.collectingAndThen(
            java.util.stream.Collectors.toMap(
                d -> String.join("|", StringUtils.defaultString(d.getDesignationType()), StringUtils.defaultString(d.getLanguage()), StringUtils.defaultString(d.getName())),
                d -> d,
                (a, b) -> a,
                java.util.LinkedHashMap::new),
            m -> new ArrayList<>(m.values()))));
    return concept;
  }

  private List<Concept> loadSupplementConcepts(String csId, Long versionId, String code, Parameters req) {
    List<String> supplements = req.getParameter().stream()
        .filter(p -> "useSupplement".equals(p.getName()))
        .map(p -> StringUtils.firstNonBlank(p.getValueCanonical(), p.getValueUri(), p.getValueUrl(), p.getValueString()))
        .filter(StringUtils::isNotBlank)
        .toList();
    if (CollectionUtils.isEmpty(supplements)) {
      return List.of();
    }
    return supplements.stream().map(s -> {
      String[] sv = s.split("\\|", 2);
      String uri = sv[0];
      String version = sv.length > 1 ? sv[1] : null;
      CodeSystem supplement = codeSystemService.query(new CodeSystemQueryParams().setUri(uri).limit(1)).findFirst().orElse(null);
      if (supplement == null || !csId.equals(supplement.getBaseCodeSystem())) {
        return null;
      }
      ConceptQueryParams cp = new ConceptQueryParams()
          .setCodeSystem(supplement.getId())
          .setCodeEq(code)
          .setCodeSystemVersion(version)
          .setCodeSystemVersionId(version == null ? versionId : null)
          .setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW))
          .limit(1);
      return conceptService.query(cp).findFirst().orElse(null);
    }).filter(c -> c != null && CollectionUtils.isNotEmpty(c.getVersions())).toList();
  }

  public static Set<String> extractDisplays(Concept c, String displayLanguage) {
    if (CollectionUtils.isEmpty(c.getVersions()) || c.getVersions().get(0).getDesignations() == null) {
      return Set.of();
    }
    List<Designation> designations = c.getVersions().get(0).getDesignations();
    LinkedHashSet<String> displays = new LinkedHashSet<>();
    Designation primary = ConceptUtil.getDisplay(designations, displayLanguage, List.of());
    if (primary != null && primary.getName() != null) {
      displays.add(primary.getName());
    }
    designations.stream()
        .filter(d -> d.getName() != null)
        .filter(d -> displayLanguage == null || displayLanguage.equals(d.getLanguage()) || (d.getLanguage() != null && d.getLanguage().startsWith(displayLanguage)))
        .filter(d -> "display".equals(d.getDesignationType()) || "abbreviation".equals(d.getDesignationType()) || "definition".equals(d.getDesignationType()))
        .map(Designation::getName)
        .forEach(displays::add);
    return displays;
  }

}
