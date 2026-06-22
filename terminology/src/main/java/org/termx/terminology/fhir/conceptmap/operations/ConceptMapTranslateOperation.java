package org.termx.terminology.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.InstanceOperationDefinition;
import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.terminology.Privilege;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.fhir.conceptmap.ConceptMapFhirMapper;
import org.termx.terminology.terminology.mapset.MapSetService;
import org.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import org.termx.terminology.terminology.mapset.version.MapSetVersionService;
import org.termx.ts.mapset.MapSet;
import org.termx.ts.mapset.MapSetAssociation;
import org.termx.ts.mapset.MapSetAssociationQueryParams;
import org.termx.ts.mapset.MapSetQueryParams;
import org.termx.ts.mapset.MapSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapTranslateOperation implements InstanceOperationDefinition, TypeOperationDefinition {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "translate";
  }

  @Override
  public ResourceContent run(ResourceId id, ResourceContent parameters) {
    Parameters req = FhirMapper.fromJson(parameters.getValue(), Parameters.class);
    String[] parts = ConceptMapFhirMapper.parseCompositeId(id.getResourceId());
    String cmId = parts[0];
    String versionNumber = parts[1];
    Parameters resp = run(cmId, null, versionNumber, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    String url = req.findParameter("url").
          map(c -> c.getValueString() != null ? c.getValueString() : c.getValueUrl()).orElse(null);
    if (url == null) {
      // Inline tx-resource path: the conformance validator bundles the ConceptMap(s) as tx-resource and selects
      // the map by source/target system rather than a url. Translate against the inline definition (guest-safe).
      List<com.kodality.zmei.fhir.resource.terminology.ConceptMap> txCms = txResourceConceptMaps(req);
      if (!txCms.isEmpty()) {
        return new ResourceContent(FhirMapper.toJson(translateInline(txCms, req)), "json");
      }
      throw new FhirException(400, IssueType.INVALID, "url parameter required");
    }
    String conceptMapVersion = req.findParameter("conceptMapVersion").
          map(c -> c.getValueString() != null ? c.getValueString() : c.getValueUrl()).orElse(null);
    Parameters resp = run(null, url, conceptMapVersion, req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private Parameters run(String cmId, String cmUrl, String cmVersion, Parameters req) {
    String sourceCode = req.findParameter("sourceCode").map(c -> c.getValueCode() == null ? c.getValueString() : c.getValueCode()).orElse(null);
    String targetCode = req.findParameter("targetCode").map(c -> c.getValueCode() == null ? c.getValueString() : c.getValueCode()).orElse(null);
    String sourceSystem = req.findParameter("system")
          .map(c -> {
          if (c.getValueUri() != null) {
            return c.getValueUri();
          } else if (c.getValueUrl() != null) {
            return c.getValueUrl();
          } else {
            return c.getValueString();
          }
        })
        .orElse(null);
    String targetSystem = req.findParameter("targetSystem")
        .map(c -> {
          if (c.getValueUri() != null) {
            return c.getValueUri();
          } else if (c.getValueUrl() != null) {
            return c.getValueUrl();
          } else {
            return c.getValueString();
          }
        })
        .orElse(null);

    if (req.findParameter("sourceCoding").isPresent()) {
      sourceCode = req.findParameter("sourceCoding").map(ParametersParameter::getValueCoding).map(Coding::getCode).orElse(null);
      sourceSystem = req.findParameter("sourceCoding").map(ParametersParameter::getValueCoding).map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("targetCoding").isPresent()) {
      targetCode = req.findParameter("targetCoding").map(ParametersParameter::getValueCoding).map(Coding::getCode).orElse(null);
      targetSystem = req.findParameter("targetCoding").map(ParametersParameter::getValueCoding).map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("sourceCodeableConcept").isPresent()) {
      Optional<Coding> coding = req.findParameter("sourceCodeableConcept").map(ParametersParameter::getValueCodeableConcept)
          .flatMap(c -> c.getCoding().stream().findFirst());
      sourceCode = coding.map(Coding::getCode).orElse(null);
      sourceSystem = coding.map(Coding::getSystem).orElse(null);
    }

    if (req.findParameter("targetCodeableConcept").isPresent()) {
      Optional<Coding> coding = req.findParameter("targetCodeableConcept").map(ParametersParameter::getValueCodeableConcept)
          .flatMap(c -> c.getCoding().stream().findFirst());
      targetCode = coding.map(Coding::getCode).orElse(null);
      targetSystem = coding.map(Coding::getSystem).orElse(null);
    }

    if (sourceCode == null && targetCode == null) {
      throw new FhirException(400, IssueType.INVALID, "One (and only one) of the in parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, targetCoding, or targetCodeableConcept) SHALL be provided, to identify the code that is to be translated.");
    }
    if (sourceCode != null && sourceSystem == null) {
      throw new FhirException(400, IssueType.INVALID, "If a source code is provided, a system must be provided.");
    }
    if (targetCode != null && targetSystem == null) {
      throw new FhirException(400, IssueType.INVALID, "If a target code is provided, a system must be provided.");
    }

    MapSetQueryParams msParams = new MapSetQueryParams()
        .setId(cmId)
        .setUri(cmUrl)
        .setVersionVersion(cmVersion)
        .setVersionsDecorated(cmVersion != null)
        .setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.MS_READ))
        .limit(1);
    MapSet mapSet = mapSetService.query(msParams).findFirst().orElse(null);
    if (mapSet == null) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    MapSetVersion mapSetVersion = Optional.ofNullable(mapSet.getVersions()).orElse(List.of()).stream().findFirst().orElse(mapSetVersionService.loadLastVersion(mapSet.getId()));
    if (mapSetVersion == null) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    MapSetAssociationQueryParams aParams = new MapSetAssociationQueryParams().setMapSetVersionId(mapSetVersion.getId())
        .setSourceCodeAndSystemUri(sourceCode == null ? null : sourceCode + "|" + sourceSystem)
        .setTargetCodeAndSystemUri(targetCode == null ? null : targetCode + "|" + targetSystem)
        .setNoMap(false)
        .limit(-1);
    List<MapSetAssociation> associations = mapSetAssociationService.query(aParams).getData();
    if (CollectionUtils.isEmpty(associations)) {
      return new Parameters().addParameter(new ParametersParameter("result").setValueBoolean(false));
    }

    Parameters p = new Parameters();
    p.addParameter(new ParametersParameter("result").setValueBoolean(true));
    associations.forEach(a -> p.addParameter(new ParametersParameter("match")
        .addPart(new ParametersParameter("equivalence").setValueCode(a.getRelationship()))
        .addPart(new ParametersParameter("concept").setValueCoding(new Coding()
            .setCode(aParams.getSourceCodeAndSystemUri() == null ? a.getSource().getCode() : a.getTarget().getCode())
            .setSystem(aParams.getSourceCodeAndSystemUri() == null ? a.getSource().getCodeSystemUri() : a.getTarget().getCodeSystemUri())))
    ));
    return p;
  }

  /** ConceptMaps supplied inline via tx-resource params. */
  private static List<com.kodality.zmei.fhir.resource.terminology.ConceptMap> txResourceConceptMaps(Parameters req) {
    if (req == null || req.getParameter() == null) {
      return List.of();
    }
    return req.getParameter().stream()
        .filter(p -> "tx-resource".equals(p.getName()))
        .map(ParametersParameter::getResource)
        .filter(r -> r instanceof com.kodality.zmei.fhir.resource.terminology.ConceptMap)
        .map(r -> (com.kodality.zmei.fhir.resource.terminology.ConceptMap) r)
        .toList();
  }

  /**
   * Translate a source/target code against inline (tx-resource) ConceptMaps, the way the conformance validator
   * invokes {@code POST /ConceptMap/$translate} with the map bundled and no url. Forward (a source code is given)
   * walks each group's elements to its targets; reverse (only a target code is given) walks targets back to the
   * source element. Response parts are alphabetical (concept, equivalence, originMap, relationship, source).
   */
  private Parameters translateInline(List<com.kodality.zmei.fhir.resource.terminology.ConceptMap> cms, Parameters req) {
    String sourceCode = req.findParameter("sourceCode").map(c -> c.getValueCode() != null ? c.getValueCode() : c.getValueString()).orElse(null);
    String targetCode = req.findParameter("targetCode").map(c -> c.getValueCode() != null ? c.getValueCode() : c.getValueString()).orElse(null);
    String sourceSystem = uriParam(req, "sourceSystem", "system");
    String targetSystem = uriParam(req, "targetSystem");
    if (req.findParameter("sourceCoding").isPresent()) {
      Coding c = req.findParameter("sourceCoding").map(ParametersParameter::getValueCoding).orElse(null);
      sourceCode = c == null ? null : c.getCode();
      sourceSystem = c == null ? null : c.getSystem();
    }
    if (req.findParameter("targetCoding").isPresent()) {
      Coding c = req.findParameter("targetCoding").map(ParametersParameter::getValueCoding).orElse(null);
      targetCode = c == null ? null : c.getCode();
      targetSystem = c == null ? null : c.getSystem();
    }
    if (sourceCode == null && targetCode == null) {
      throw new FhirException(400, IssueType.INVALID,
          "One (and only one) of the in parameters (sourceCode, sourceCoding, sourceCodeableConcept, targetCode, "
              + "targetCoding, or targetCodeableConcept) SHALL be provided, to identify the code that is to be translated.");
    }
    boolean reverse = sourceCode == null;
    List<ParametersParameter> matches = new java.util.ArrayList<>();
    for (com.kodality.zmei.fhir.resource.terminology.ConceptMap cm : cms) {
      String originMap = cm.getUrl() == null ? null : cm.getUrl() + (cm.getVersion() != null ? "|" + cm.getVersion() : "");
      for (var group : Optional.ofNullable(cm.getGroup()).orElse(List.of())) {
        if (sourceSystem != null && group.getSource() != null && !sourceSystem.equals(group.getSource())) {
          continue;
        }
        if (targetSystem != null && group.getTarget() != null && !targetSystem.equals(group.getTarget())) {
          continue;
        }
        for (var element : Optional.ofNullable(group.getElement()).orElse(List.of())) {
          if (!reverse && !sourceCode.equals(element.getCode())) {
            continue;
          }
          for (var target : Optional.ofNullable(element.getTarget()).orElse(List.of())) {
            if (reverse && !targetCode.equals(target.getCode())) {
              continue;
            }
            matches.add(buildMatch(group.getTarget(), target.getCode(), target.getRelationship(), originMap,
                reverse ? group.getSource() : null, reverse ? element.getCode() : null));
          }
        }
      }
    }
    Parameters out = new Parameters();
    out.addParameter(new ParametersParameter("result").setValueBoolean(!matches.isEmpty()));
    matches.forEach(out::addParameter);
    return out;
  }

  /** A {@code match} parameter; parts emitted alphabetically: concept, equivalence, originMap, relationship, source. */
  private static ParametersParameter buildMatch(String targetSystem, String targetCode, String relationship,
                                                String originMap, String sourceSystem, String sourceCode) {
    ParametersParameter match = new ParametersParameter("match");
    match.addPart(new ParametersParameter("concept").setValueCoding(new Coding(targetSystem, targetCode)));
    String equivalence = relationship == null ? null : equivalence(relationship);
    if (equivalence != null) {
      match.addPart(new ParametersParameter("equivalence").setValueCode(equivalence));
    }
    if (originMap != null) {
      match.addPart(new ParametersParameter("originMap").setValueCanonical(originMap));
    }
    if (relationship != null) {
      match.addPart(new ParametersParameter("relationship").setValueCode(relationship));
    }
    if (sourceSystem != null && sourceCode != null) {
      match.addPart(new ParametersParameter("source").setValueCoding(new Coding(sourceSystem, sourceCode)));
    }
    return match;
  }

  /** Map an R5 ConceptMap {@code relationship} to the legacy R4 {@code equivalence} code (null when no mapping). */
  private static String equivalence(String relationship) {
    return switch (relationship) {
      case "equivalent" -> "equivalent";
      case "source-is-narrower-than-target" -> "narrower";
      case "source-is-broader-than-target" -> "wider";
      case "related-to" -> "relatedto";
      case "not-related-to" -> "disjoint";
      default -> null;
    };
  }

  private static String uriParam(Parameters req, String... names) {
    for (String name : names) {
      String v = req.findParameter(name)
          .map(c -> c.getValueUri() != null ? c.getValueUri() : c.getValueUrl() != null ? c.getValueUrl() : c.getValueString())
          .orElse(null);
      if (v != null) {
        return v;
      }
    }
    return null;
  }

}
