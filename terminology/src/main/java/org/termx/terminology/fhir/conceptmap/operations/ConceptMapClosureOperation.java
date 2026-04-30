package org.termx.terminology.fhir.conceptmap.operations;

import com.kodality.kefhir.core.api.resource.TypeOperationDefinition;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroup;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElement;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap.ConceptMapGroupElementTarget;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.terminology.closure.ClosureService;
import org.termx.terminology.terminology.closure.ClosureService.ClosureConceptInput;
import org.termx.terminology.terminology.closure.ClosureService.ClosureDelta;
import org.termx.ts.closure.ClosureRelationship;

/**
 * FHIR R5 ConceptMap/$closure operation -- maintains a per-name client-side transitive closure
 * table over subsumption relationships. Routed type-level (POST /ConceptMap/$closure) per kefhir's
 * dispatch model.
 *
 * Modes:
 *  - name only         -> initOrReset(name); returns empty ConceptMap.
 *  - name + concept[]  -> addConcepts(...); returns ConceptMap with newly-discovered relationships.
 *  - name + version    -> getAtVersion(name, v); returns ConceptMap with relationships up to v.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapClosureOperation implements TypeOperationDefinition {
  private final ClosureService closureService;

  public String getResourceType() {
    return ResourceType.ConceptMap.name();
  }

  public String getOperationName() {
    return "closure";
  }

  public ResourceContent run(ResourceContent p) {
    Parameters req = FhirMapper.fromJson(p.getValue(), Parameters.class);
    ConceptMap resp = run(req);
    return new ResourceContent(FhirMapper.toJson(resp), "json");
  }

  private ConceptMap run(Parameters req) {
    // $closure has no per-resource permission target; require an authenticated session.
    SessionStore.require();

    String name = req.findParameter("name")
        .map(ParametersParameter::getValueString)
        .filter(s -> !s.isBlank())
        .orElseThrow(() -> new FhirException(400, IssueType.REQUIRED, "parameter 'name' is required"));

    String version = req.findParameter("version").map(ParametersParameter::getValueString).orElse(null);

    if (version != null && !version.isBlank()) {
      int v;
      try {
        v = Integer.parseInt(version);
      } catch (NumberFormatException e) {
        throw new FhirException(400, IssueType.INVALID, "parameter 'version' must be an integer");
      }
      ClosureDelta delta = closureService.getAtVersion(name, v);
      if (delta == null) {
        throw new FhirException(404, IssueType.NOTFOUND, "closure '" + name + "' not found");
      }
      return toFhir(delta);
    }

    List<ClosureConceptInput> concepts = req.getParameter() == null ? List.of() :
        req.getParameter().stream()
            .filter(pp -> "concept".equals(pp.getName()))
            .map(ParametersParameter::getValueCoding)
            .filter(c -> c != null && c.getSystem() != null && c.getCode() != null)
            .map(c -> new ClosureConceptInput(c.getSystem(), c.getCode()))
            .toList();

    if (concepts.isEmpty()) {
      ClosureDelta delta = closureService.initOrReset(name);
      return toFhir(delta);
    }

    ClosureDelta delta = closureService.addConcepts(name, concepts);
    return toFhir(delta);
  }

  private ConceptMap toFhir(ClosureDelta delta) {
    ConceptMap cm = new ConceptMap();
    cm.setName(delta.closure().getName());
    cm.setVersion(String.valueOf(delta.closure().getCurrentVersion()));
    cm.setDate(OffsetDateTime.now());
    cm.setStatus("active");

    // Group relationships by source codeSystem (which is also the target codeSystem -- $closure
    // is intra-CodeSystem subsumption).
    Map<String, List<ClosureRelationship>> bySystem = new LinkedHashMap<>();
    for (ClosureRelationship rel : delta.relationships()) {
      bySystem.computeIfAbsent(rel.getCodeSystem(), k -> new ArrayList<>()).add(rel);
    }
    List<ConceptMapGroup> groups = new ArrayList<>();
    bySystem.forEach((codeSystem, rels) -> {
      ConceptMapGroup group = new ConceptMapGroup();
      group.setSource(codeSystem);
      group.setTarget(codeSystem);
      group.setElement(buildElements(rels));
      groups.add(group);
    });
    cm.setGroup(groups);
    return cm;
  }

  private static List<ConceptMapGroupElement> buildElements(Collection<ClosureRelationship> rels) {
    Map<String, ConceptMapGroupElement> byChild = new LinkedHashMap<>();
    for (ClosureRelationship rel : rels) {
      ConceptMapGroupElement element = byChild.computeIfAbsent(rel.getChildCode(), code -> {
        ConceptMapGroupElement e = new ConceptMapGroupElement();
        e.setCode(code);
        e.setTarget(new ArrayList<>());
        return e;
      });
      ConceptMapGroupElementTarget target = new ConceptMapGroupElementTarget();
      target.setCode(rel.getParentCode());
      // FHIR R5 ConceptMap uses 'relationship'. The child is more specific -> source is narrower.
      target.setRelationship("source-is-narrower-than-target");
      element.getTarget().add(target);
    }
    return new ArrayList<>(byChild.values());
  }

  /** Used by tests to construct a Coding without depending on a particular SDK helper. */
  static ParametersParameter conceptParameter(Coding coding) {
    return new ParametersParameter("concept").setValueCoding(coding);
  }
}
