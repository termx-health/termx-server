package org.termx.core.ts;

import com.kodality.commons.model.QueryResult;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ValueSetExternalExpandProvider {
  public List<ValueSetVersionConcept> expand(ValueSetVersionRuleSet ruleSet, ValueSetVersion version, String preferredLanguage) {
    if (ruleSet == null || ruleSet.getRules() == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> include = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("include"))
        .flatMap(rule -> ruleExpand(rule, version, preferredLanguage).stream()).toList();
    List<ValueSetVersionConcept> exclude = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("exclude"))
        .flatMap(rule -> ruleExpand(rule, version, preferredLanguage).stream()).toList();
    return include.stream().filter(ic -> exclude.stream().noneMatch(ec -> ec.getConcept().getCode().equals(ic.getConcept().getCode())))
        .toList();
  }

  public abstract List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage);

  /**
   * Paged expansion of a single rule. Providers SHOULD override this to push
   * paging and free-text filtering down to the upstream terminology server —
   * the default falls back to {@link #ruleExpand} + in-memory filter+slice,
   * which materialises the full rule before slicing (fine for small code
   * systems, an OOM risk for large external sources like SNOMED).
   *
   * <p>The returned {@code QueryResult.meta.total} is the post-filter,
   * pre-pagination count; callers (e.g. FHIR {@code $expand}) use it as
   * {@code expansion.total}.
   *
   * @param textFilter  free-text typeahead filter (FHIR R5 {@code filter}); applied
   *                    against code+display, case-insensitive substring, before paging.
   *                    {@code null}/blank = no filter.
   * @param offset      0-based offset into the post-filter result; {@code null} = 0.
   * @param count       page size; {@code null} = unbounded (caller assumes responsibility).
   */
  public QueryResult<ValueSetVersionConcept> ruleExpandPaged(ValueSetVersionRule rule, ValueSetVersion version,
                                                             String preferredLanguage, String textFilter,
                                                             Integer offset, Integer count) {
    List<ValueSetVersionConcept> all = ruleExpand(rule, version, preferredLanguage);
    if (all == null) {
      all = List.of();
    }
    if (textFilter != null && !textFilter.isBlank()) {
      String needle = textFilter.toLowerCase();
      all = all.stream().filter(c -> {
        String code = c.getConcept() != null ? c.getConcept().getCode() : null;
        String display = c.getDisplay() != null ? c.getDisplay().getName() : null;
        return (code != null && code.toLowerCase().contains(needle))
            || (display != null && display.toLowerCase().contains(needle));
      }).toList();
    }
    int total = all.size();
    int from = offset == null ? 0 : Math.min(offset, total);
    int to = count == null ? total : Math.min(from + count, total);
    List<ValueSetVersionConcept> page = all.subList(from, to);
    QueryResult<ValueSetVersionConcept> result = new QueryResult<>(page);
    result.getMeta().setTotal(total);
    result.getMeta().setOffset(from);
    return result;
  }

  public abstract String getCodeSystemId();
}
