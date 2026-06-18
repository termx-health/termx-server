package org.termx.terminology.loinc;

import com.kodality.commons.model.QueryResult;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.termx.core.ts.CodeSystemExternalProvider;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;

/**
 * Resolves {@code http://loinc.org} codes by delegating to an external LOINC FHIR server ({@link LoincClient})
 * instead of importing the (100k+ concept) LOINC content locally. Plugs into the same
 * {@link CodeSystemExternalProvider} mechanism SNOMED uses, so {@code $lookup}/{@code $validate-code} render
 * the delegated concept unchanged.
 *
 * <p>The local-vs-external policy is configurable via {@code loinc.source}:
 * <ul>
 *   <li>{@code local} — never delegate (pure local LOINC, or "not found" if none loaded);</li>
 *   <li>{@code local-first} (default) — delegate only when no local LOINC content is present, so an
 *       install that imported LOINC keeps its own version;</li>
 *   <li>{@code external} — always delegate, even if a local LOINC exists.</li>
 * </ul>
 * Inert unless {@code loinc.authentication} is set (then it behaves as {@code local}).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoincCodeSystemProvider extends CodeSystemExternalProvider {
  /** TermX CodeSystem id of the {@code http://loinc.org} stub (see the loinc-external-codesystem-stub changeset). */
  private static final String LOINC = "loinc";

  private final LoincClient loincClient;
  // Lazy: this is a CodeSystemExternalProvider and CodeSystemService transitively depends on
  // ConceptService, which injects List<CodeSystemExternalProvider> — a direct injection forms a DI
  // cycle (same reason SnomedCodeSystemProvider uses BeanProvider). Defer resolution to call time.
  private final BeanProvider<CodeSystemService> codeSystemService;

  @Value("${loinc.source:local-first}")
  String source;

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    if (!LOINC.equals(params.getCodeSystem()) || !shouldDelegate()) {
      return QueryResult.empty();
    }
    // Only single-code lookups ($lookup / $validate-code by code) are delegated.
    String code = StringUtils.isNotEmpty(params.getCodeEq()) ? params.getCodeEq() : params.getCode();
    if (StringUtils.isEmpty(code)) {
      return QueryResult.empty();
    }
    Parameters resp = loincClient.lookup(code, params.getCodeSystemVersion());
    if (resp == null || resp.findParameter("display").isEmpty()) {
      return QueryResult.empty();
    }
    return new QueryResult<>(List.of(toConcept(code, resp)));
  }

  @Override
  public String getCodeSystemId() {
    return LOINC;
  }

  private boolean shouldDelegate() {
    if (!loincClient.isConfigured() || "local".equalsIgnoreCase(source)) {
      return false;
    }
    if ("external".equalsIgnoreCase(source)) {
      return true;
    }
    // local-first: delegate only when the local LOINC CodeSystem carries no content of its own
    return !hasLocalContent();
  }

  private boolean hasLocalContent() {
    // The external stub is content=not-present; a locally imported LOINC is content=complete/fragment.
    return codeSystemService.get().load(LOINC).map(cs -> !"not-present".equals(cs.getContent())).orElse(false);
  }

  /** Maps an external {@code $lookup} Parameters response into a TermX {@link Concept}. */
  private Concept toConcept(String code, Parameters resp) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(code);
    version.setCodeSystem(LOINC);
    version.setStatus(PublicationStatus.active);

    List<Designation> designations = new ArrayList<>();
    resp.findParameter("display").map(ParametersParameter::getValueString).ifPresent(display ->
        designations.add(new Designation().setName(display).setDesignationType("display").setPreferred(true).setStatus("active")));
    if (resp.getParameter() != null) {
      resp.getParameter().stream().filter(p -> "designation".equals(p.getName())).forEach(p -> {
        String value = part(p, "value", ParametersParameter::getValueString);
        if (StringUtils.isNotEmpty(value)) {
          designations.add(new Designation()
              .setName(value)
              .setLanguage(part(p, "language", ParametersParameter::getValueCode))
              .setDesignationType(Optional.ofNullable(p.getPart("use"))
                  .map(ParametersParameter::getValueCoding).map(c -> c.getCode()).orElse("display"))
              .setStatus("active"));
        }
      });
    }
    version.setDesignations(designations);

    List<EntityPropertyValue> properties = new ArrayList<>();
    if (resp.getParameter() != null) {
      resp.getParameter().stream().filter(p -> "property".equals(p.getName())).forEach(p -> {
        String pCode = part(p, "code", ParametersParameter::getValueCode);
        EntityPropertyValue pv = toPropertyValue(pCode, p.getPart("value"));
        if (pv != null) {
          properties.add(pv);
        }
      });
    }
    version.setPropertyValues(properties);

    Concept concept = new Concept();
    concept.setCode(code);
    concept.setCodeSystem(LOINC);
    concept.setVersions(List.of(version));
    return concept;
  }

  private static String part(ParametersParameter p, String name, java.util.function.Function<ParametersParameter, String> getter) {
    return Optional.ofNullable(p.getPart(name)).map(getter).orElse(null);
  }

  /**
   * Maps a {@code property} part into an {@link EntityPropertyValue} with the {@code entityPropertyType} set
   * to match the FHIR {@code value[x]} — required because the $lookup renderer switches on that type (a null
   * type NPEs). A Coding value is rendered as its display/code string (the not-present stub has no property
   * definitions to validate a coding against).
   */
  private static EntityPropertyValue toPropertyValue(String code, ParametersParameter value) {
    if (StringUtils.isEmpty(code) || value == null) {
      return null;
    }
    if (value.getValueCode() != null) {
      return prop(code, EntityPropertyType.code, value.getValueCode());
    }
    if (value.getValueString() != null) {
      return prop(code, EntityPropertyType.string, value.getValueString());
    }
    if (value.getValueBoolean() != null) {
      return prop(code, EntityPropertyType.bool, value.getValueBoolean());
    }
    if (value.getValueDecimal() != null) {
      return prop(code, EntityPropertyType.decimal, value.getValueDecimal());
    }
    if (value.getValueInteger() != null) {
      return prop(code, EntityPropertyType.integer, value.getValueInteger());
    }
    if (value.getValueCoding() != null) {
      String s = value.getValueCoding().getDisplay() != null ? value.getValueCoding().getDisplay() : value.getValueCoding().getCode();
      return s == null ? null : prop(code, EntityPropertyType.string, s);
    }
    return null;
  }

  private static EntityPropertyValue prop(String code, String type, Object value) {
    return new EntityPropertyValue().setEntityProperty(code).setEntityPropertyType(type).setValue(value);
  }
}
