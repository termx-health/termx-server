package org.termx.terminology.terminology.codesystem;

import org.termx.terminology.terminology.codesystem.concept.ConceptService;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.EntityPropertyRule;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper for resolving UCUM coding property values.
 * <p>
 * UCUM is a {@code fragment} code system: its concepts are defined by grammar, not enumerated. A concept query
 * ({@code code_system=ucum}, list of codes) only matches materialised units — the UCUM essence atoms plus any codes
 * a UCUM supplement (e.g. {@code ucum-lt}) enumerates — so a valid-but-non-materialised expression such as
 * {@code %{vol}}, {@code [CFU]/g} or {@code mg/mmol{creat}} resolves to nothing and is otherwise reported as an
 * unknown reference on import/validation. This helper lets both the file importer and the concept validator fall
 * back to UCUM grammar validation for property rules that target UCUM (the base {@code ucum} or a supplement of it).
 */
@Singleton
@RequiredArgsConstructor
public class UcumCodingSupport {
  public static final String UCUM = "ucum";

  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;

  /** The first UCUM code system referenced by the rule (the base {@code ucum} or a supplement whose base is UCUM). */
  public Optional<String> ucumSystemOfRule(EntityPropertyRule rule) {
    if (rule == null || CollectionUtils.isEmpty(rule.getCodeSystems())) {
      return Optional.empty();
    }
    return rule.getCodeSystems().stream().filter(this::isUcumOrSupplement).findFirst();
  }

  /** True when {@code codeSystem} is the base {@code ucum} or a supplement whose base code system is UCUM. */
  public boolean isUcumOrSupplement(String codeSystem) {
    if (UCUM.equals(codeSystem)) {
      return true;
    }
    return codeSystemService.load(codeSystem).map(CodeSystem::getBaseCodeSystem).filter(UCUM::equals).isPresent();
  }

  /** True when {@code code} is a valid UCUM expression by grammar, even if it is not materialised as a concept. */
  public boolean isValidUcumCode(String code) {
    // The UCUM provider answers only for the base "ucum" and validates a single code against the grammar.
    return StringUtils.isNotEmpty(code)
        && CollectionUtils.isNotEmpty(conceptService.query(new ConceptQueryParams().setCodeSystem(UCUM).setCode(code)).getData());
  }
}
